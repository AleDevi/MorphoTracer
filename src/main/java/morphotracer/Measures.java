package morphotracer;

import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything computed from the ROIs of one photo: sizes for morphology.csv, points, spots and their positions.
 *  Pure computation on ROIs in photo pixels, no window: it is what the tests check. */
public class Measures {

    public static final String ITEM = "mt_item";
    public static final String SCALE = "scale";

    /** Column -> value, in the setup's order (morphology.csv). */
    public final Map<String, String> columns = new LinkedHashMap<String, String>();
    /** One map per point (landmarks.csv). */
    public final List<Map<String, String>> points = new ArrayList<Map<String, String>>();
    /** One map per spot (spots.csv). */
    public final List<Map<String, String>> spots = new ArrayList<Map<String, String>>();
    /** Item -> short text for the panel, e.g. "15.1 mm" or "3 spots, 1.2 mm²". */
    public final Map<String, String> shown = new LinkedHashMap<String, String>();
    /** Item -> point (x, y) in pixels. */
    public final Map<String, double[]> xy = new LinkedHashMap<String, double[]>();

    /** The item a ROI belongs to: its mt_item property, else its name without a trailing _N. */
    public static String itemOf(Roi r) {
        String p = r.getProperty(ITEM);
        if (p != null && !p.isEmpty())
            return p;
        String n = r.getName();
        if (n == null)
            return "";
        return n.replaceFirst("_\\d+$", "");
    }

    public static List<Roi> of(List<Roi> rois, String item) {
        List<Roi> out = new ArrayList<Roi>();
        for (Roi r : rois)
            if (itemOf(r).equals(item))
                out.add(r);
        return out;
    }

    /** Morphology columns of a setup, after the fixed ones. */
    public static List<String> columnNames(Setup s) {
        List<String> c = new ArrayList<String>();
        for (Item it : s.items) {
            switch (it.type) {
                case LENGTH: case LENGTH_FROM_POINTS: c.add(it.name + "_mm"); break;
                case PART: c.add(it.name + "_area_mm2"); break;
                case COLOUR:
                    c.add(it.name + "_area_mm2");
                    c.add(it.name + "_n");
                    if (s.training())
                        c.add(it.name + "_complete");
                    break;
                case COUNT: c.add(it.name + "_n"); break;
                default: break;
            }
        }
        return c;
    }

