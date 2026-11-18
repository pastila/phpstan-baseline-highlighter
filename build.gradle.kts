import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.security.MessageDigest
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "dev.baselinehighlighter"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

val phpBackend = providers.gradleProperty("phpBackend").orElse("jetbrains")
val openPhpPluginPath = providers.gradleProperty("openPhpPluginPath")
val raketaPhpPluginPath = providers.gradleProperty("raketaPhpPluginPath")
val mcpServerPluginPath = providers.gradleProperty("mcpServerPluginPath")
val mcpServerEnabled = providers.gradleProperty("mcpServerEnabled").map(String::toBoolean).orElse(true)
val openPhpSdkZip = layout.projectDirectory.file(".intellijPlatform/sdk/php-for-openide-0.9.9.zip")
val downloadOpenPhpSdk by tasks.registering {
    val checksum = "805b689889b37971c7f2b0f08d264d8ef33b9d1a210b20324373d753d0e02718"
    inputs.property("version", "0.9.9")
    inputs.property("sha256", checksum)
    outputs.file(openPhpSdkZip)
    doLast {
        val destination = openPhpSdkZip.asFile
        fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        if (!destination.exists() || digest(destination) != checksum) {
            check(!gradle.startParameter.isOffline) { "OpenPHP SDK is not cached. Run without --offline or pass -PopenPhpPluginPath=/path/to/php-for-openide." }
            destination.parentFile.mkdirs()
            val temporary = File(destination.parentFile, destination.name + ".tmp")
            try {
                URI("https://plugins.openide.ru/plugin/download?pluginId=ru.openide.openphp&version=0.9.9").toURL().openConnection().apply {
                    connectTimeout = 15000
                    readTimeout = 60000
                }.getInputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(digest(temporary) == checksum) { "OpenPHP SDK checksum mismatch" }
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
        }
    }
}
val unpackOpenPhpSdk by tasks.registering(Sync::class) {
    dependsOn(downloadOpenPhpSdk)
    from({ zipTree(openPhpSdkZip) })
    into(layout.buildDirectory.dir("sdk/openphp"))
}

dependencies {
    if (!mcpServerEnabled.get()) {
        require(mcpServerPluginPath.isPresent) { "Pass -PmcpServerPluginPath=/path/to/mcpserver to compile without installing MCP Server." }
        compileOnly(fileTree(mcpServerPluginPath.get()) { include("lib/*.jar") })
        testCompileOnly(fileTree(mcpServerPluginPath.get()) { include("lib/*.jar") })
    }
    if (openPhpPluginPath.isPresent) compileOnly(fileTree(openPhpPluginPath.get()) { include("lib/*.jar"); exclude("**/*-searchableOptions.jar") })
    else compileOnly(files(fileTree(unpackOpenPhpSdk.map { it.destinationDir }) { include("**/lib/*.jar"); exclude("**/*-searchableOptions.jar") }).builtBy(unpackOpenPhpSdk))
    intellijPlatform {
        val localPath = providers.gradleProperty("localIdePath").orNull
        if (localPath != null) local(localPath)
        else create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        if (phpBackend.get() == "jetbrains") {
            val phpVersion = providers.gradleProperty("phpPluginVersion").orNull
            if (phpVersion == null) bundledPlugin("com.jetbrains.php") else plugin("com.jetbrains.php:$phpVersion")
        } else if (phpBackend.get() == "openphp") {
            localPlugin(openPhpPluginPath.get())
            bundledPlugin("org.jetbrains.plugins.yaml")
            bundledPlugins("intellij.libraries.misc.plugin", "intellij.testRunner.plugin", "intellij.structureView.plugin", "intellij.bookmarks.plugin")
        } else if (phpBackend.get() == "raketa") {
            if (raketaPhpPluginPath.isPresent) testLocalPlugin(raketaPhpPluginPath.get())
            else testBundledPlugin("ru.raketa.php")
            testBundledPlugin("intellij.testRunner.plugin")
        }
        if (providers.gradleProperty("platformType").get() == "IU" && localPath == null) bundledModules("com.intellij.modules.ultimate")
        bundledPlugin("com.intellij.modules.json")
        if (mcpServerEnabled.get()) {
            if (mcpServerPluginPath.isPresent) localPlugin(mcpServerPluginPath.get())
            else bundledPlugin("com.intellij.mcpServer")
        }
        val frameworkVersion = providers.gradleProperty("testFrameworkVersion").orNull
        if (frameworkVersion == null) testFramework(TestFrameworkType.Platform)
        else testFramework(TestFrameworkType.Platform, frameworkVersion)
    }
    if (phpBackend.get() != "jetbrains") {
        compileOnly(fileTree(providers.gradleProperty("jetBrainsPhpPluginPath").get()) { include("lib/*.jar") })
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation(kotlin("stdlib"))
}

kotlin { jvmToolchain(21) }

intellijPlatform {
    pluginConfiguration {
        name = "PHPStan Baseline Highlighter"
        ideaVersion { sinceBuild = "252"; untilBuild = "263.*" }
    }
    pluginVerification {
        ides {
            val localPath = providers.gradleProperty("verificationIdePath").orNull
            if (localPath == null) recommended() else local(localPath)
        }
    }
}

tasks.test {
    useJUnit()
    maxHeapSize = "1g"
    systemProperty("java.awt.headless", "true")
    systemProperty("baseline.phpBackend", phpBackend.get())
    systemProperty("baseline.mcpServerEnabled", mcpServerEnabled.get())
    filter {
        if (phpBackend.get() == "none") {
            excludeTestsMatching("*.PhpatNavigationTest")
            excludeTestsMatching("*.BaselineIntegrationTest")
            excludeTestsMatching("*.BaselineFunctionIntegrationTest")
            excludeTestsMatching("*.BaselineMcpToolsetTest")
        }
        else excludeTestsMatching("*.BaselineWithoutPhpTest")
        if (!mcpServerEnabled.get()) excludeTestsMatching("*.BaselineMcpToolsetTest")
    }
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/${phpBackend.get()}"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/${phpBackend.get()}"))
    // IDE bootstrap requires these flags when running fixture tests on JDK 21.
    jvmArgs("-ea")
    val ideHome = provider { intellijPlatform.platformPath.toFile().absolutePath }
    jvmArgumentProviders += CommandLineArgumentProvider { listOf("-Didea.home.path=${ideHome.get()}") }
    testLogging { events("failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

tasks.register<JavaExec>("benchmarkBaselines") {
    group = "verification"
    description = "Measures parsing a directory of baseline files without PHPStan"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.baselinehighlighter.core.BaselineBenchmark"
    providers.gradleProperty("baselineDirectory").orNull?.let { args(it) }
}
