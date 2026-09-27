plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "dev.rutvik.flutter_developer_tools"
version = "1.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    // NOTE: kotlinx-coroutines is intentionally NOT declared here. The IntelliJ Platform
    // bundles a patched coroutines fork; bundling our own copy causes classloader conflicts.
    // See https://plugins.jetbrains.com/docs/intellij/kotlin-coroutines.html

    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        create("IC", "2025.2.3")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("org.intellij.plugins.markdown")
        plugin("Dart", "252.25557.23")
        plugin("io.flutter", "88.0.0")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
            // untilBuild intentionally left unset for open-ended forward compatibility.
            // The effective ceiling is governed by the Dart and io.flutter plugin dependencies.
        }

        changeNotes = """
        <h3>1.0.1</h3>
        <h4>Fixed</h4>
        <ul>
            <li>Type hints no longer throw errors with Dart plugin 509 and newer, and work again there (#1)</li>
            <li>Parameter name hints no longer throw errors on constructor calls with Dart plugin 508.1 and newer</li>
            <li>If a Dart or Flutter plugin update breaks one of our features, that feature now turns off quietly instead of breaking highlighting in the whole editor</li>
            <li>Package docs on hover no longer hang for up to 30 seconds when pub.dev can't be reached</li>
            <li>Package upgrade quick fixes no longer start pub get while the IDE is holding its write lock</li>
            <li>Saving breadcrumb settings can no longer clash with breadcrumbs being drawn</li>
        </ul>
        <h4>Added</h4>
        <ul>
            <li>A "Report on GitHub" button for plugin errors in the IDE error dialog</li>
            <li>A new plugin icon, with a dark theme version</li>
        </ul>
        <h3>1.0.0</h3>
        <p>First release.</p>
        <h4>Package management</h4>
        <ul>
            <li>Pub.dev auto-complete with popularity info</li>
            <li>README and CHANGELOG on hover, with repository links</li>
            <li>Inline update hints, with safe and full upgrade fixes</li>
            <li>Warnings for discontinued and incompatible packages</li>
            <li>Click through to pub.dev package and version pages</li>
        </ul>
        <h4>Dart code</h4>
        <ul>
            <li>Doc code samples highlighted to match your IDE theme</li>
            <li>Breadcrumbs with custom icons for classes, methods, and more</li>
            <li>Parameter name hints for positional arguments</li>
            <li>Inferred type hints for variables and parameters</li>
            <li>Code lens with usage and implementation counts</li>
        </ul>
        <h4>Quick actions</h4>
        <ul>
            <li>Run flutter gen-l10n from .arb files</li>
            <li>Run build_runner build, watch, and clean from generated files</li>
        </ul>
        """.trimIndent()
    }

    // No signing/publishing tasks are configured: the plugin is uploaded manually through the
    // JetBrains Marketplace web UI, which signs it for distribution. Author signing is optional
    // and can be added later if desired (https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).

    // Run the IntelliJ Plugin Verifier against the IDEs recommended for our compatibility range.
    pluginVerification {
        ides {
            recommended()
        }
    }

    buildSearchableOptions = false
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
