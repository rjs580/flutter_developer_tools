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
            <li><b>Enhanced Breadcrumbs</b>: Configurable navigation bar with custom icons and widgets for classes, methods, constructors, mixins, enums, extensions, getters, and setters</li>
            <li><b>Parameter Hints</b>: Show parameter names for non-named arguments</li>
            <li><b>Type Hints</b>: Display inferred types for variables and parameters</li>
            <li><b>Code Lens</b>: Usage counts and implementation counts for code elements</li>
        </ul>

        <h4>Visualization Tools</h4>
        <ul>
            <li><b>Widget Usage Heatmap Tool Window</b>: Analyze widget usage patterns across your Flutter project
                <ul>
                    <li>Visual heatmap with color-coded intensity (green to red)</li>
                    <li>Sortable table showing usage count, file count, and inheritance info</li>
                    <li>Filter by custom widgets or high-usage widgets (>10 references)</li>
                    <li>Export to CSV for further analysis</li>
                    <li>Double-click navigation to widget definitions</li>
                    <li>Manual refresh with last updated timestamp</li>
                    <li>Helps identify refactoring candidates and reusable components</li>
                </ul>
            </li>
            <li><b>Dart Call Graph Tool Window</b>: Visual representation of function/method call relationships
                <ul>
                    <li>Interactive graph showing control flow in current Dart file</li>
                    <li>Color-coded nodes by type (function, method, constructor, getter, setter)</li>
                    <li>Hierarchical layout with automatic level detection</li>
                    <li>Zoom in/out/reset controls for large graphs</li>
                    <li>Auto-refresh when switching files (toggleable)</li>
                    <li>Filter by top-level functions or methods only</li>
                    <li>Export to PNG for documentation</li>
                    <li>Click on nodes to navigate to definitions</li>
                </ul>
            </li>
        </ul>

        <h4>Quick Actions</h4>
        <ul>
            <li>Run 'Flutter gen-l10n' directly from .arb files</li>
        </ul>

        <h4>Additional Features</h4>
        <ul>
            <li>Multi-repository support (GitHub, GitLab, Bitbucket, Codeberg, SourceHut)</li>
            <li>Monorepo-aware package documentation fetching</li>
            <li>Configurable hint display options</li>
            <li>Cache management for optimal performance</li>
            <li>Full light and dark theme support for all visualization tools</li>
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