    /** ppm = pixels per mm, null when the photo has no scale. done = colour classes confirmed complete. */
    public static Measures compute(Setup s, List<Roi> rois, Double ppm, int width, int height, List<String> done) {
        Measures m = new Measures();
        for (Item it : s.items)
            if (it.type == Item.Type.POINT) {
                List<Roi> r = of(rois, it.name);
                if (!r.isEmpty()) {
                    FloatPolygon p = r.get(0).getFloatPolygon();
                    if (p.npoints > 0)
                        m.xy.put(it.name, new double[] {p.xpoints[0], p.ypoints[0]});
                }
            }
        for (Item it : s.items) {
            List<Roi> r = of(rois, it.name);
            switch (it.type) {
                case LENGTH: {
                    Double px = r.isEmpty() ? null : r.get(0).getLength();
                    m.columns.put(it.name + "_mm", Csv.num(mm(px, ppm)));
                    m.shown.put(it.name, px == null ? "" : len(px, ppm));
                    break;
                }
                case LENGTH_FROM_POINTS: {
                    double[] a = m.xy.get(it.from), b = m.xy.get(it.to);
                    Double px = a == null || b == null ? null : Math.hypot(a[0] - b[0], a[1] - b[1]);
                    m.columns.put(it.name + "_mm", Csv.num(mm(px, ppm)));
                    m.shown.put(it.name, px == null ? "needs " + it.from + " and " + it.to : len(px, ppm));
                    break;
                }
                case POINT: {
                    double[] p = m.xy.get(it.name);
                    m.shown.put(it.name, p == null ? "" : "done");
                    break;
                }
                case PART: {
                    double a = 0;
                    for (Roi x : r)
                        a += areaPx(x);
                    Double v = r.isEmpty() ? null : a;
                    m.columns.put(it.name + "_area_mm2", Csv.num(mm2(v, ppm)));
                    m.shown.put(it.name, v == null ? "" : area(v, ppm) + (r.size() > 1 ? " (" + r.size() + " pieces)" : ""));
                    break;
                }
                case COLOUR: {
                    double a = 0;
                    for (Roi x : r)
                        a += areaPx(x);
                    boolean complete = done.contains(it.name);
                    // no spot and not confirmed: unknown, not zero
                    Double v = r.isEmpty() && !complete ? null : a;
                    m.columns.put(it.name + "_area_mm2", Csv.num(mm2(v, ppm)));
                    m.columns.put(it.name + "_n", r.isEmpty() && !complete ? Csv.NA : String.valueOf(r.size()));
                    if (s.training())
                        m.columns.put(it.name + "_complete", Csv.bool(complete));
                    m.shown.put(it.name, r.size() + (r.size() == 1 ? " spot" : " spots")
                            + (r.isEmpty() ? "" : ", " + area(a, ppm)) + (complete ? ", all done" : ""));
                    break;
                }
                case COUNT: {
                    int n = 0;
                    for (Roi x : r)
                        n += x.getFloatPolygon().npoints;
                    m.columns.put(it.name + "_n", r.isEmpty() ? Csv.NA : String.valueOf(n));
                    m.shown.put(it.name, r.isEmpty() ? "" : String.valueOf(n));
                    break;
                }
                default: break;
            }
        }
        for (Item it : s.items(Item.Type.POINT)) {
            double[] p = m.xy.get(it.name);
            if (p == null)
                continue;
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("landmark", it.name);
            row.put("x_px", Csv.num(p[0]));
            row.put("y_px", Csv.num(p[1]));
            row.put("x_mm", Csv.num(mm(p[0], ppm)));
            row.put("y_mm", Csv.num(mm(p[1], ppm)));
            m.points.add(row);
        }
        Axis axis = Axis.of(s, m.xy, rois, width, height);
        for (Item it : s.items(Item.Type.COLOUR)) {
            int k = 0;
            for (Roi x : of(rois, it.name)) {
                k++;
                double[] c = centroid(x);
                Map<String, String> row = new LinkedHashMap<String, String>();
                row.put("class", it.name);
                row.put("spot", String.valueOf(k));
                double a = areaPx(x);
                row.put("area_px", Csv.num(a));
                row.put("area_mm2", Csv.num(mm2(a, ppm)));
                row.put("centroid_x_px", Csv.num(c[0]));
                row.put("centroid_y_px", Csv.num(c[1]));
                double[] pos = axis == null ? null : axis.position(c);
                row.put("along_sl", Csv.num(pos == null ? null : pos[0]));
                row.put("along_mm", Csv.num(pos == null ? null : mm(pos[1], ppm)));
                row.put("across_depth", Csv.num(pos == null ? null : pos[2]));
                m.spots.add(row);
            }
        }
        return m;
    }

    /** Required items that are missing, in plain words (training adds its own rules). */
    public static List<String> missing(Setup s, List<Roi> rois, List<String> done, Measures m) {
        List<String> out = new ArrayList<String>();
        for (Item it : s.items) {
            boolean has = it.type == Item.Type.LENGTH_FROM_POINTS
                    ? m.xy.containsKey(it.from) && m.xy.containsKey(it.to) : !of(rois, it.name).isEmpty();
            if (it.required && !has && !(it.type == Item.Type.COLOUR && done.contains(it.name)))
                out.add(it.name + " is missing");
            if (s.training() && it.type == Item.Type.COLOUR && !done.contains(it.name))
                out.add(it.name + ": not confirmed 'all spots done'");
        }
        return out;
    }

    static Double mm(Double px, Double ppm) {
        return px == null || ppm == null ? null : px / ppm;
    }

    static Double mm2(Double px, Double ppm) {
        return px == null || ppm == null ? null : px / (ppm * ppm);
    }

    private static String len(double px, Double ppm) {
        return ppm == null ? Csv.num(px) + " px" : Csv.num(round2(px / ppm)) + " mm";
    }

