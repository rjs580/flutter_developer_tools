plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.10.4"
}

group = "dev.rutvik.flutter_developer_tools"
version = "1.0.0"

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    intellijPlatform {
        create("IC", "2025.1.4.1")
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
            sinceBuild = "251"
        }

        changeNotes = """
        <h3>Version 1.0.0</h3>
        <h4>Pubspec.yaml Intelligence</h4>
        <ul>
            <li><b>Smart Auto-completion</b>: Pub.dev package suggestions with popularity indicators</li>
            <li><b>Package Documentation</b>: Hover for README and CHANGELOG with repository links</li>
            <li><b>Update Management</b>: Inline hints with safe/full upgrade quick fixes</li>
            <li><b>Health Warnings</b>: Visual badges for discontinued and incompatible packages</li>
            <li><b>Quick Navigation</b>: Click-through to pub.dev package and version pages</li>
        </ul>

        <h4>Dart Code Enhancement</h4>
        <ul>
            <li><b>Syntax-Highlighted Documentation</b>: Code examples in hover docs respect your IDE color scheme</li>
            <li><b>Enhanced Breadcrumbs</b>: Navigate through classes, methods, constructors, mixins, enums, extensions, getters, and setters</li>
            <li><b>Parameter Hints</b>: Show parameter names for non-named arguments</li>
            <li><b>Type Hints</b>: Display inferred types for variables and parameters</li>
            <li><b>Code Lens</b>: Usage counts and implementation counts for code elements</li>
        </ul>

        <h4>Additional Features</h4>
        <ul>
            <li>Multi-repository support (GitHub, GitLab, Bitbucket, Codeberg, SourceHut)</li>
            <li>Monorepo-aware package documentation fetching</li>
            <li>Configurable hint display options</li>
            <li>Cache management for optimal performance</li>
        </ul>
        """.trimIndent()
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
