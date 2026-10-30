package morphotracer;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** A fish lying head-left: body 400 x 100 px rectangle from x=100 to 500, y=150 to 250; 10 px per mm. */
    static Setup setup(Setup.Purpose p) {
        Setup s = new Setup();
        s.name = "test";
        s.purpose = p;
        s.items.add(new Item("sl", Item.Type.LENGTH, true, ""));
        s.items.add(new Item("snout", Item.Type.POINT, true, ""));
        s.items.add(new Item("peduncle_end", Item.Type.POINT, true, ""));
        s.items.add(new Item("eye", Item.Type.POINT, false, ""));
        Item slp = new Item("sl_points", Item.Type.LENGTH_FROM_POINTS, false, "");
        slp.from = "snout";
        slp.to = "peduncle_end";
        s.items.add(slp);
        s.items.add(new Item("body", Item.Type.PART, true, ""));
        s.items.add(new Item("orange", Item.Type.COLOUR, true, ""));
        s.items.add(new Item("black", Item.Type.COLOUR, false, ""));
        s.items.add(new Item("rays", Item.Type.COUNT, false, ""));
        s.axisFrom = "snout";
        s.axisTo = "peduncle_end";
        s.bodyPart = "body";
        return s;
    }

    static Roi named(Roi r, String item, String name) {
        r.setName(name);
        r.setProperty(Measures.ITEM, item);
        return r;
    }

    static List<Roi> fish() {
        List<Roi> r = new ArrayList<Roi>();
        Line scale = new Line(10, 10, 110, 10);
        r.add(named(scale, Measures.SCALE, Measures.SCALE));
        r.add(named(new Line(100, 200, 500, 200), "sl", "sl"));
        r.add(named(new PointRoi(100, 200), "snout", "snout"));
        r.add(named(new PointRoi(500, 200), "peduncle_end", "peduncle_end"));
        r.add(named(new Roi(100, 150, 400, 100), "body", "body"));
        // two orange spots: 20x20 near the back at 1/4 of SL, 10x10 near the belly at 3/4
        r.add(named(new Roi(190, 155, 20, 20), "orange", "orange_1"));
        r.add(named(new Roi(395, 235, 10, 10), "orange", "orange_2"));
        PointRoi rays = new PointRoi(new float[] {600, 610, 620}, new float[] {50, 50, 50}, 3);
        r.add(named(rays, "rays", "rays"));
        return r;
    }

    @Test
    public void sizesAreasCountsInMm() {
        Setup s = setup(Setup.Purpose.MEASURES);
        Measures m = Measures.compute(s, fish(), 10.0, 700, 400, Arrays.asList("orange"));
        assertEquals("40", m.columns.get("sl_mm"));
        assertEquals("40", m.columns.get("sl_points_mm"));
        assertEquals("400", m.columns.get("body_area_mm2"));          // 40000 px / 100
        assertEquals("5", m.columns.get("orange_area_mm2"));          // (400 + 100) / 100
        assertEquals("2", m.columns.get("orange_n"));
        assertEquals("NA", m.columns.get("black_area_mm2"));          // nothing traced, not confirmed: unknown
        assertEquals("3", m.columns.get("rays_n"));
        assertFalse(m.columns.containsKey("orange_complete"));        // only in training setups
        assertEquals(2, m.points.size());
    }

    @Test
    public void confirmedEmptyClassIsZero() {
        Setup s = setup(Setup.Purpose.TRAINING);
        Measures m = Measures.compute(s, fish(), 10.0, 700, 400, Arrays.asList("orange", "black"));
        assertEquals("0", m.columns.get("black_area_mm2"));
        assertEquals("0", m.columns.get("black_n"));
        assertEquals("TRUE", m.columns.get("black_complete"));
    }

    @Test
    public void noScaleGivesNa() {
        Measures m = Measures.compute(setup(Setup.Purpose.MEASURES), fish(), null, 700, 400, new ArrayList<String>());
        assertEquals("NA", m.columns.get("sl_mm"));
        assertEquals("NA", m.columns.get("body_area_mm2"));
        assertEquals("400", m.points.get(0).get("x_px").equals("100") ? "400" : "x");
    }

    @Test
    public void spotPositionsAlongAndAcross() {
        Measures m = Measures.compute(setup(Setup.Purpose.MEASURES), fish(), 10.0, 700, 400, Arrays.asList("orange"));
        Map<String, String> a = m.spots.get(0), b = m.spots.get(1);
        assertEquals(0.25, Double.parseDouble(a.get("along_sl")), 0.01);
        assertEquals(10, Double.parseDouble(a.get("along_mm")), 0.1);
        assertEquals(0.15, Double.parseDouble(a.get("across_depth")), 0.02);   // centre 15 px below the back of 100
        assertEquals(0.75, Double.parseDouble(b.get("along_sl")), 0.01);
        assertEquals(0.9, Double.parseDouble(b.get("across_depth")), 0.02);
    }

    @Test
    public void missingAndTrainingRules() {
        Setup s = setup(Setup.Purpose.TRAINING);
        List<Roi> r = fish();
        r.remove(4);                                                     // no body
        List<String> done = Arrays.asList("orange");
        Measures m = Measures.compute(s, r, 10.0, 700, 400, done);
        List<String> miss = Measures.missing(s, r, done, m);
        assertTrue(miss.contains("body is missing"));
        assertTrue(miss.contains("black: not confirmed 'all spots done'"));
        assertFalse(miss.toString().contains("eye"));                   // optional
        assertTrue(Measures.missing(setup(Setup.Purpose.MEASURES), r, done, m).size() == 1);
    }

    @Test
    public void storeSavesReopensAndRewritesOnePhoto() throws Exception {
        Setup s = setup(Setup.Purpose.BOTH);
        File dir = tmp.newFolder("results");
        Store st = new Store(dir, s);
        List<Roi> rois = fish();
        Measures m = Measures.compute(s, rois, 10.0, 700, 400, Arrays.asList("orange", "black"));
        Map<String, String> row = Panel.row(s, "IMG_1.JPG", "day1", "M1", 700, 400,
                new Scale(10.0, Scale.SHARED, "IMG_1.JPG", 10), m, true, "", "a, note");
        st.save("day1/IMG_1.JPG", row, m, rois, 700, 400);
        st.save("day1/IMG_1.JPG", row, m, rois, 700, 400);            // saving again replaces, no duplicates

        Store again = new Store(dir, s);
        assertEquals(1, again.rows.size());
        assertEquals("M1", again.row("day1/IMG_1.JPG").get("fish_id"));
        assertEquals("a, note", again.row("day1/IMG_1.JPG").get("notes"));
        assertEquals(2, again.points.size());
        assertEquals(2, again.spots.size());
        List<Roi> back = Store.readRois(again.roiFile("day1/IMG_1.JPG"));
        assertEquals(rois.size(), back.size());
        assertEquals("orange", Measures.itemOf(back.get(5)));
        assertEquals("orange_1", back.get(5).getName());
        assertTrue(new File(dir, "setup.json").exists());
        assertTrue(new File(dir, "morphometrics/landmarks.tps").exists());
        String tps = new String(java.nio.file.Files.readAllBytes(new File(dir, "morphometrics/landmarks.tps").toPath()));
        assertTrue(tps.startsWith("LM=3\n100 200\n500 200\n-1 -1\n"));
        ImagePlus colours = IJ.openImage(new File(dir, "masks/day1__IMG_1_colours.png").getPath());
        assertEquals(1, colours.getProcessor().get(200, 160));        // orange = class 1
        assertEquals(0, colours.getProcessor().get(300, 200));
        ImagePlus parts = IJ.openImage(new File(dir, "masks/day1__IMG_1_parts.png").getPath());
        assertEquals(1, parts.getProcessor().get(300, 200));
    }

    @Test
    public void measuresOnlyMakesNoMasks() throws Exception {
        Setup s = setup(Setup.Purpose.MEASURES);
        s.items.removeAll(s.items(Item.Type.POINT));
        s.items.removeAll(s.items(Item.Type.COLOUR));
        s.items.removeAll(s.items(Item.Type.LENGTH_FROM_POINTS));
        s.axisFrom = s.axisTo = "";
        File dir = tmp.newFolder("results");
        Store st = new Store(dir, s);
        Measures m = Measures.compute(s, fish(), 10.0, 700, 400, new ArrayList<String>());
        st.save("IMG_2.JPG", Panel.row(s, "IMG_2.JPG", ".", "", 700, 400, Scale.toSet(), m, true, "", ""),
                m, fish(), 700, 400);
        assertFalse(new File(dir, "masks").exists());
        assertFalse(new File(dir, "morphometrics").exists());
        assertTrue(new File(dir, "morphology.csv").exists());
    }

    @Test
    public void scaleModes() throws Exception {
        Setup s = setup(Setup.Purpose.MEASURES);
        File dir = tmp.newFolder("results");
        Store st = new Store(dir, s);
        Measures m = Measures.compute(s, fish(), 10.0, 700, 400, new ArrayList<String>());
        st.save("d1/A.JPG", Panel.row(s, "A.JPG", "d1", "", 700, 400, new Scale(10.0, Scale.SHARED, "A.JPG", 10), m, true, "", ""), m, fish(), 700, 400);
        Thread.sleep(1100);
        st.save("d1/B.JPG", Panel.row(s, "B.JPG", "d1", "", 700, 400, new Scale(12.0, Scale.DRAWN, "B.JPG", 10), m, true, "", ""), m, fish(), 700, 400);

        s.scaleMode = Setup.ScaleMode.PER_FOLDER;
        Scale c = Scale.forPhoto(s, st, "d1/C.JPG", "d1", new ArrayList<Roi>());
        assertEquals(Scale.FOLDER, c.source);                          // B was for B only
        assertEquals(10.0, c.ppm, 1e-9);
        assertEquals("A.JPG", c.photo);
        assertFalse(Scale.forPhoto(s, st, "d2/D.JPG", "d2", new ArrayList<Roi>()).set());

        s.scaleMode = Setup.ScaleMode.CARRY_FORWARD;
        Scale d = Scale.forPhoto(s, st, "d2/D.JPG", "d2", new ArrayList<Roi>());
        assertEquals(Scale.CARRIED, d.source);
        assertEquals(12.0, d.ppm, 1e-9);                                // the last one used

        s.scaleMode = Setup.ScaleMode.EVERY_PHOTO;
        assertFalse(Scale.forPhoto(s, st, "d2/D.JPG", "d2", new ArrayList<Roi>()).set());
        assertEquals(Scale.DRAWN, Scale.forPhoto(s, st, "d1/B.JPG", "d1", new ArrayList<Roi>()).source); // a saved photo keeps its own
    }

    @Test
    public void csvForR() throws Exception {
        assertEquals("'=SUM(A1)", Csv.text("=SUM(A1)"));
        assertEquals("NA", Csv.num(null));
        assertEquals("0.3333", Csv.num(1 / 3.0));
        File f = tmp.newFile("t.csv");
        Csv.write(f, Arrays.asList("a", "b"), Arrays.asList((Map<String, String>) new java.util.HashMap<String, String>() {{
            put("a", "x, \"y\"");
            put("b", "1.5");
        }}));
        List<String> h = new ArrayList<String>();
        List<Map<String, String>> rows = Csv.read(f, h);
        assertEquals("x, \"y\"", rows.get(0).get("a"));
        assertEquals("1.5", rows.get(0).get("b"));
    }

    @Test
    public void setupRoundTripAndChecks() throws Exception {
        Setup s = setup(Setup.Purpose.BOTH);
        s.idPattern = "(M\\d+)";
        File f = tmp.newFile("s.json");
        s.save(f);
        Setup b = Setup.load(f);
        assertEquals(s.items.size(), b.items.size());
        assertEquals("snout", b.item("sl_points").from);
        assertEquals(Setup.Purpose.BOTH, b.purpose);
        assertTrue(b.problems().isEmpty());
        assertEquals("M103", b.idFromFile("IMG_9860_M103.JPG"));
        b.items.add(new Item("bad name", Item.Type.PART, false, ""));
        b.items.add(new Item("scale", Item.Type.LENGTH, false, ""));
        assertEquals(2, b.problems().size());
    }

    @Test
    public void guppyColourProfileImport() throws Exception {
        File f = new File(getClass().getResource("/guppy_male.json").toURI());
        List<Item> items = Setup.fromProfile(f);
        assertEquals("snout", items.get(0).name);
        assertEquals(Item.Type.POINT, items.get(0).type);
        assertTrue(items.get(0).required);
        Setup s = new Setup();
        s.items.addAll(items);
        assertEquals(Item.Type.PART, s.item("caudal").type);
        assertFalse(s.item("caudal").required);
        assertEquals(Item.Type.COLOUR, s.item("orange").type);
        assertFalse(s.item("iridescent").required);
        assertTrue(s.problems().isEmpty());
    }

    @Test
    public void bundledExampleSetupIsValid() throws Exception {
        for (String n : MorphoTracer_.EXAMPLES) {
            Setup s = Setup.fromMap((Map<String, Object>) Json.parse(MorphoTracer_.example(n)));
            assertTrue(n + ": " + s.problems(), s.problems().isEmpty());
        }
    }
}
