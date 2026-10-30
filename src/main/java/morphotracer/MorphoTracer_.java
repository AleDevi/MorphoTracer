package morphotracer;

import ij.IJ;
import ij.plugin.PlugIn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Plugins > MorphoTracer: opens the panel. Setups live in ~/.morphotracer/setups (one JSON file each). */
public class MorphoTracer_ implements PlugIn {

    public static final String VERSION = "0.1.0";
    static final String[] EXAMPLES = {"guppy_male_example", "measures_only_example"};

    @Override
    public void run(String arg) {
        if (Panel.instance != null) {
            Panel.instance.toFront();
            return;
        }
        try {
            Panel.instance = new Panel();
            Panel.instance.setVisible(true);
        } catch (Exception e) {
            IJ.handleException(e);
        }
    }

    static File setupsDir() {
        return new File(new File(System.getProperty("user.home"), ".morphotracer"), "setups");
    }

    /** First start: write the example setups so there is something to begin with. */
    @SuppressWarnings("unchecked")
    static void writeExamples() throws IOException {
        File dir = setupsDir();
        dir.mkdirs();
        for (String n : EXAMPLES) {
            File f = new File(dir, n + ".json");
            if (!f.exists())
                Setup.fromMap((Map<String, Object>) Json.parse(example(n))).save(f);
        }
    }

    static String example(String name) throws IOException {
        InputStream in = MorphoTracer_.class.getResourceAsStream("/setups/" + name + ".json");
        if (in == null)
            throw new IOException("Missing example setup " + name);
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0)
                b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
