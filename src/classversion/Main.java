package classversion;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class Main {

    private static final Pattern DEPENDENCY = Pattern.compile("([^:]+):([^:]+):([^:]+)(:[^:]+)?");

    private static String javaVersion(int major, int minor) {
        if (major >= 49) {
            return String.valueOf((major - 49) + 5);
        } else if (major >= 46) {
            int subJava = (major - 46) + 2;
            return "1." + subJava;
        } else if (major == 45) {
            if (minor == 3) {
                return "1.1";
            } else if (minor == 0) {
                return "1.0";
            } else {
                return "1.x";
            }
        }
        return null;
    }

    private static String fullVersion(int major, int minor) {
        String java = javaVersion(major, minor);
        if (java == null)
            return null;
        return String.format("Java %s (%s.%s)", java, major, minor);
    }

    private static String detectClassVersion(InputStream in) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(in)) {
            byte[] buf = new byte[8];
            while (true) {
                ZipEntry entry = zis.getNextEntry();
                if (entry == null)
                    break;
                if (entry.isDirectory())
                    continue;
                String name = entry.getName().toLowerCase();
                if (name.startsWith("meta-inf"))
                    continue;
                if (!name.endsWith(".class"))
                    continue;
                if (name.endsWith("-info.class"))
                    continue;
                int read = zis.readNBytes(buf, 0, buf.length);
                if (read < buf.length)
                    continue;
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf));
                int magic = dis.readInt();
                if (magic != 0xCAFEBABE)
                    continue;
                int minor = dis.readUnsignedShort();
                int major = dis.readUnsignedShort();
                String version = fullVersion(major, minor);
                if (version == null)
                    continue;
                return version;
            }
        }
        return "<unknown>";
    }

    private static void detectClassVersion(String name, InputStream in) throws IOException {
        String version = detectClassVersion(in);
        System.out.printf("%s: %s%n", name, version);
    }

    private static void detectClassVersion(String name, URI uri) throws IOException {
        URLConnection conn = uri.toURL().openConnection();
        try (InputStream is = conn.getInputStream()) {
            detectClassVersion(name, is);
        }
    }

    private static void detectClassVersion(Path root, Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Path newRoot = root == null ? path : root;
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(path)) {
                for (Path child : paths) {
                    detectClassVersion(newRoot, child);
                }
            }
        } else {
            String name = root == null ? path.toString() : root.relativize(path).toString();
            try (InputStream is = Files.newInputStream(path)) {
                detectClassVersion(name, is);
            }
        }
    }

    private static void detectClassVersion(String arg) throws IOException {
        Matcher matcher = DEPENDENCY.matcher(arg);
        if (matcher.matches()) {
            String group = matcher.group(1);
            String artifact = matcher.group(2);
            String version = matcher.group(3);
            String classifier = matcher.group(4);
            URI uri = URI.create(String.format(
                "https://repo1.maven.org/maven2/%s/%s/%s/%s-%s%s.jar",
                group.replace('.', '/'), artifact, version,
                artifact, version, classifier == null ? "" : "-" + classifier
            ));
            detectClassVersion(arg, uri);
            return;
        }
        try {
            URI uri = URI.create(arg);
            if (uri.getScheme() != null) {
                detectClassVersion(arg, uri);
                return;
            }
        } catch (Exception ex) {
            // ignore
        }
        Path path = Path.of(arg);
        if (Files.exists(path)) {
            detectClassVersion(null, path);
            return;
        }
        System.err.printf("'%s' is not an URL, file or dependency%n", arg);
    }

    public static void main(String[] args) throws IOException {
        for (String arg : args) {
            detectClassVersion(arg);
        }
    }
}
