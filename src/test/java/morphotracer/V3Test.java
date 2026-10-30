package morphotracer;

import ij.gui.Line;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** 0.3.0: turned / mirrored views, SL -> TL with points from the line ends, colour % of body + caudal. */
public class V3Test {

    @Test
    public void viewsGoBackToTheOriginal() {
        int w = 300, h = 200;
        for (int t = 0; t < 4; t++)
            for (boolean m : new boolean[] {false, true}) {
                View v = new View(t, m);
                double[] d = v.toDisplay(37.5, 12.25, w, h);
                double[] o = v.toOriginal(d[0], d[1], w, h);
                assertEquals(v + " x", 37.5, o[0], 1e-9);
                assertEquals(v + " y", 12.25, o[1], 1e-9);
                assertEquals(v.toString(), View.parse(v.toString()).toString());
            }
    }

    @Test
    public void displayedPixelIsTheOriginalPixel() {
        int w = 300, h = 200;
        ByteProcessor ip = new ByteProcessor(w, h);
        ip.set(40, 10, 255);
        for (int t = 0; t < 4; t++)
            for (boolean m : new boolean[] {false, true}) {
                View v = new View(t, m);
                ImageProcessor shown = v.apply(ip);
                double[] d = v.toDisplay(40.5, 10.5, w, h);          // centre of the marked pixel
                assertEquals(v.toString(), 255, shown.get((int) Math.floor(d[0]), (int) Math.floor(d[1])));
                assertArrayEquals(v.size(w, h), new int[] {shown.getWidth(), shown.getHeight()});
            }
    }

    @Test
    public void flipMirrorsWhatIsOnTheScreen() {
        int w = 300, h = 200;
        View v = new View(1, false), f = v.flip();
        int[] s = v.size(w, h);
        double[] a = v.toDisplay(40, 10, w, h), b = f.toDisplay(40, 10, w, h);
        assertEquals(s[0] - a[0], b[0], 1e-9);
        assertEquals(a[1], b[1], 1e-9);
    }

    @Test
    public void roiDrawnOnATurnedPhotoIsSavedOnTheOriginal() {
        View v = new View(1, true);
        Roi spot = new PolygonRoi(new float[] {10, 50, 50, 10}, new float[] {20, 20, 60, 60}, 4, Roi.POLYGON);
        spot.setProperty(Measures.ITEM, "orange");
        Roi back = v.toDisplay(v.toOriginal(spot, 300, 200), 300, 200);
        assertEquals(Measures.areaPx(spot), Measures.areaPx(back), 0.5);
        assertEquals("orange", back.getProperty(Measures.ITEM));
        Line l = new Line(0, 0, 30, 40);
        assertEquals(50, v.toOriginal(l, 300, 200).getLength(), 1e-6);
    }

    static Setup guppy() {
        Setup s = new Setup();
        s.name = "g";
        Item sl = new Item("sl", Item.Type.LENGTH, true, "");
        sl.from = "snout";
        sl.to = "peduncle_end";
        Item tl = new Item("tl", Item.Type.LENGTH, true, "");
        tl.continues = "sl";
        tl.to = "tail_tip";
        s.items.addAll(Arrays.asList(sl, tl, new Item("snout", Item.Type.POINT, true, ""),
                new Item("peduncle_end", Item.Type.POINT, true, ""), new Item("tail_tip", Item.Type.POINT, false, ""),
                new Item("body", Item.Type.PART, true, ""), new Item("caudal", Item.Type.PART, true, ""),
                new Item("orange", Item.Type.COLOUR, true, "")));
        s.percentOf = Arrays.asList("body", "caudal");
        return s;
    }

    @Test
    public void slSetsItsPointsAndTlContinuesIt() {
        Setup s = guppy();
        assertTrue(s.problems().toString(), s.problems().isEmpty());
        List<Roi> rois = new ArrayList<Roi>();
        Roi sl = new PolygonRoi(new float[] {100, 300, 500}, new float[] {200, 210, 200}, 3, Roi.POLYLINE);
        Measures.placeLength(s, s.item("sl"), sl, rois);
        // the tail line starts a little off the end of SL: it is joined there
        Measures.placeLength(s, s.item("tl"), new Line(505, 195, 600, 195), rois);
        Measures m = Measures.compute(s, rois, 10.0, 800, 400, new ArrayList<String>());
        assertArrayEquals(new double[] {100, 200}, m.xy.get("snout"), 1e-6);
        assertArrayEquals(new double[] {500, 200}, m.xy.get("peduncle_end"), 1e-6);
        assertArrayEquals(new double[] {600, 195}, m.xy.get("tail_tip"), 1e-6);
        double slPx = Math.hypot(200, 10) * 2, tail = Math.hypot(100, 5);
        assertEquals(slPx / 10, Double.parseDouble(m.columns.get("sl_mm")), 1e-3);
        assertEquals((slPx + tail) / 10, Double.parseDouble(m.columns.get("tl_mm")), 1e-3);

        // SL drawn again: peduncle_end moves and the tail line follows it
        Measures.placeLength(s, s.item("sl"), new Line(100, 200, 480, 200), rois);
        Measures m2 = Measures.compute(s, rois, 10.0, 800, 400, new ArrayList<String>());
        assertArrayEquals(new double[] {480, 200}, m2.xy.get("peduncle_end"), 1e-6);
        assertEquals((380 + Math.hypot(120, 5)) / 10, Double.parseDouble(m2.columns.get("tl_mm")), 1e-3);
        assertEquals(1, Measures.of(rois, "peduncle_end").size());
    }

    @Test
    public void tlWithoutSlIsNa() {
        Setup s = guppy();
        List<Roi> rois = new ArrayList<Roi>();
        Measures.placeLength(s, s.item("tl"), new Line(500, 200, 600, 200), rois);
        assertEquals("NA", Measures.compute(s, rois, 10.0, 800, 400, new ArrayList<String>()).columns.get("tl_mm"));
    }

    @Test
    public void colourPercentOfBodyAndCaudal() {
        Setup s = guppy();
        List<Roi> rois = new ArrayList<Roi>();
        rois.add(CoreTest.named(new Roi(100, 150, 400, 100), "body", "body"));      // 40000 px
        rois.add(CoreTest.named(new Roi(500, 150, 100, 100), "caudal", "caudal"));  // 10000 px
        rois.add(CoreTest.named(new Roi(200, 160, 50, 50), "orange", "orange_1"));  // 2500 px
        Measures m = Measures.compute(s, rois, null, 800, 400, Arrays.asList("orange"));
        assertEquals("5", m.columns.get("orange_percent"));                          // no scale needed
        rois.remove(1);
        assertEquals("NA", Measures.compute(s, rois, null, 800, 400, Arrays.asList("orange")).columns.get("orange_percent"));
        s.percentOf = Arrays.asList("fin");
        assertFalse(s.problems().isEmpty());
    }

    @Test
    public void loopsInContinuesAreRefused() {
        Setup s = guppy();
        s.item("sl").continues = "tl";
        assertFalse(s.problems().isEmpty());
    }
}
