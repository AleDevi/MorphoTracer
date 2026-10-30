package morphotracer;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;

/** Edit a setup: purpose, scale mode, fish id, body axis, and the table of items. Saved as <name>.json. */
public class SetupDialog extends JDialog {

    static final String[] COLS = {"Name", "Type", "Required", "From (point)", "To (point)", "Continues (length)", "Colour", "Description"};

    final JTextField name = new JTextField(20);
    final JComboBox<Setup.Purpose> purpose = new JComboBox<Setup.Purpose>(Setup.Purpose.values());
    final JTextField annotator = new JTextField(20);
    final JComboBox<Setup.ScaleMode> scaleMode = new JComboBox<Setup.ScaleMode>(Setup.ScaleMode.values());
    final JTextField scaleMm = new JTextField(6);
    final JTextField idPattern = new JTextField(20);
    final JTextField idTest = new JTextField(16);
    final JLabel idResult = new JLabel(" ");
    final JTextField axisFrom = new JTextField(12);
    final JTextField axisTo = new JTextField(12);
    final JTextField bodyPart = new JTextField(12);
    final JTextField percentOf = new JTextField(16);
    final DefaultTableModel model = new DefaultTableModel(COLS, 0) {
        @Override
        public Class<?> getColumnClass(int c) {
            return c == 2 ? Boolean.class : Object.class;
        }
    };
    final JTable table = new JTable(model);
    Setup result;

