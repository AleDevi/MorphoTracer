package morphotracer;

import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.RoiDecoder;
import ij.io.RoiEncoder;
import ij.ImagePlus;
import ij.process.ByteProcessor;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** The results folder of one setup:
 *  morphology.csv and setup.json at the top; morphometrics/ (points, spots), rois/ (one zip per photo, to reopen
 *  and correct a photo) and masks/ (training only) are made only when needed. Photos are never written. */
public class Store {

    public static final List<String> FIXED = Collections.unmodifiableList(Arrays.asList(
            "photo", "folder", "fish_id", "setup", "purpose", "annotator", "date",
            "image_width", "image_height", "px_per_mm", "scale_source", "scale_photo"));
    public static final List<String> END = Collections.unmodifiableList(Arrays.asList("complete", "flags", "notes"));
    static final List<String> POINT_COLS = Arrays.asList("photo", "folder", "fish_id", "landmark", "x_px", "y_px", "x_mm", "y_mm");
    static final List<String> SPOT_COLS = Arrays.asList("photo", "folder", "fish_id", "class", "spot", "area_px", "area_mm2",
            "centroid_x_px", "centroid_y_px", "along_sl", "along_mm", "across_depth");

    public final File dir;
    public final Setup setup;
    /** photo key (folder/photo) -> row of morphology.csv, in saving order. */
    final Map<String, Map<String, String>> rows = new LinkedHashMap<String, Map<String, String>>();
    final List<String> extraColumns = new ArrayList<String>();
    final List<Map<String, String>> points = new ArrayList<Map<String, String>>();
    final List<Map<String, String>> spots = new ArrayList<Map<String, String>>();

    public Store(File dir, Setup setup) throws IOException {
        this.dir = dir;
        this.setup = setup;
        List<String> header = new ArrayList<String>();
        for (Map<String, String> r : Csv.read(morphology(), header))
            rows.put(key(r.get("folder"), r.get("photo")), r);
        for (String h : header)
            if (!columns().contains(h))
                extraColumns.add(h);
        points.addAll(Csv.read(new File(morphometrics(), "landmarks.csv"), new ArrayList<String>()));
        spots.addAll(Csv.read(new File(morphometrics(), "spots.csv"), new ArrayList<String>()));
    }

    public static String key(String folder, String photo) {
        return (folder == null || folder.isEmpty() || folder.equals(".") ? "" : folder + "/") + photo;
    }

    File morphology() {
        return new File(dir, "morphology.csv");
    }

    File morphometrics() {
        return new File(dir, "morphometrics");
    }

    /** Header of morphology.csv: fixed columns, one block per item, then complete/flags/notes. */
    public List<String> columns() {
        List<String> c = new ArrayList<String>(FIXED);
        c.addAll(Measures.columnNames(setup));
        c.addAll(END);
        return c;
    }

    public Map<String, String> row(String key) {
        return rows.get(key);
    }

    public File roiFile(String key) {
        return new File(new File(dir, "rois"), safe(key) + ".zip");
    }

    static String safe(String key) {
        return key.replace("/", "__").replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** Save one photo: its row, its points and spots, its ROIs, and (training) its masks. */
    public void save(String key, Map<String, String> row, Measures m, List<Roi> rois, int width, int height)
            throws IOException {
        dir.mkdirs();
        rows.remove(key);
        rows.put(key, row);
        removeRows(points, key);
        removeRows(spots, key);
        for (Map<String, String> p : m.points)
            points.add(withPhoto(row, p));
        for (Map<String, String> p : m.spots)
            spots.add(withPhoto(row, p));
        writeRois(roiFile(key), rois);
        if (setup.training() && width > 0)
            writeMasks(key, rois, width, height);
        writeTables();
        setup.save(new File(dir, "setup.json"));
    }

    private static void removeRows(List<Map<String, String>> list, String key) {
        for (int k = list.size() - 1; k >= 0; k--)
            if (key(list.get(k).get("folder"), list.get(k).get("photo")).equals(key))
                list.remove(k);
    }

    private static Map<String, String> withPhoto(Map<String, String> row, Map<String, String> p) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        out.put("photo", row.get("photo"));
        out.put("folder", row.get("folder"));
        out.put("fish_id", row.get("fish_id"));
        out.putAll(p);
        return out;
    }

    void writeTables() throws IOException {
        List<String> header = columns();
        header.addAll(extraColumns);
        Csv.write(morphology(), header, new ArrayList<Map<String, String>>(rows.values()));
        if (setup.hasMorphometrics() || !points.isEmpty() || !spots.isEmpty()) {
            morphometrics().mkdirs();
            Csv.write(new File(morphometrics(), "landmarks.csv"), POINT_COLS, points);
            Csv.write(new File(morphometrics(), "spots.csv"), SPOT_COLS, spots);
            writeTps(new File(morphometrics(), "landmarks.tps"));
        }
    }

