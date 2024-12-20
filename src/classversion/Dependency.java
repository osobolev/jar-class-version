package classversion;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record Dependency(String group, String artifact, String version, String classifier) {

    private static final Pattern DEPENDENCY_WRAPPER = Pattern.compile("['\"]?\\w+['\"]?\\s*\\(\\s*['\"]?(.*)['\"]?\\s*\\)");
    private static final Pattern DEPENDENCY = Pattern.compile("([\\w.]+):([\\w.]+):([\\w.]+)(:[\\w.]+)?");

    static final String MAVEN_BASE_URL = "https://repo1.maven.org/maven2";

    static Dependency parseDependency(String arg) {
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
