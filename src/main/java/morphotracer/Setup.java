package morphotracer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A named way of measuring: purpose, scale mode, fish id, and the list of items. Saved as JSON and reused. */
public class Setup {

    public enum Purpose {
        MEASURES("Measures"), TRAINING("Training annotations"), BOTH("Both");

        public final String label;

        Purpose(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum ScaleMode {
        EVERY_PHOTO("Every photo"), PER_FOLDER("Once per folder"), CARRY_FORWARD("Carry forward");

        public final String label;

        ScaleMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");

    public String name = "new_setup";
    public Purpose purpose = Purpose.MEASURES;
    public String annotator = "";
    public ScaleMode scaleMode = ScaleMode.PER_FOLDER;
    public double scaleMm = 10;
    /** Regular expression applied to the file name; its first group (or the whole match) is the fish id. */
    public String idPattern = "";
    /** Body axis for spot positions: two POINT items (e.g. snout, peduncle_end), and the PART used for body depth. */
    public String axisFrom = "";
    public String axisTo = "";
    public String bodyPart = "";
    public List<Item> items = new ArrayList<Item>();

    public boolean training() {
        return purpose != Purpose.MEASURES;
    }

    public Item item(String n) {
        for (Item it : items)
            if (it.name.equals(n))
                return it;
        return null;
    }

    public List<Item> items(Item.Type t) {
        List<Item> out = new ArrayList<Item>();
        for (Item it : items)
            if (it.type == t)
                out.add(it);
        return out;
    }

    public boolean hasMorphometrics() {
        return !items(Item.Type.POINT).isEmpty() || !items(Item.Type.COLOUR).isEmpty();
    }

    /** Fish id read from the file name with idPattern; "" when there is no pattern or no match. */
    public String idFromFile(String fileName) {
        if (idPattern.trim().isEmpty())
            return "";
        String stem = fileName.replaceFirst("\\.[^.]*$", "");
        try {
            Matcher m = Pattern.compile(idPattern).matcher(stem);
            if (!m.find())
                return "";
            return m.groupCount() > 0 && m.group(1) != null ? m.group(1) : m.group();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Problems that make the setup unusable, in plain words; empty list when it is fine. */
    public List<String> problems() {
        List<String> p = new ArrayList<String>();
        if (!NAME.matcher(name).matches())
            p.add("The setup name may only use letters, digits, '_' and '-'.");
        List<String> seen = new ArrayList<String>();
        for (Item it : items) {
            if (!NAME.matcher(it.name).matches())
                p.add("Item name '" + it.name + "': use only letters, digits, '_' and '-'.");
            else if (seen.contains(it.name.toLowerCase()))
                p.add("Two items are called '" + it.name + "'.");
            else if (it.name.equalsIgnoreCase("scale"))
                p.add("'scale' is reserved for the scale line: choose another item name.");
            seen.add(it.name.toLowerCase());
            if (it.type == Item.Type.LENGTH_FROM_POINTS && (!isPoint(it.from) || !isPoint(it.to)))
                p.add("Length '" + it.name + "' needs two Point items in 'From' and 'To'.");
        }
        if (!axisFrom.isEmpty() || !axisTo.isEmpty())
            if (!isPoint(axisFrom) || !isPoint(axisTo))
                p.add("The body axis needs two Point items.");
        if (!bodyPart.isEmpty() && (item(bodyPart) == null || item(bodyPart).type != Item.Type.PART))
            p.add("The body part for depth must be a Part item.");
        if (!idPattern.trim().isEmpty()) {
            try {
                Pattern.compile(idPattern);
            } catch (RuntimeException e) {
                p.add("The fish id pattern is not valid.");
            }
        }
        if (scaleMm <= 0)
            p.add("The known scale length must be more than 0 mm.");
        return p;
    }

    private boolean isPoint(String n) {
        Item it = item(n);
        return it != null && it.type == Item.Type.POINT;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("morphotracer_setup", 1);
        m.put("name", name);
        m.put("purpose", purpose.name());
        m.put("annotator", annotator);
        m.put("scale_mode", scaleMode.name());
        m.put("scale_mm", scaleMm);
        m.put("id_pattern", idPattern);
        m.put("axis_from", axisFrom);
        m.put("axis_to", axisTo);
        m.put("body_part", bodyPart);
        List<Object> a = new ArrayList<Object>();
        for (Item it : items)
            a.add(it.toMap());
        m.put("items", a);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Setup fromMap(Map<String, Object> m) {
        Setup s = new Setup();
        s.name = Json.str(m, "name", s.name);
        s.purpose = Purpose.valueOf(Json.str(m, "purpose", s.purpose.name()));
        s.annotator = Json.str(m, "annotator", "");
        s.scaleMode = ScaleMode.valueOf(Json.str(m, "scale_mode", s.scaleMode.name()));
        s.scaleMm = Json.num(m, "scale_mm", s.scaleMm);
        s.idPattern = Json.str(m, "id_pattern", "");
        s.axisFrom = Json.str(m, "axis_from", "");
        s.axisTo = Json.str(m, "axis_to", "");
        s.bodyPart = Json.str(m, "body_part", "");
        Object items = m.get("items");
        if (items instanceof List)
            for (Object o : (List<Object>) items)
                if (o instanceof Map)
                    s.items.add(Item.fromMap((Map<String, Object>) o));
        return s;
    }

    public Setup copy() {
        return fromMap(toMap());
    }

    public void save(File f) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        Files.write(tmp.toPath(), Json.write(toMap()).getBytes(StandardCharsets.UTF_8));
        Store.replace(tmp, f);
    }

    @SuppressWarnings("unchecked")
    public static Setup load(File f) throws IOException {
        Object o = Json.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        if (!(o instanceof Map))
            throw new IOException(f.getName() + " is not a MorphoTracer setup.");
        return fromMap((Map<String, Object>) o);
    }

    /** Items from a Guppy Colour profile (profiles/*.json): landmarks become Points, parts Parts, colour classes Colour classes. */
    @SuppressWarnings("unchecked")
    public static List<Item> fromProfile(File f) throws IOException {
        Object o = Json.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        if (!(o instanceof Map))
            throw new IOException(f.getName() + " is not a Guppy Colour profile.");
        Map<String, Object> m = (Map<String, Object>) o;
        List<Item> out = new ArrayList<Item>();
        for (Object l : list(m.get("landmarks"))) {
            Map<String, Object> e = (Map<String, Object>) l;
            Item it = new Item(Json.str(e, "key", ""), Item.Type.POINT, Json.bool(e, "required", false), "");
            it.label = Json.str(e, "label", "");
            out.add(it);
        }
        for (Object l : list(m.get("parts"))) {
            Map<String, Object> e = (Map<String, Object>) l;
            Item it = new Item(Json.str(e, "key", ""), Item.Type.PART, !Json.bool(e, "optional", false), "");
            it.label = Json.str(e, "label", "");
            out.add(it);
        }
        for (Object l : list(m.get("colour_classes"))) {
            Map<String, Object> e = (Map<String, Object>) l;
            Item it = new Item(Json.str(e, "key", ""), Item.Type.COLOUR, !Json.bool(e, "optional", false),
                    Json.str(e, "display", ""));
            it.label = Json.str(e, "label", "");
            out.add(it);
        }
        if (out.isEmpty())
            throw new IOException(f.getName() + " has no landmarks, parts or colour classes.");
        return out;
    }

    private static List<Object> list(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<Object>();
    }
}
