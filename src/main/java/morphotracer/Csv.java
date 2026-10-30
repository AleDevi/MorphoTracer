package morphotracer;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CSV files ready for read.csv() in R: comma, decimal point, NA for missing values, UTF-8. */
public final class Csv {

    public static final String NA = "NA";

    private Csv() {
    }

    /** Number with at most 4 decimals, or NA. */
    public static String num(Double v) {
        if (v == null || v.isNaN() || v.isInfinite())
            return NA;
        return new BigDecimal(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public static String bool(boolean b) {
        return b ? "TRUE" : "FALSE";
    }

    /** Free text: Excel would read '=...', '+...', '-...', '@...' as a formula, so it gets a leading '. */
    public static String text(String s) {
        if (s == null || s.isEmpty())
            return NA;
        s = s.replace('\n', ' ').replace('\r', ' ');
        if ("=+-@".indexOf(s.charAt(0)) >= 0)
            s = "'" + s;
        return s;
    }

    static String quote(String s) {
        if (s == null)
            return NA;
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0)
            return '"' + s.replace("\"", "\"\"") + '"';
        return s;
    }

    public static void write(File f, List<String> header, List<Map<String, String>> rows) throws IOException {
        StringBuilder b = new StringBuilder();
        line(b, header);
        for (Map<String, String> r : rows) {
            List<String> cells = new ArrayList<String>();
            for (String h : header) {
                String v = r.get(h);
                cells.add(v == null ? NA : v);
            }
            line(b, cells);
        }
        File tmp = new File(f.getPath() + ".tmp");
        Files.write(tmp.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        Store.replace(tmp, f);
    }

    private static void line(StringBuilder b, List<String> cells) {
        for (int k = 0; k < cells.size(); k++) {
            if (k > 0)
                b.append(',');
            b.append(quote(cells.get(k)));
        }
        b.append('\n');
    }

    /** Header in the first element, then one map per row (column -> value as written). */
    public static List<Map<String, String>> read(File f, List<String> headerOut) throws IOException {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        if (!f.exists())
            return rows;
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        if (text.startsWith("﻿"))
            text = text.substring(1);
        List<List<String>> table = parse(text);
        if (table.isEmpty())
            return rows;
        List<String> header = table.get(0);
        headerOut.clear();
        headerOut.addAll(header);
        for (int r = 1; r < table.size(); r++) {
            Map<String, String> m = new LinkedHashMap<String, String>();
            List<String> cells = table.get(r);
            for (int c = 0; c < header.size(); c++)
                m.put(header.get(c), c < cells.size() ? cells.get(c) : NA);
            rows.add(m);
        }
        return rows;
    }

    static List<List<String>> parse(String text) {
        List<List<String>> out = new ArrayList<List<String>>();
        List<String> row = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            if (quoted) {
                if (c == '"' && k + 1 < text.length() && text.charAt(k + 1) == '"') {
                    cell.append('"');
                    k++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && k + 1 < text.length() && text.charAt(k + 1) == '\n')
                    k++;
                row.add(cell.toString());
                cell.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty()))
                    out.add(row);
                row = new ArrayList<String>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            out.add(row);
        }
        return out;
    }

    /** Parse a number written by num(); null for NA or text. */
    public static Double parseNum(String s) {
        if (s == null || s.equals(NA) || s.isEmpty())
            return null;
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
