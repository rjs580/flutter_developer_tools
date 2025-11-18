package dev.rutvik.flutter_developer_tools.utils

/**
 * Utilities for comparing semantic versions and determining update types.
 */
object VersionUtils {

    data class SemanticVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String? = null,
        val build: String? = null
    ) : Comparable<SemanticVersion> {

        override fun compareTo(other: SemanticVersion): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)

            // Pre-release versions have lower precedence
            return when {
                preRelease == null && other.preRelease == null -> 0
                preRelease == null -> 1
                other.preRelease == null -> -1
                else -> preRelease.compareTo(other.preRelease)
            }
        }

        override fun toString(): String {
            val base = "$major.$minor.$patch"
            val pre = preRelease?.let { "-$it" } ?: ""
            val bld = build?.let { "+$it" } ?: ""
            return "$base$pre$bld"
        }
    }

    enum class UpdateType {
        MAJOR,
        MINOR,
        PATCH,
        NONE
    }

    /**
     * Parses a version string into a SemanticVersion object.
     * Handles versions like "1.2.3", "1.2.3-beta", "1.2.3+build"
     */
    fun parseVersion(versionStr: String): SemanticVersion? {
        val normalized = PubspecUtils.normalizeVersionString(versionStr)

        // Pattern: major.minor.patch[-prerelease][+build]
        val regex = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z\-.]+))?(?:\+([0-9A-Za-z\-.]+))?${'$'}""")
        val match = regex.matchEntire(normalized) ?: return null

        val (major, minor, patch) = match.destructured
        val preRelease = match.groups[4]?.value
        val build = match.groups[5]?.value

        return try {
            SemanticVersion(
                major = major.toInt(),
                minor = minor.toInt(),
                patch = patch.toInt(),
                preRelease = preRelease,
                build = build
            )
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Determines the type of update between two versions.
     */
    fun getUpdateType(current: String, latest: String): UpdateType {
        val currentVer = parseVersion(current) ?: return UpdateType.NONE
        val latestVer = parseVersion(latest) ?: return UpdateType.NONE

        if (currentVer >= latestVer) return UpdateType.NONE

        return when {
            latestVer.major > currentVer.major -> UpdateType.MAJOR
            latestVer.minor > currentVer.minor -> UpdateType.MINOR
            latestVer.patch > currentVer.patch -> UpdateType.PATCH
            else -> UpdateType.NONE
        }
    }

    /**
     * Gets the safe upgrade version (skips major updates).
     * Returns the highest version that doesn't increase the major version.
     */
    fun getSafeUpgradeVersion(current: String, latest: String): String? {
        val currentVer = parseVersion(current) ?: return null
        val latestVer = parseVersion(latest) ?: return null

        // If latest is same or lower major version, it's safe
        if (latestVer.major <= currentVer.major) {
            return latest
        }

        // Otherwise, keep the current major version (can't suggest a specific version without API data)
        return null
    }

    /**
     * Formats an update type as a human-readable label.
     */
    fun formatUpdateType(updateType: UpdateType): String {
        return when (updateType) {
            UpdateType.MAJOR -> "Major Update"
            UpdateType.MINOR -> "Minor Update"
            UpdateType.PATCH -> "Patch Update"
            UpdateType.NONE -> ""
        }
    }
}