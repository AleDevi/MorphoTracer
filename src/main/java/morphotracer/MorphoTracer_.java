package morphotracer;

import ij.IJ;
import ij.plugin.PlugIn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/** Plugins > MorphoTracer: opens the panel. Setups live in ~/.morphotracer/setups (one JSON file each). */
public class MorphoTracer_ implements PlugIn {

    public static final String VERSION = "0.3.1";
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
            String text = example(n);
            int version = (int) Json.num((Map<String, Object>) Json.parse(text), "example_version", 0);
            if (f.exists()) {
                int old = 0;
                try {
                    old = (int) Json.num((Map<String, Object>) Json.parse(
                            new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)), "example_version", 0);
                } catch (RuntimeException e) {
                    old = 0;
                }
                if (old >= version)
                    continue;
                // a newer example: keep the old file (maybe edited) under another name
                File keep = new File(dir, n + "_before_" + VERSION.replace('.', '_') + ".json");
                if (!keep.exists())
                    Files.move(f.toPath(), keep.toPath());
            }
            Setup.fromMap((Map<String, Object>) Json.parse(text)).save(f);
            // keep the example version, which Setup does not store
            Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
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
