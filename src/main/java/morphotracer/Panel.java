package morphotracer;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.io.DirectoryChooser;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The MorphoTracer window: pick a setup and a photo folder, then for each photo set the scale, mark every item,
 *  and Save fish. Keys on the photo: A add the selection, N skip a point, S / Shift+S scale, 1-9 pick an item. */
public class Panel extends JFrame {

    static Panel instance;
    static final String[] IMAGES = {".jpg", ".jpeg", ".png", ".tif", ".tiff", ".bmp"};

    Setup setup;
    Store store;
    File photosDir;
    List<File> photos = new ArrayList<File>();
    int index = -1;
    ImagePlus imp;
    int origW, origH;
    List<Roi> rois = new ArrayList<Roi>();
    List<String> done = new ArrayList<String>();
    Scale scale = Scale.toSet();
    boolean dirty;
    boolean updating;

    final JComboBox<String> setups = new JComboBox<String>();
    final JLabel folderLabel = new JLabel("No photo folder yet");
    final JLabel resultsLabel = new JLabel(" ");
    final JComboBox<String> photoBox = new JComboBox<String>();
    final JTextField fishId = new JTextField(10);
    final JLabel scaleLabel = new JLabel(" ");
    final DefaultListModel<String> itemModel = new DefaultListModel<String>();
    final JList<String> itemList = new JList<String>(itemModel);
    final JLabel hint = new JLabel(" ");
    final JTextField notes = new JTextField(24);
    final JButton doneButton = new JButton("All spots done");
    final MouseAdapter pointClick = new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            Item it = selected();
            if (it != null && it.type == Item.Type.POINT && imp != null && imp.getRoi() instanceof PointRoi
                    && Toolbar.getToolId() == Toolbar.POINT)
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        add();
                    }
                });
        }
    };
    final KeyEventDispatcher keys = new KeyEventDispatcher() {
        public boolean dispatchKeyEvent(KeyEvent e) {
            return key(e);
        }
    };

    Panel() throws IOException {
        super("MorphoTracer " + MorphoTracer_.VERSION);
        MorphoTracer_.writeExamples();
        build();
        loadSetupNames(Prefs.get("morphotracer.setup", MorphoTracer_.EXAMPLES[0]));
        reopenLastFolder();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        pack();
        setLocation(20, 80);
    }

    private void build() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(row(new JLabel("Setup:"), setups, button("Edit...", new Runnable() {
            public void run() {
                editSetup(false);
            }
        }), button("New...", new Runnable() {
            public void run() {
                editSetup(true);
            }
        })));
        top.add(row(button("Photo folder...", new Runnable() {
            public void run() {
                choosePhotos();
            }
        }), folderLabel));
        top.add(row(resultsLabel));
        top.add(row(button("< Prev", new Runnable() {
            public void run() {
                go(index - 1);
            }
        }), photoBox, button("Next >", new Runnable() {
            public void run() {
                go(index + 1);
            }
        })));
        top.add(row(new JLabel("Fish id:"), fishId));
        top.add(row(new JLabel("Scale:"), scaleLabel));
        top.add(row(button("Draw scale [S]", new Runnable() {
            public void run() {
                drawScale(false);
            }
        }), button("...for next photos too [Shift+S]", new Runnable() {
            public void run() {
                drawScale(true);
            }
        }), button("No scale possible", new Runnable() {
            public void run() {
                noScale();
            }
        })));

        itemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        itemList.setVisibleRowCount(16);
        itemList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                if (!e.getValueIsAdjusting() && !updating)
                    selectTool();
            }
        });
        JPanel middle = new JPanel(new BorderLayout());
        middle.setBorder(BorderFactory.createTitledBorder("Items: [x] done, [!] required, [ ] optional (click one, or press 1-9)"));
        middle.add(new JScrollPane(itemList), BorderLayout.CENTER);
        hint.setForeground(new Color(0, 90, 160));
        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.add(row(hint));
        actions.add(row(new JLabel("Tool:"), tool("Line", "line"), tool("Segmented line", "polyline"),
                tool("Polygon", "polygon"), tool("Freehand", "freehand"), tool("Point", "point"),
                tool("Multi-point", "multipoint")));
        actions.add(row(button("Add selection [A]", new Runnable() {
            public void run() {
                add();
            }
        }), button("Remove last", new Runnable() {
            public void run() {
                removeLast();
            }
        }), doneButton, button("Skip point [N]", new Runnable() {
            public void run() {
                skip();
            }
        })));
        doneButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                toggleDone();
            }
        });
        middle.add(actions, BorderLayout.SOUTH);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(row(new JLabel("Notes:"), notes));
        JButton save = button("Save fish", new Runnable() {
            public void run() {
                save();
            }
        });
        save.setFont(save.getFont().deriveFont(Font.BOLD));
        bottom.add(row(save, button("Next photo without saving", new Runnable() {
            public void run() {
                go(index + 1);
            }
        })));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(middle, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);

        setups.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!updating && setups.getSelectedItem() != null)
                    useSetup((String) setups.getSelectedItem());
            }
        });
        photoBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!updating && photoBox.getSelectedIndex() >= 0 && photoBox.getSelectedIndex() != index)
                    go(photoBox.getSelectedIndex());
            }
        });
        photoBox.setPrototypeDisplayValue("[x] some_folder/IMG_0000_long_name.JPG");
        fishId.getDocument().addDocumentListener(new Changed());
        notes.getDocument().addDocumentListener(new Changed());
    }

    class Changed implements javax.swing.event.DocumentListener {
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            if (!updating)
                dirty = true;
        }

        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            insertUpdate(e);
        }

        public void changedUpdate(javax.swing.event.DocumentEvent e) {
        }
    }

    static JPanel row(java.awt.Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        for (java.awt.Component c : cs)
            p.add(c);
        return p;
    }

    static JButton tool(String text, final String name) {
        JButton b = button(text, new Runnable() {
            public void run() {
                IJ.setTool(name);
            }
        });
        b.setMargin(new java.awt.Insets(1, 4, 1, 4));
        return b;
    }

    static JButton button(String text, final Runnable r) {
        JButton b = new JButton(text);
        b.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        });
        return b;
    }

    // ---- setups and folders

    void loadSetupNames(String select) {
        updating = true;
        setups.removeAllItems();
        File[] fs = MorphoTracer_.setupsDir().listFiles();
        List<String> names = new ArrayList<String>();
        if (fs != null)
            for (File f : fs)
                if (f.getName().endsWith(".json"))
                    names.add(f.getName().substring(0, f.getName().length() - 5));
        java.util.Collections.sort(names);
        for (String n : names)
            setups.addItem(n);
        updating = false;
        if (names.contains(select))
            setups.setSelectedItem(select);
        if (setups.getSelectedItem() != null)
            useSetup((String) setups.getSelectedItem());
    }

    void useSetup(String name) {
        if (setup != null && name.equals(setup.name) && store != null)
            return;
        if (!askSave())
            return;
        try {
            setup = Setup.load(new File(MorphoTracer_.setupsDir(), name + ".json"));
            Prefs.set("morphotracer.setup", name);
            openStore();
        } catch (Exception e) {
            error("Could not read the setup " + name + ": " + e.getMessage());
        }
    }

    void editSetup(boolean fresh) {
        if (!askSave())
            return;
        Setup s = fresh || setup == null ? new Setup() : setup.copy();
        Setup out = SetupDialog.edit(this, s, fresh);
        if (out == null)
            return;
        setup = null;
        store = null;
        loadSetupNames(out.name);
    }

    void choosePhotos() {
        if (!askSave())
            return;
        DirectoryChooser dc = new DirectoryChooser("Folder with the photos (subfolders included)");
        if (dc.getDirectory() == null)
            return;
        photosDir = new File(dc.getDirectory());
        Prefs.set("morphotracer.photos", photosDir.getPath());
        photos = listImages(photosDir);
        openStore();
    }

    /** Start where the last session stopped: the last photo folder, at its first photo not done. */
    void reopenLastFolder() {
        String last = Prefs.get("morphotracer.photos", "");
        if (last.isEmpty() || !new File(last).isDirectory())
            return;
        photosDir = new File(last);
        photos = listImages(photosDir);
        openStore();
    }

    static List<File> listImages(File dir) {
        List<File> out = new ArrayList<File>();
        collect(dir, out, 0);
        java.util.Collections.sort(out);
        return out;
    }

    private static void collect(File dir, List<File> out, int depth) {
        File[] fs = dir.listFiles();
        if (fs == null || depth > 6)
            return;
        for (File f : fs) {
            if (f.isDirectory() && !f.getName().startsWith(".") && !f.getName().startsWith("results_")) {
                collect(f, out, depth + 1);
            } else {
                String n = f.getName().toLowerCase(Locale.ROOT);
                for (String e : IMAGES)
                    if (n.endsWith(e) && !n.startsWith("."))
                        out.add(f);
            }
        }
    }

    /** Results of this setup for this photo folder: <folder>/results_<setup>/ (photos are never written). */
    void openStore() {
        if (setup == null || photosDir == null) {
            refreshAll();
            return;
        }
        try {
            store = new Store(new File(photosDir, "results_" + setup.name), setup);
        } catch (IOException e) {
            error("Could not read the results: " + e.getMessage());
            return;
        }
        folderLabel.setText(photosDir.getPath());
        resultsLabel.setText("Results: " + store.dir.getPath());
        int first = 0;
        for (int k = 0; k < photos.size(); k++)
            if (!"TRUE".equals(rowOf(k) == null ? null : rowOf(k).get("complete"))) {
                first = k;
                break;
            }
        index = -1;
        if (photos.isEmpty())
            error("No photos (jpg, png, tif, bmp) in " + photosDir.getPath());
        else
            go(first);
    }

    String folderOf(File f) {
        String rel = photosDir.toURI().relativize(f.getParentFile().toURI()).getPath();
        if (rel.endsWith("/"))
            rel = rel.substring(0, rel.length() - 1);
        return rel.isEmpty() ? "." : rel;
    }

    String keyOf(int k) {
        File f = photos.get(k);
        return Store.key(folderOf(f), f.getName());
    }

    Map<String, String> rowOf(int k) {
        return store == null ? null : store.row(keyOf(k));
    }

    // ---- photos

    void go(int k) {
        if (store == null || photos.isEmpty() || k < 0 || k >= photos.size()) {
            refreshPhotoBox();
            return;
        }
        if (k != index && !askSave()) {
            refreshPhotoBox();
            return;
        }
        File f = photos.get(k);
        ImagePlus next = IJ.openImage(f.getPath());
        if (next == null) {
            error("Could not open " + f.getName());
            return;
        }
        if (imp != null) {
            imp.changes = false;
            imp.close();
        }
        index = k;
        imp = next;
        origW = imp.getWidth();
        origH = imp.getHeight();
        imp.show();
        ImageWindow win = imp.getWindow();
        if (win != null) {
            ImageCanvas c = win.getCanvas();
            c.addMouseListener(pointClick);
        }
        String key = keyOf(k);
        try {
            rois = Store.readRois(store.roiFile(key));
        } catch (IOException e) {
            rois = new ArrayList<Roi>();
            error("Could not read the saved marks of this photo: " + e.getMessage());
        }
        Map<String, String> row = store.row(key);
        done = new ArrayList<String>();
        for (Item it : setup.items(Item.Type.COLOUR))
            if (row != null && ("TRUE".equals(row.get(it.name + "_complete"))
                    || row.get(it.name + "_complete") == null && row.get(it.name + "_n") != null
                    && !Csv.NA.equals(row.get(it.name + "_n"))))
                done.add(it.name);
        scale = Scale.forPhoto(setup, store, key, folderOf(f), rois);
        updating = true;
        String id = row == null || Csv.NA.equals(row.get("fish_id")) ? setup.idFromFile(f.getName()) : row.get("fish_id");
        fishId.setText(id == null ? "" : id.replaceFirst("^'", ""));
        String n = row == null || row.get("notes") == null || Csv.NA.equals(row.get("notes")) ? "" : row.get("notes");
        notes.setText(n.replaceFirst("^'", ""));
        updating = false;
        dirty = false;
        refreshAll();
        selectFirstMissing();
        if (!scale.set())
            hint("Scale first: draw a line over a known length (line tool), then press S.");
        IJ.setTool(scale.set() ? Toolbar.POINT : Toolbar.LINE);
    }

    boolean askSave() {
        if (!dirty || imp == null)
            return true;
        int a = JOptionPane.showConfirmDialog(this, "Save this fish before leaving it?", "MorphoTracer",
                JOptionPane.YES_NO_CANCEL_OPTION);
        if (a == JOptionPane.CANCEL_OPTION || a == JOptionPane.CLOSED_OPTION)
            return false;
        if (a == JOptionPane.YES_OPTION)
            return save(false);
        dirty = false;
        return true;
    }

    // ---- items

    Item selected() {
        int i = itemList.getSelectedIndex();
        return setup == null || i < 0 || i >= setup.items.size() ? null : setup.items.get(i);
    }

    boolean has(Item it, Measures m) {
        switch (it.type) {
            case LENGTH_FROM_POINTS: return m.xy.containsKey(it.from) && m.xy.containsKey(it.to);
            case COLOUR: return done.contains(it.name);
            default: return !Measures.of(rois, it.name).isEmpty();
        }
    }

    void selectFirstMissing() {
        if (setup == null)
            return;
        Measures m = measures();
        for (int k = 0; k < setup.items.size(); k++) {
            Item it = setup.items.get(k);
            if (it.drawn() && !has(it, m)) {
                itemList.setSelectedIndex(k);
                return;
            }
        }
        itemList.setSelectedIndex(0);
    }

    /** Next item to do after the selected one (points go on by themselves). */
    void selectNext() {
        int i = itemList.getSelectedIndex();
        Measures m = measures();
        for (int k = i + 1; k < setup.items.size(); k++) {
            Item it = setup.items.get(k);
            if (it.drawn() && !has(it, m)) {
                itemList.setSelectedIndex(k);
                return;
            }
        }
        hint("Everything after this item is done: check the list, then Save fish.");
        drawOverlay();
    }

    void selectTool() {
        Item it = selected();
        if (it == null)
            return;
        doneButton.setEnabled(it.type == Item.Type.COLOUR);
        doneButton.setText(done.contains(it.name) ? "Not all spots done" : "All spots done");
        String what = it.label.isEmpty() ? it.name : it.name + " (" + it.label + ")";
        switch (it.type) {
            case LENGTH:
                IJ.setTool(Toolbar.LINE);
                hint(what + ": draw a line (straight, or segmented to follow the fish), then press A.");
                break;
            case POINT:
                IJ.setTool(Toolbar.POINT);
                hint(what + ": click on the photo." + (it.required ? "" : " Not there? Press N."));
                break;
            case PART:
                IJ.setTool(Toolbar.POLYGON);
                hint(what + ": draw around it (polygon or freehand), then press A.");
                break;
            case COLOUR:
                IJ.setTool(Toolbar.POLYGON);
                hint(what + ": draw around one spot, press A; repeat for every spot, then 'All spots done'.");
                break;
            case COUNT:
                IJ.setTool("multipoint");
                hint(what + ": click each one (multi-point tool), then press A.");
                break;
            default:
                hint(what + ": computed from " + it.from + " and " + it.to + ", nothing to draw.");
        }
        drawOverlay();
    }

    void add() {
        Item it = selected();
        if (imp == null || it == null)
            return;
        Roi r = imp.getRoi();
        if (r == null) {
            hint("Draw on the photo first.");
            return;
        }
        String wrong = null;
        switch (it.type) {
            case POINT: if (!(r instanceof PointRoi)) wrong = "a point"; break;
            case COUNT: if (!(r instanceof PointRoi)) wrong = "points (multi-point tool)"; break;
            case LENGTH: if (!r.isLine()) wrong = "a line"; break;
            case PART: case COLOUR: if (!r.isArea()) wrong = "an area (polygon or freehand)"; break;
            default: wrong = "nothing (it is computed)";
        }
        if (wrong != null) {
            hint(it.name + " needs " + wrong + ".");
            return;
        }
        Roi c;
        if (it.type == Item.Type.POINT) {
            ij.process.FloatPolygon p = r.getFloatPolygon();
            c = new PointRoi(p.xpoints[p.npoints - 1], p.ypoints[p.npoints - 1]);
        } else {
            c = (Roi) r.clone();
        }
        c.setProperty(Measures.ITEM, it.name);
        List<Roi> same = Measures.of(rois, it.name);
        if (it.many()) {
            int max = 0;
            for (Roi x : same) {
                String n = x.getName() == null ? "" : x.getName();
                java.util.regex.Matcher mm = java.util.regex.Pattern.compile("_(\\d+)$").matcher(n);
                max = Math.max(max, mm.find() ? Integer.parseInt(mm.group(1)) : 1);
            }
            c.setName(it.type == Item.Type.COLOUR ? it.name + "_" + (max + 1)
                    : same.isEmpty() ? it.name : it.name + "_" + (max + 1));
        } else {
            rois.removeAll(same);
            c.setName(it.name);
        }
        rois.add(c);
        imp.deleteRoi();
        dirty = true;
        refreshItems();
        // one mark per item: go on to the next one; colour classes stay for the next spot
        if (it.type != Item.Type.COLOUR)
            selectNext();
        else
            drawOverlay();
    }

    void skip() {
        Item it = selected();
        if (it == null)
            return;
        if (it.required)
            hint(it.name + " is required; it can be left out only with Save fish > Save anyway.");
        int i = itemList.getSelectedIndex();
        if (i + 1 < setup.items.size())
            itemList.setSelectedIndex(i + 1);
    }

    void removeLast() {
        Item it = selected();
        if (it == null)
            return;
        List<Roi> same = Measures.of(rois, it.name);
        if (same.isEmpty()) {
            hint("Nothing marked for " + it.name + ".");
            return;
        }
        rois.remove(same.get(same.size() - 1));
        dirty = true;
        refreshItems();
        drawOverlay();
    }

    void toggleDone() {
        Item it = selected();
        if (it == null || it.type != Item.Type.COLOUR)
            return;
        if (!done.remove(it.name))
            done.add(it.name);
        dirty = true;
        refreshItems();
        selectTool();
    }

    // ---- scale

    void drawScale(boolean forNext) {
        if (imp == null)
            return;
        Roi r = imp.getRoi();
        if (!(r instanceof Line) || r.getType() != Roi.LINE) {
            IJ.setTool(Toolbar.LINE);
            hint("Draw a straight line over a known length, then press S (or Shift+S).");
            return;
        }
        double last = Prefs.get("morphotracer.scale_mm." + setup.name, setup.scaleMm);
        String in = (String) JOptionPane.showInputDialog(imp.getWindow(), "Length of this line in mm:", "Scale",
                JOptionPane.QUESTION_MESSAGE, null, null, Csv.num(last));
        if (in == null)
            return;
        double mm;
        try {
            mm = Double.parseDouble(in.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            error("Not a number: " + in);
            return;
        }
        if (mm <= 0) {
            error("The length must be more than 0 mm.");
            return;
        }
        Prefs.set("morphotracer.scale_mm." + setup.name, mm);
        boolean shared = setup.scaleMode != Setup.ScaleMode.EVERY_PHOTO && (forNext || !scale.set()
                || scale.source.equals(Scale.SHARED));
        Line l = (Line) r.clone();
        l.setName(Measures.SCALE);
        l.setProperty(Measures.ITEM, Measures.SCALE);
        l.setProperty("mm", Csv.num(mm));
        removeScale();
        rois.add(l);
        scale = Scale.drawn(l, mm, shared, photos.get(index).getName());
        imp.deleteRoi();
        dirty = true;
        refreshAll();
        selectFirstMissing();
    }

    void noScale() {
        if (imp == null)
            return;
        removeScale();
        scale = new Scale(null, Scale.NONE, "", 0);
        dirty = true;
        refreshAll();
    }

    private void removeScale() {
        for (int k = rois.size() - 1; k >= 0; k--)
            if (Measures.SCALE.equals(Measures.itemOf(rois.get(k))))
                rois.remove(k);
    }

    // ---- saving

    Measures measures() {
        return Measures.compute(setup, rois, scale.ppm, origW, origH, done);
    }

    void save() {
        save(true);
    }

    boolean save(boolean thenNext) {
        if (imp == null || store == null)
            return false;
        Measures m = measures();
        List<String> problems = Measures.missing(setup, rois, done, m);
        if (!scale.set())
            problems.add(0, "no scale (press S, or No scale possible)");
        boolean resized = imp.getWidth() != origW || imp.getHeight() != origH;
        if (resized)
            problems.add(0, "the photo was cropped, rotated or resized in ImageJ: the marks no longer match the original");
        if (resized && setup.training()) {
            error("This photo was cropped, rotated or resized: open it again (Prev / Next) and mark the original.");
            return false;
        }
        if (!problems.isEmpty()) {
            Object[] options = {"Go back", "Save anyway (marked incomplete)"};
            int a = JOptionPane.showOptionDialog(this, "Not complete:\n- " + join(problems, "\n- "), "Save fish",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            if (a != 1)
                return false;
        }
        File f = photos.get(index);
        Map<String, String> row = row(setup, f.getName(), folderOf(f), fishId.getText().trim(), origW, origH, scale, m,
                problems.isEmpty(), join(problems, "; "), notes.getText().trim());
        try {
            store.save(keyOf(index), row, m, rois, origW, origH);
        } catch (IOException e) {
            error("Could not save: " + e.getMessage());
            return false;
        }
        dirty = false;
        refreshPhotoBox();
        IJ.showStatus("MorphoTracer: saved " + f.getName());
        if (thenNext && index + 1 < photos.size())
            go(index + 1);
        else if (thenNext)
            hint("Saved. That was the last photo of the folder.");
        return true;
    }

    /** One row of morphology.csv. */
    static Map<String, String> row(Setup s, String photo, String folder, String fishId, int w, int h, Scale scale,
                                   Measures m, boolean complete, String flags, String notes) {
        Map<String, String> r = new LinkedHashMap<String, String>();
        r.put("photo", Csv.text(photo));
        r.put("folder", Csv.text(folder));
        r.put("fish_id", Csv.text(fishId));
        r.put("setup", s.name);
        r.put("purpose", s.purpose.name().toLowerCase(Locale.ROOT));
        r.put("annotator", Csv.text(s.annotator));
        r.put("date", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(new Date()));
        r.put("image_width", String.valueOf(w));
        r.put("image_height", String.valueOf(h));
        r.put("px_per_mm", Csv.num(scale.ppm));
        r.put("scale_source", scale.set() ? scale.source : Csv.NA);
        r.put("scale_photo", Csv.text(scale.photo));
        r.putAll(m.columns);
        r.put("complete", Csv.bool(complete));
        r.put("flags", Csv.text(flags));
        r.put("notes", Csv.text(notes));
        return r;
    }

    static String join(List<String> l, String sep) {
        StringBuilder b = new StringBuilder();
        for (String s : l)
            b.append(b.length() == 0 ? "" : sep).append(s);
        return b.toString();
    }

    // ---- display

    void refreshAll() {
        refreshPhotoBox();
        refreshItems();
        scaleLabel.setText(scale.describe());
        scaleLabel.setForeground(scale.set() ? Color.BLACK : new Color(180, 0, 0));
        drawOverlay();
        pack();
    }

    void refreshPhotoBox() {
        updating = true;
        photoBox.removeAllItems();
        int nDone = 0;
        for (int k = 0; k < photos.size(); k++) {
            Map<String, String> r = rowOf(k);
            String mark = r == null ? "[ ] " : "TRUE".equals(r.get("complete")) ? "[x] " : "[~] ";
            if (r != null && "TRUE".equals(r.get("complete")))
                nDone++;
            photoBox.addItem(mark + keyOf(k));
        }
        if (index >= 0 && index < photos.size())
            photoBox.setSelectedIndex(index);
        if (photosDir != null)
            folderLabel.setText(photosDir.getName() + ": " + nDone + " / " + photos.size() + " done");
        updating = false;
    }

    void refreshItems() {
        if (setup == null)
            return;
        updating = true;
        int sel = itemList.getSelectedIndex();
        if (itemModel.size() != setup.items.size())
            itemModel.clear();
        Measures m = measures();
        for (int k = 0; k < setup.items.size(); k++) {
            Item it = setup.items.get(k);
            String mark = has(it, m) ? "[x]" : it.required ? "[!]" : "[ ]";
            String shown = m.shown.get(it.name);
            String text = String.format("%s %2d %-20s %s", mark, k + 1, it.name, shown == null ? "" : shown);
            if (k < itemModel.size())
                itemModel.set(k, text);
            else
                itemModel.addElement(text);
        }
        if (sel >= 0 && sel < itemModel.size())
            itemList.setSelectedIndex(sel);
        updating = false;
        setTitle("MorphoTracer " + MorphoTracer_.VERSION + (setup == null ? "" : " - " + setup.name + " (" + setup.purpose + ")"));
    }

    void drawOverlay() {
        if (imp == null || setup == null)
            return;
        Overlay ov = new Overlay();
        Item sel = selected();
        for (Roi r : rois) {
            Roi c = (Roi) r.clone();
            String item = Measures.itemOf(r);
            Item it = setup.item(item);
            Color col = item.equals(Measures.SCALE) ? Color.CYAN : it == null ? Color.WHITE : parse(it.colour);
            c.setStrokeColor(col);
            c.setStrokeWidth(sel != null && sel.name.equals(item) ? 2 : 1);
            ov.add(c);
        }
        // marks already made cannot be picked up and moved by mistake
        ov.selectable(false);
        ov.drawNames(true);
        ov.drawLabels(true);
        ov.setLabelColor(Color.WHITE);
        imp.setOverlay(ov);
    }

    static Color parse(String hex) {
        try {
            return Color.decode(hex);
        } catch (RuntimeException e) {
            return Color.YELLOW;
        }
    }

    void hint(String s) {
        hint.setText(s);
    }

    void error(String s) {
        JOptionPane.showMessageDialog(this, s, "MorphoTracer", JOptionPane.WARNING_MESSAGE);
    }

    /** Keys typed on the photo or the panel: handled here and not passed to ImageJ. */
    boolean key(KeyEvent e) {
        if (imp == null || imp.getWindow() == null || e.isControlDown() || e.isAltDown() || e.isMetaDown())
            return false;
        KeyboardFocusManager fm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Window w = fm.getFocusedWindow();
        boolean photo = w instanceof ImageWindow && ((ImageWindow) w).getImagePlus() == imp;
        // in the panel too, except while typing in a text field
        boolean panel = w == this && !(fm.getFocusOwner() instanceof javax.swing.text.JTextComponent);
        if (!photo && !panel)
            return false;
        int code = e.getKeyCode();
        char ch = Character.toLowerCase(e.getKeyChar());
        boolean ours = code == KeyEvent.VK_A || code == KeyEvent.VK_N || code == KeyEvent.VK_S
                || code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9
                || ch == 'a' || ch == 'n' || ch == 's' || ch >= '1' && ch <= '9';
        if (!ours)
            return false;
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            if (code == KeyEvent.VK_A)
                add();
            else if (code == KeyEvent.VK_N)
                skip();
            else if (code == KeyEvent.VK_S)
                drawScale(e.isShiftDown());
            else if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9 && code - KeyEvent.VK_1 < itemModel.size())
                itemList.setSelectedIndex(code - KeyEvent.VK_1);
        }
        return true;
    }

    void close() {
        if (!askSave())
            return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
        instance = null;
        dispose();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(520, 500);
    }
}
