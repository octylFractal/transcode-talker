import org.cadixdev.gradle.licenser.LicenseExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    application
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.licenser)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(18))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenCentral()
}

configure<LicenseExtension> {
    exclude {
        // TODO make a PR to licenser to properly fix this
        it.file.startsWith(project.buildDir)
    }
    header(rootProject.file("HEADER.txt"))
    (this as ExtensionAware).extra.apply {
        for (key in listOf("organization", "url")) {
            set(key, rootProject.property(key))
        }
    }
}

dependencies {
    compileOnly(libs.jetbrains.annotations)

    implementation(libs.guava)

    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)

    // Spring Boot Substitutions (+$1/each)
    implementation(libs.spring.boot.starter.undertow)
    implementation(libs.spring.boot.starter.log4j2)
    modules {
        module(libs.spring.boot.starter.tomcat.get().module) {
            replacedBy(libs.spring.boot.starter.undertow.get().module, "Tomcat bad.")
        }
        module(libs.spring.boot.starter.logging.get().module) {
            replacedBy(libs.spring.boot.starter.log4j2.get().module, "Log4J2 good.")
        }
    }

    implementation(libs.owasp.html.sanitizer)

    implementation(libs.adventure.api)
    implementation(libs.adventure.textSerializer.gson)
    implementation(libs.adventure.textSerializer.legacy)
    implementation(libs.adventure.textSerializer.plain)

    // For Log4J2 async
    runtimeOnly(libs.disruptor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

application {
    mainClass.set("net.octyl.transcodetalker.TranscodeTalker")
    applicationDefaultJvmArgs = listOf(
        "-Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector",
        "-Xms64M",
        "-Xmx128M",
        "-XX:G1PeriodicGCInterval=1000"
    )
}

tasks.named<JavaExec>("run") {
    workingDir(".")
}

tasks.test {
    useJUnitPlatform()
}
