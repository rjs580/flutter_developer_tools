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
            <h3>Version 1.0.0 - Initial Release</h3>
            <h4>Features</h4>
            <ul>
                <li><b>Smart Package Auto-completion</b>: Auto-complete pub.dev packages with Flutter Favorites badge and likes count</li>
                <li><b>Intelligent Package Insights</b>: Hover over package names to view comprehensive README documentation with repository links</li>
                <li><b>Version Changelog Access</b>: Hover over version numbers to see CHANGELOG with version history</li>
                <li><b>Direct Package Navigation</b>: CMD+click (or CTRL+click) on package names and versions to open pub.dev pages</li>
                <li><b>Update Notifications</b>: Inlay hints showing available updates with categorization (Major/Minor/Patch)</li>
                <li><b>Safe Upgrade Quick Fixes</b>: One-click safe upgrades (skip major versions) and full upgrades with optional pub get execution</li>
                <li><b>Package Health Indicators</b>: Visual warnings for discontinued and Dart 3 incompatible packages</li>
                <li><b>Multi-Repository Support</b>: Fetches documentation from GitHub, GitLab, Bitbucket, Codeberg, and SourceHut</li>
                <li><b>Monorepo Intelligence</b>: Automatically handles packages in monorepo structures</li>
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