    private static String area(double px, Double ppm) {
        return ppm == null ? Csv.num(px) + " px²" : Csv.num(round2(px / (ppm * ppm))) + " mm²";
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** Area in pixels as ImageJ's Measure counts it: the pixels inside the ROI mask. */
    public static double areaPx(Roi r) {
        Rectangle b = r.getBounds();
        ImageProcessor mask = r.getMask();
        if (mask == null)
            return (double) b.width * b.height;
        byte[] p = (byte[]) mask.getPixels();
        int n = 0;
        for (byte v : p)
            if (v != 0)
                n++;
        return n;
    }

    /** Centre of the pixels inside the ROI (pixel centres at +0.5). */
    public static double[] centroid(Roi r) {
        Rectangle b = r.getBounds();
        ImageProcessor mask = r.getMask();
        double sx = 0, sy = 0;
        long n = 0;
        for (int y = 0; y < b.height; y++)
            for (int x = 0; x < b.width; x++)
                if (mask == null || mask.get(x, y) != 0) {
                    sx += b.x + x + 0.5;
                    sy += b.y + y + 0.5;
                    n++;
                }
        return n == 0 ? new double[] {b.x, b.y} : new double[] {sx / n, sy / n};
    }

    /** Full-size mask of a list of ROIs: 255 inside. */
    public static ByteProcessor mask(List<Roi> rois, int width, int height) {
        ByteProcessor bp = new ByteProcessor(width, height);
        bp.setValue(255);
        for (Roi r : rois)
            if (r.isArea())
                bp.fill(r);
        return bp;
    }

    /** Body axis from two points: position along it as a fraction of its length, and across the body. */
    static class Axis {
        final double ax, ay, ux, uy, length;
        final ByteProcessor body;

        Axis(double[] a, double[] b, ByteProcessor body) {
            ax = a[0];
            ay = a[1];
            length = Math.hypot(b[0] - a[0], b[1] - a[1]);
            ux = (b[0] - a[0]) / length;
            uy = (b[1] - a[1]) / length;
            this.body = body;
        }

        static Axis of(Setup s, Map<String, double[]> xy, List<Roi> rois, int width, int height) {
            double[] a = xy.get(s.axisFrom), b = xy.get(s.axisTo);
            if (a == null || b == null || Math.hypot(b[0] - a[0], b[1] - a[1]) < 1)
                return null;
            List<Roi> parts = s.bodyPart.isEmpty() ? new ArrayList<Roi>() : Measures.of(rois, s.bodyPart);
            return new Axis(a, b, parts.isEmpty() || width <= 0 ? null : mask(parts, width, height));
        }

        /** {along as fraction of the axis, along in px, across as fraction of body depth (0 = edge on the left
         *  of the axis direction: the back for a fish with the head on the left and the back up)}. */
        double[] position(double[] c) {
            double dx = c[0] - ax, dy = c[1] - ay;
            double along = dx * ux + dy * uy;
            // left of the direction of travel in image coordinates (y down)
            double nx = uy, ny = -ux;
            double t = dx * nx + dy * ny;
            Double across = null;
            if (body != null) {
                double fx = ax + ux * along, fy = ay + uy * along;
                double[] edges = run(fx, fy, nx, ny, t);
                if (edges != null && edges[1] - edges[0] > 0)
                    across = (edges[1] - t) / (edges[1] - edges[0]);
            }
            return new double[] {along / length, along, across == null ? Double.NaN : across};
        }

        /** Inside-body run along the normal through (fx, fy) closest to offset t: {low t, high t}. */
        private double[] run(double fx, double fy, double nx, double ny, double t) {
            double reach = Math.hypot(body.getWidth(), body.getHeight());
            double step = 0.5, lo = Double.NaN, hi = Double.NaN, best = Double.MAX_VALUE;
            double[] out = null;
            boolean in = false;
            for (double k = -reach; k <= reach; k += step) {
                int x = (int) Math.floor(fx + nx * k), y = (int) Math.floor(fy + ny * k);
                boolean inside = x >= 0 && y >= 0 && x < body.getWidth() && y < body.getHeight() && body.get(x, y) != 0;
                if (inside && !in)
                    lo = k;
                if (inside)
                    hi = k;
                if (!inside && in || inside && k + step > reach) {
                    double d = t < lo ? lo - t : t > hi ? t - hi : 0;
                    if (d < best) {
                        best = d;
                        out = new double[] {lo, hi};
                    }
                }
                in = inside;
            }
            return out;
        }
    }

    /** A point ROI at (x, y), as clicked. */
    public static PointRoi point(double x, double y) {
        return new PointRoi(x, y);
    }
}