    /** Landmarks for geomorph (readland.tps): one block per photo, y measured upward, missing points -1 -1
     *  (read them with negNA = TRUE); SCALE = mm per pixel when the photo has a scale. */
    void writeTps(File f) throws IOException {
        List<Item> lm = setup.items(Item.Type.POINT);
        if (lm.isEmpty())
            return;
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> e : rows.entrySet()) {
            Map<String, String> row = e.getValue();
            Map<String, double[]> xy = new LinkedHashMap<String, double[]>();
            for (Map<String, String> p : points)
                if (key(p.get("folder"), p.get("photo")).equals(e.getKey()))
                    xy.put(p.get("landmark"), new double[] {Csv.parseNum(p.get("x_px")), Csv.parseNum(p.get("y_px"))});
            if (xy.isEmpty())
                continue;
            Double h = Csv.parseNum(row.get("image_height"));
            b.append("LM=").append(lm.size()).append('\n');
            for (Item it : lm) {
                double[] p = xy.get(it.name);
                if (p == null)
                    b.append("-1 -1\n");
                else
                    b.append(Csv.num(p[0])).append(' ').append(Csv.num(h == null ? -p[1] : h - p[1])).append('\n');
            }
            b.append("IMAGE=").append(row.get("photo")).append('\n');
            b.append("ID=").append(tpsId(row)).append('\n');
            Double ppm = Csv.parseNum(row.get("px_per_mm"));
            if (ppm != null)
                b.append("SCALE=").append(Csv.num(1 / ppm)).append('\n');
            b.append('\n');
        }
        File tmp = new File(f.getPath() + ".tmp");
        Files.write(tmp.toPath(), b.toString().getBytes("UTF-8"));
        replace(tmp, f);
    }

    private static String tpsId(Map<String, String> row) {
        String id = row.get("fish_id");
        return id == null || id.equals(Csv.NA) ? row.get("photo") : id;
    }

    void writeMasks(String key, List<Roi> rois, int width, int height) throws IOException {
        File masks = new File(dir, "masks");
        masks.mkdirs();
        String base = safe(key).replaceFirst("\\.[^.]*$", "");
        write(new File(masks, base + "_parts.png"), labels(rois, setup.items(Item.Type.PART), width, height));
        if (!setup.items(Item.Type.COLOUR).isEmpty())
            write(new File(masks, base + "_colours.png"), labels(rois, setup.items(Item.Type.COLOUR), width, height));
    }

    /** Full-size label image: 0 = nothing, then 1, 2, ... in the setup's order (later items drawn over earlier ones). */
    public static ByteProcessor labels(List<Roi> rois, List<Item> items, int width, int height) {
        ByteProcessor bp = new ByteProcessor(width, height);
        int v = 0;
        for (Item it : items) {
            v++;
            bp.setValue(v);
            for (Roi r : Measures.of(rois, it.name))
                if (r.isArea())
                    bp.fill(r);
        }
        return bp;
    }

    private static void write(File f, ByteProcessor bp) throws IOException {
        File tmp = new File(f.getPath() + ".tmp.png");
        if (!new FileSaver(new ImagePlus("mask", bp)).saveAsPng(tmp.getPath()))
            throw new IOException("Could not write " + f.getName());
        replace(tmp, f);
    }

    public static void writeRois(File f, List<Roi> rois) throws IOException {
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));
        try {
            int k = 0;
            for (Roi r : rois) {
                k++;
                String n = r.getName() == null ? "roi" : r.getName();
                zos.putNextEntry(new ZipEntry(String.format("%03d-%s.roi", k, n.replaceAll("[^A-Za-z0-9_-]", "_"))));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                RoiEncoder re = new RoiEncoder(out);
                re.write(r);
                out.flush();
                zos.write(bytes.toByteArray());
                zos.closeEntry();
            }
        } finally {
            zos.close();
        }
        replace(tmp, f);
    }

    /** ROIs of a zip, in their saved order. The size limit keeps a damaged file from filling the memory. */
    public static List<Roi> readRois(File f) throws IOException {
        List<Roi> out = new ArrayList<Roi>();
        if (!f.exists())
            return out;
        ZipInputStream zis = new ZipInputStream(new FileInputStream(f));
        try {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zis.getNextEntry()) != null) {
                if (!e.getName().endsWith(".roi"))
                    continue;
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buf)) > 0) {
                    b.write(buf, 0, n);
                    if (b.size() > 50000000)
                        throw new IOException(f.getName() + " is damaged (an entry is too large).");
                }
                Roi r = new RoiDecoder(b.toByteArray(), e.getName()).getRoi();
                if (r != null)
                    out.add(r);
            }
        } finally {
            zis.close();
        }
        return out;
    }

    static void replace(File tmp, File f) throws IOException {
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** The last scale saved in a folder that was made the folder scale (scale_source drawn_shared), or null. */
    public Map<String, String> folderScale(String folder) {
        Map<String, String> best = null;
        for (Map<String, String> r : rows.values())
            if (eq(r.get("folder"), folder) && "drawn_shared".equals(r.get("scale_source"))
                    && (best == null || r.get("date").compareTo(best.get("date")) >= 0))
                best = r;
        return best;
    }

    /** The most recently saved row that has a scale, or null (for Carry forward). */
    public Map<String, String> lastScale() {
        Map<String, String> best = null;
        for (Map<String, String> r : rows.values())
            if (Csv.parseNum(r.get("px_per_mm")) != null && (best == null || r.get("date").compareTo(best.get("date")) >= 0))
                best = r;
        return best;
    }

    private static boolean eq(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    static InputStream resource(String name) {
        return Store.class.getResourceAsStream(name);
    }
}
