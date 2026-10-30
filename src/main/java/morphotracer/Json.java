package morphotracer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader and writer for setups and profiles (ImageJ has no JSON library).
 *  Objects become LinkedHashMap, arrays ArrayList, numbers Double, plus String, Boolean and null. */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length())
            throw p.error("unexpected text after the end");
        return v;
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("JSON: " + what + " at character " + i);
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i)))
            i++;
    }

    private Object value() {
        if (i >= s.length())
            throw error("unexpected end");
        char c = s.charAt(i);
        if (c == '{')
            return object();
        if (c == '[')
            return array();
        if (c == '"')
            return string();
        if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        if (s.startsWith("null", i)) {
            i += 4;
            return null;
        }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == '}') {
            i++;
            return m;
        }
        while (true) {
            ws();
            if (i >= s.length() || s.charAt(i) != '"')
                throw error("expected a name in quotes");
            String k = string();
            ws();
            expect(':');
            ws();
            m.put(k, value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            expect('}');
            return m;
        }
    }

    private List<Object> array() {
        List<Object> a = new ArrayList<Object>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == ']') {
            i++;
            return a;
        }
        while (true) {
            ws();
            a.add(value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            expect(']');
            return a;
        }
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c)
            throw error("expected '" + c + "'");
        i++;
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"')
                return b.toString();
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (i >= s.length())
                break;
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u':
                    if (i + 4 > s.length())
                        throw error("bad \\u escape");
                    b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: b.append(e);
            }
        }
        throw error("unterminated string");
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0)
            i++;
        if (start == i)
            throw error("unexpected character '" + s.charAt(i) + "'");
        return Double.valueOf(s.substring(start, i));
    }

    /** Pretty JSON, two spaces per level. */
    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v, "");
        return b.append('\n').toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object v, String indent) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String) {
            quote(b, (String) v);
        } else if (v instanceof Boolean) {
            b.append(v.toString());
        } else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15)
                b.append((long) d);
            else
                b.append(d);
        } else if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v;
            if (m.isEmpty()) {
                b.append("{}");
                return;
            }
            b.append("{\n");
            int n = 0;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                b.append(indent).append("  ");
                quote(b, e.getKey());
                b.append(": ");
                write(b, e.getValue(), indent + "  ");
                b.append(++n < m.size() ? ",\n" : "\n");
            }
            b.append(indent).append('}');
        } else if (v instanceof List) {
            List<Object> a = (List<Object>) v;
            if (a.isEmpty()) {
                b.append("[]");
                return;
            }
            b.append("[\n");
            for (int k = 0; k < a.size(); k++) {
                b.append(indent).append("  ");
                write(b, a.get(k), indent + "  ");
                b.append(k + 1 < a.size() ? ",\n" : "\n");
            }
            b.append(indent).append(']');
        } else {
            quote(b, v.toString());
        }
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20)
                        b.append(String.format("\\u%04x", (int) c));
                    else
                        b.append(c);
            }
        }
        b.append('"');
    }

    // small typed getters used by Setup and the profile import

    static String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v == null ? def : v.toString();
    }

    static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    static double num(Map<String, Object> m, String k, double def) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }
}