    SetupDialog(JFrame owner, Setup s, boolean fresh) {
        super(owner, fresh ? "New setup" : "Edit setup " + s.name, true);
        name.setText(s.name);
        purpose.setSelectedItem(s.purpose);
        annotator.setText(s.annotator);
        scaleMode.setSelectedItem(s.scaleMode);
        scaleMm.setText(Csv.num(s.scaleMm));
        idPattern.setText(s.idPattern);
        axisFrom.setText(s.axisFrom);
        axisTo.setText(s.axisTo);
        bodyPart.setText(s.bodyPart);
        percentOf.setText(Panel.join(s.percentOf, ", "));
        for (Item it : s.items)
            addRow(it);
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<Item.Type>(Item.Type.values())));
        table.setRowHeight(22);
        table.getColumnModel().getColumn(7).setPreferredWidth(260);

        JPanel form = new JPanel(new GridBagLayout());
        int y = 0;
        y = field(form, y, "Setup name", name, "letters, digits, _ and - (e.g. guppy_morpho_training)");
        y = field(form, y, "Purpose", purpose, "Measures: sizes for analysis. Training: annotations for Guppy Colour (stricter checks, masks)");
        y = field(form, y, "Annotator", annotator, "your name, written in every row");
        y = field(form, y, "Scale", scaleMode, "Every photo / once per folder / carry forward the last one");
        y = field(form, y, "Known length (mm)", scaleMm, "proposed when you draw a scale line");
        y = field(form, y, "Fish id from file name", idPattern, "optional pattern, e.g. M\\d+ finds M103 in IMG_9860_M103.JPG; a part in ( ) is kept");
        y = field(form, y, "Try it on a file name", idTest, "");
        y = field(form, y, "", idResult, "");
        y = field(form, y, "Body axis from point", axisFrom, "for spot positions, e.g. snout");
        y = field(form, y, "Body axis to point", axisTo, "e.g. peduncle_end");
        y = field(form, y, "Body part for depth", bodyPart, "e.g. body");
        field(form, y, "Colour % of parts", percentOf, "parts that make 100% for <class>_percent, e.g. body, caudal (empty: no %)");
        javax.swing.event.DocumentListener test = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                tryId();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                tryId();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
            }
        };
        idPattern.getDocument().addDocumentListener(test);
        idTest.getDocument().addDocumentListener(test);

        JPanel items = new JPanel(new BorderLayout());
        items.add(new JLabel("<html>&nbsp;<b>Items</b>, in the order they are marked. " + typesHelp() + "</html>"), BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(820, 300));
        items.add(sp, BorderLayout.CENTER);
        items.add(Panel.row(Panel.button("Add item", new Runnable() {
            public void run() {
                addRow(new Item("new_item", Item.Type.PART, false, ""));
            }
        }), Panel.button("Remove", new Runnable() {
            public void run() {
                stopEditing();
                int r = table.getSelectedRow();
                if (r >= 0)
                    model.removeRow(r);
            }
        }), Panel.button("Up", new Runnable() {
            public void run() {
                move(-1);
            }
        }), Panel.button("Down", new Runnable() {
            public void run() {
                move(1);
            }
        }), Panel.button("Add names from a Guppy Colour profile...", new Runnable() {
            public void run() {
                importProfile();
            }
        })), BorderLayout.SOUTH);

        JButton ok = Panel.button("Save setup", new Runnable() {
            public void run() {
                ok();
            }
        });
        JButton cancel = Panel.button("Cancel", new Runnable() {
            public void run() {
                dispose();
            }
        });
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(form, BorderLayout.NORTH);
        getContentPane().add(items, BorderLayout.CENTER);
        getContentPane().add(Panel.row(ok, cancel), BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    static Setup edit(JFrame owner, Setup s, boolean fresh) {
        SetupDialog d = new SetupDialog(owner, s, fresh);
        d.setVisible(true);
        return d.result;
    }

    static String typesHelp() {
        StringBuilder b = new StringBuilder();
        for (Item.Type t : Item.Type.values())
            b.append("<br>&nbsp;").append(t.label).append(": ").append(t.hint);
        return b.toString();
    }

    private static int field(JPanel p, int y, String label, java.awt.Component c, String help) {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 6, 2, 6);
        g.anchor = GridBagConstraints.WEST;
        g.gridy = y;
        g.gridx = 0;
        p.add(new JLabel(label), g);
        g.gridx = 1;
        p.add(c, g);
        g.gridx = 2;
        JLabel h = new JLabel(help);
        h.setForeground(java.awt.Color.GRAY);
        p.add(h, g);
        return y + 1;
    }

    void tryId() {
        Setup s = new Setup();
        s.idPattern = idPattern.getText();
        String id = s.idFromFile(idTest.getText().trim());
        idResult.setText(idTest.getText().trim().isEmpty() ? " " : id.isEmpty() ? "no id found" : "fish id: " + id);
    }

    void addRow(Item it) {
        model.addRow(new Object[] {it.name, it.type, it.required, it.from, it.to, it.continues, it.colour, it.label});
    }

    void stopEditing() {
        if (table.isEditing())
            table.getCellEditor().stopCellEditing();
    }

    void move(int d) {
        stopEditing();
        int r = table.getSelectedRow();
        if (r < 0 || r + d < 0 || r + d >= model.getRowCount())
            return;
        model.moveRow(r, r, r + d);
        table.setRowSelectionInterval(r + d, r + d);
    }

    void importProfile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Guppy Colour profile (guppycolor/profiles/*.json)");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        try {
            List<Item> items = Setup.fromProfile(fc.getSelectedFile());
            Setup now = build();
            int added = 0;
            for (Item it : items)
                if (now.item(it.name) == null) {
                    addRow(it);
                    added++;
                }
            JOptionPane.showMessageDialog(this, added + " items added. Set their order and which are required, then Save setup.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not read the profile: " + e.getMessage());
        }
    }

    Setup build() {
        stopEditing();
        Setup s = new Setup();
        s.name = name.getText().trim();
        s.purpose = (Setup.Purpose) purpose.getSelectedItem();
        s.annotator = annotator.getText().trim();
        s.scaleMode = (Setup.ScaleMode) scaleMode.getSelectedItem();
        try {
            s.scaleMm = Double.parseDouble(scaleMm.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            s.scaleMm = -1;
        }
        s.idPattern = idPattern.getText().trim();
        s.axisFrom = axisFrom.getText().trim();
        s.axisTo = axisTo.getText().trim();
        s.bodyPart = bodyPart.getText().trim();
        s.percentOf = Setup.names(percentOf.getText());
        for (int r = 0; r < model.getRowCount(); r++) {
            Object t = model.getValueAt(r, 1);
            Item.Type type = t instanceof Item.Type ? (Item.Type) t : Item.Type.valueOf(t.toString());
            Item it = new Item(str(r, 0), type, Boolean.TRUE.equals(model.getValueAt(r, 2)), str(r, 6));
            it.from = str(r, 3);
            it.to = str(r, 4);
            it.continues = str(r, 5);
            it.label = str(r, 7);
            s.items.add(it);
        }
        return s;
    }

    private String str(int r, int c) {
        Object v = model.getValueAt(r, c);
        return v == null ? "" : v.toString().trim();
    }

    void ok() {
        Setup s = build();
        List<String> p = s.problems();
        if (s.items.isEmpty())
            p.add("Add at least one item.");
        if (!p.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fix:\n- " + Panel.join(p, "\n- "), "Setup", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File f = new File(MorphoTracer_.setupsDir(), s.name + ".json");
        try {
            s.save(f);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not save: " + e.getMessage());
            return;
        }
        result = s;
        dispose();
    }
}
