package morphotracer;

import ij.gui.Line;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;

/** How a photo is shown: mirrored (left-right) or not, then turned by 0, 90, 180 or 270 degrees clockwise.
 *  Marks are always kept and saved in the coordinates of the original photo; only the display is turned. */
public class View {

    public final int turns;
    public final boolean mirror;

    public View(int turns, boolean mirror) {
        this.turns = ((turns % 4) + 4) % 4;
        this.mirror = mirror;
    }

    public static final View NONE = new View(0, false);

    public boolean none() {
        return turns == 0 && !mirror;
    }

    /** "none", "r90", "r180", "r270", "m", "m+r90"... as written in morphology.csv. */
    @Override
    public String toString() {
        if (none())
            return "none";
        String r = turns == 0 ? "" : "r" + turns * 90;
        return mirror ? (r.isEmpty() ? "m" : "m+" + r) : r;
    }

    /** For the panel: "as taken", "turned 90 degrees right", "mirrored, turned 180 degrees". */
    public String describe() {
        if (none())
            return "as taken";
        String t = turns == 0 ? "" : turns == 2 ? "turned 180 degrees" : turns == 1 ? "turned 90 degrees right"
                : "turned 90 degrees left";
        return mirror ? "mirrored" + (t.isEmpty() ? "" : ", " + t) : t;
    }

    public static View parse(String s) {
        if (s == null || s.isEmpty() || s.equals("none") || s.equals(Csv.NA))
            return NONE;
        boolean m = s.startsWith("m");
        int deg = 0;
        int i = s.indexOf('r');
        if (i >= 0) {
            try {
                deg = Integer.parseInt(s.substring(i + 1));
            } catch (NumberFormatException e) {
                deg = 0;
            }
        }
        return new View(deg / 90, m);
    }

    public View turnRight() {
        return new View(turns + 1, mirror);
    }

    public View turnLeft() {
        return new View(turns + 3, mirror);
    }

    /** Mirror as seen on the screen (left-right of the displayed photo). */
    public View flip() {
        // a left-right flip after the turns equals mirroring the original and turning the other way
        return new View((4 - turns) % 4, !mirror);
    }

    /** Size of the displayed photo for an original of w x h. */
    public int[] size(int w, int h) {
        return turns % 2 == 0 ? new int[] {w, h} : new int[] {h, w};
    }

    public ImageProcessor apply(ImageProcessor ip) {
        ImageProcessor out = ip;
        if (mirror) {
            out = out.duplicate();
            out.flipHorizontal();
        }
        for (int k = 0; k < turns; k++)
            out = out.rotateRight();
        return out;
    }

    /** Original photo coordinates -> displayed. */
    public double[] toDisplay(double x, double y, int w, int h) {
        if (mirror)
            x = w - x;
        int cw = w, ch = h;
        for (int k = 0; k < turns; k++) {
            double nx = ch - y, ny = x;
            x = nx;
            y = ny;
            int t = cw;
            cw = ch;
            ch = t;
        }
        return new double[] {x, y};
    }

    /** Displayed coordinates -> original photo. */
    public double[] toOriginal(double x, double y, int w, int h) {
        int[] d = size(w, h);
        int cw = d[0], ch = d[1];
        for (int k = 0; k < turns; k++) {
            // undo one clockwise turn: (x, y) in cw x ch came from (y, cw - x) in ch x cw
            double nx = y, ny = cw - x;
            x = nx;
            y = ny;
            int t = cw;
            cw = ch;
            ch = t;
        }
        if (mirror)
            x = w - x;
        return new double[] {x, y};
    }

    public Roi toDisplay(Roi r, int w, int h) {
        return none() ? (Roi) r.clone() : map(r, w, h, true);
    }

    public Roi toOriginal(Roi r, int w, int h) {
        return none() ? (Roi) r.clone() : map(r, w, h, false);
    }

    private Roi map(Roi r, int w, int h, boolean forward) {
        Roi out;
        if (r instanceof Line && r.getType() == Roi.LINE) {
            Line l = (Line) r;
            double[] a = pt(l.x1d, l.y1d, w, h, forward), b = pt(l.x2d, l.y2d, w, h, forward);
            out = new Line(a[0], a[1], b[0], b[1]);
        } else {
            FloatPolygon p = r.getFloatPolygon();
            float[] xs = new float[p.npoints], ys = new float[p.npoints];
            for (int k = 0; k < p.npoints; k++) {
                double[] q = pt(p.xpoints[k], p.ypoints[k], w, h, forward);
                xs[k] = (float) q[0];
                ys[k] = (float) q[1];
            }
            if (r instanceof PointRoi)
                out = new PointRoi(xs, ys, p.npoints);
            else if (r.isLine())
                out = new PolygonRoi(xs, ys, p.npoints, Roi.POLYLINE);
            else
                out = new PolygonRoi(xs, ys, p.npoints, r.getType() == Roi.FREEROI ? Roi.FREEROI : Roi.POLYGON);
        }
        copyLabels(r, out);
        return out;
    }

    private double[] pt(double x, double y, int w, int h, boolean forward) {
        return forward ? toDisplay(x, y, w, h) : toOriginal(x, y, w, h);
    }

    static void copyLabels(Roi from, Roi to) {
        to.setName(from.getName());
        to.setStrokeColor(from.getStrokeColor());
        for (String k : new String[] {Measures.ITEM, "mm", Measures.AUTO}) {
            String v = from.getProperty(k);
            if (v != null)
                to.setProperty(k, v);
        }
    }
}
