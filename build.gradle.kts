plugins {
    id("java")
}

group = "io.github.osobolev"
version = "1.0"

repositories {
    mavenCentral()
}

sourceSets {
    main {
        java.srcDir("src")
        resources.srcDir("resources")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "classversion.Main"
        )
    }
}

tasks.register("distr", Copy::class) {
    from(tasks.jar)
    from("config")
    into("$rootDir/distr")
}
