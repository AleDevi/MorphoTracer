package morphotracer;

import ij.gui.Line;
import ij.gui.Roi;

import java.util.List;
import java.util.Map;

/** The scale of one photo and where it comes from:
 *  drawn (on this photo, for this photo only), drawn_shared (on this photo, used by the next photos too),
 *  folder (from the folder's shared scale), carried (from the last photo with a scale), none (no scale possible). */
public class Scale {

    public static final String DRAWN = "drawn", SHARED = "drawn_shared", FOLDER = "folder", CARRIED = "carried",
            NONE = "none", TO_SET = "";

    public final Double ppm;
    public final String source;
    /** Photo where the scale line was drawn ("" for none). */
    public final String photo;
    public final double mm;

    public Scale(Double ppm, String source, String photo, double mm) {
        this.ppm = ppm;
        this.source = source;
        this.photo = photo;
        this.mm = mm;
    }

    public boolean set() {
        return !source.equals(TO_SET);
    }

    public static Scale toSet() {
        return new Scale(null, TO_SET, "", 0);
    }

    /** A scale line over a known length. */
    public static Scale drawn(Roi line, double mm, boolean shared, String photo) {
        return new Scale(line.getLength() / mm, shared ? SHARED : DRAWN, photo, mm);
    }

    public static Line line(List<Roi> rois) {
        for (Roi r : rois)
            if (Measures.SCALE.equals(Measures.itemOf(r)) && r instanceof Line)
                return (Line) r;
        return null;
    }

    /** Scale of a photo: its own saved one, else what the setup's mode gives (folder or last photo), else to set. */
    public static Scale forPhoto(Setup s, Store store, String key, String folder, List<Roi> savedRois) {
        Map<String, String> own = store.row(key);
        if (own != null && !Csv.NA.equals(own.get("scale_source"))) {
            String src = own.get("scale_source");
            Line l = line(savedRois);
            double mm = l == null ? 0 : parseMm(l);
            return new Scale(Csv.parseNum(own.get("px_per_mm")), src, own.get("scale_photo"), mm);
        }
        Map<String, String> from = null;
        String src = TO_SET;
        if (s.scaleMode == Setup.ScaleMode.PER_FOLDER) {
            from = store.folderScale(folder);
            src = FOLDER;
        } else if (s.scaleMode == Setup.ScaleMode.CARRY_FORWARD) {
            from = store.lastScale();
            src = CARRIED;
        }
        if (from == null || Csv.parseNum(from.get("px_per_mm")) == null)
            return toSet();
        String photo = from.get("scale_photo");
        if (photo == null || photo.equals(Csv.NA))
            photo = from.get("photo");
        return new Scale(Csv.parseNum(from.get("px_per_mm")), src, photo, 0);
    }

    /** When nothing is set yet, should a new line become the shared scale? Yes unless the mode is Every photo. */
    public static boolean shareByDefault(Setup s) {
        return s.scaleMode != Setup.ScaleMode.EVERY_PHOTO;
    }

    static double parseMm(Roi line) {
        Double v = Csv.parseNum(line.getProperty("mm"));
        return v == null ? 0 : v;
    }

    /** One line for the panel. */
    public String describe() {
        if (source.equals(TO_SET))
            return "not set: draw a line over a known length and press S";
        if (source.equals(NONE))
            return "none (no scale possible): sizes in pixels only";
        String px = ppm == null ? "?" : Csv.num(Math.round(ppm * 100) / 100.0);
        String what = px + " px/mm";
        if (source.equals(DRAWN))
            return what + " (drawn here, this photo only)";
        if (source.equals(SHARED))
            return what + " (drawn here, used by the next photos)";
        if (source.equals(FOLDER))
            return what + " (folder scale, from " + photo + ")";
        return what + " (carried from " + photo + ")";
    }
}
