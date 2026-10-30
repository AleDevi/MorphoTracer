package morphotracer;

import java.util.LinkedHashMap;
import java.util.Map;

/** One thing measured on every animal: a length, a point, an area of a body part or of a colour class, a count. */
public class Item {

    public enum Type {
        LENGTH("Length", "a straight or segmented line; From/To: points set by its ends; Continues: added to another length"),
        LENGTH_FROM_POINTS("Length from points", "computed between two points, nothing to draw"),
        POINT("Point", "one click (landmark)"),
        PART("Part", "a polygon around a body part"),
        COLOUR("Colour class", "one polygon per spot"),
        COUNT("Count", "several clicks, one per object counted");

        public final String label;
        public final String hint;

        Type(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public String name;
    public Type type;
    public boolean required;
    public String colour;
    /** LENGTH_FROM_POINTS: the two POINT items it is computed from.
     *  LENGTH: the POINT items set by the start and the end of the line (optional). */
    public String from = "";
    public String to = "";
    /** LENGTH only: another length this one continues (TL = SL + the line from the end of SL to the tail tip). */
    public String continues = "";
    /** Longer description shown in the panel (e.g. from a profile). */
    public String label = "";

    public Item(String name, Type type, boolean required, String colour) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.colour = colour == null || colour.isEmpty() ? defaultColour(type) : colour;
    }

    static String defaultColour(Type t) {
        switch (t) {
            case LENGTH: case LENGTH_FROM_POINTS: return "#00ff00";
            case POINT: return "#ff00ff";
            case PART: return "#ffff00";
            case COLOUR: return "#ff8000";
            default: return "#00ffff";
        }
    }

    /** Is something drawn on the photo for this item? */
    public boolean drawn() {
        return type != Type.LENGTH_FROM_POINTS;
    }

    /** Can a photo hold several ROIs of this item (spots)? */
    public boolean many() {
        return type == Type.COLOUR || type == Type.PART;
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", name);
        m.put("type", type.name());
        m.put("required", required);
        m.put("colour", colour);
        if (!from.isEmpty())
            m.put("from", from);
        if (!to.isEmpty())
            m.put("to", to);
        if (!continues.isEmpty())
            m.put("continues", continues);
        if (!label.isEmpty())
            m.put("label", label);
        return m;
    }

    static Item fromMap(Map<String, Object> m) {
        Item it = new Item(Json.str(m, "name", ""), Type.valueOf(Json.str(m, "type", "LENGTH")),
                Json.bool(m, "required", false), Json.str(m, "colour", ""));
        it.from = Json.str(m, "from", "");
        it.to = Json.str(m, "to", "");
        it.continues = Json.str(m, "continues", "");
        it.label = Json.str(m, "label", "");
        return it;
    }
}
