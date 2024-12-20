package classversion;

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

    private static final Pattern DEPENDENCY_WRAPPER = Pattern.compile("['\"]?\\w+['\"]?\\s*\\(\\s*['\"]?(.*)['\"]?\\s*\\)");
    private static final Pattern DEPENDENCY = Pattern.compile("([\\w.]+):([\\w.]+):([\\w.]+)(:[\\w.]+)?");
    private static final String MAVEN_BASE_URL = "https://repo1.maven.org/maven2";

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

    private static String detectClassClassVersion(MagicDetector detected) throws IOException {
        DataInputStream dis = new DataInputStream(detected.restStream);
        int minor = dis.readUnsignedShort();
        int major = dis.readUnsignedShort();
        return fullVersion(major, minor);
    }

    private static String detectJarClassVersion(InputStream in) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(in)) {
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
                MagicDetector detected = MagicDetector.detect(zis);
                if (detected.fileType != FileType.CLASS)
                    continue;
                String version = detectClassClassVersion(detected);
                if (version == null)
                    continue;
                return version;
            }
        }
        return null;
    }

    private static void detectClassVersion(boolean warnUndetected, String name, InputStream in) throws IOException {
        MagicDetector detected = MagicDetector.detect(in);
        String version;
        if (detected.fileType == FileType.JAR) {
            version = detectJarClassVersion(detected.fullStream);
        } else if (detected.fileType == FileType.CLASS) {
            version = detectClassClassVersion(detected);
        } else {
            if (warnUndetected) {
                System.err.printf("'%s' is not a JAR or class%n", name);
            }
            return;
        }
        System.out.printf("%s: %s%n", name, version == null ? "<unknown>" : version);
    }

    private static void detectUriClassVersion(String name, URI uri) throws IOException {
        URLConnection conn = uri.toURL().openConnection();
        try (InputStream is = conn.getInputStream()) {
            detectClassVersion(true, name, is);
        }
    }

    private static void detectFileClassVersion(Path root, Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Path newRoot = root == null ? path : root;
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(path)) {
                for (Path child : paths) {
                    detectFileClassVersion(newRoot, child);
                }
            }
        } else {
            String name = root == null ? path.toString() : root.relativize(path).toString();
            try (InputStream is = Files.newInputStream(path)) {
                detectClassVersion(root == null, name, is);
            }
        }
    }

    private record Dependency(String group, String artifact, String version, String classifier) {

        @Override
        public String toString() {
            return String.format(
                "%s:%s:%s%s",
                group, artifact, version, classifier == null ? "" : ":" + classifier
            );
        }

        URI toMavenURI() {
            return URI.create(String.format(
                "%s/%s/%s/%s/%s-%s%s.jar",
                MAVEN_BASE_URL, group.replace('.', '/'), artifact, version,
                artifact, version, classifier == null ? "" : "-" + classifier
            ));
        }
    }

    private static Dependency parseDependency(String arg) {
        String depArg;
        {
            Matcher matcher = DEPENDENCY_WRAPPER.matcher(arg);
            if (matcher.matches()) {
                depArg = matcher.group(1);
            } else {
                depArg = arg;
            }
        }
        Matcher matcher = DEPENDENCY.matcher(depArg);
        if (!matcher.matches())
            return null;
        String group = matcher.group(1);
        String artifact = matcher.group(2);
        String version = matcher.group(3);
        String classifier = matcher.group(4);
        return new Dependency(group, artifact, version, classifier);
    }

    private static void detectClassVersion(String arg) throws IOException {
        Dependency dep = parseDependency(arg);
        if (dep != null) {
            detectUriClassVersion(dep.toString(), dep.toMavenURI());
            return;
        }
        URI uri = null;
        try {
            URI tryUri = URI.create(arg);
            if (tryUri.getScheme() != null) {
                uri = tryUri;
            }
        } catch (Exception ex) {
            // ignore
        }
        if (uri != null) {
            detectUriClassVersion(arg, uri);
            return;
        }
        Path path = null;
        try {
            Path tryPath = Path.of(arg);
            if (Files.exists(tryPath)) {
                path = tryPath;
            }
        } catch (Exception ex) {
            // ignore
        }
        if (path != null) {
            detectFileClassVersion(null, path);
            return;
        }
        System.err.printf("'%s' is not an URL, file or dependency%n", arg);
    }

    public static void main(String[] args) throws IOException {
        if (args.length <= 0) {
            System.out.println("Usage:");
            System.out.println("class-version.bat <file>");
            System.out.println("class-version.bat <URL>");
            System.out.println("class-version.bat <group>:<artifact>:<version>[:<classifier>]");
            System.out.println("class-version.bat <scope>(\"<group>:<artifact>:<version>[:<classifier>]\")");
            return;
        }
        for (String arg : args) {
            detectClassVersion(arg);
        }
    }
}
