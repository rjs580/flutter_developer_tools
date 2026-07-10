package dev.rutvik.flutter_developer_tools.utils

/**
 * Utilities for comparing semantic versions and determining update types.
 */
object VersionUtils {

    // Pattern: major.minor.patch[-prerelease][+build]
    private val VERSION_REGEX =
        Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z\-.]+))?(?:\+([0-9A-Za-z\-.]+))?$""")

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

        val match = VERSION_REGEX.matchEntire(normalized) ?: return null

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

        // A change that crosses the pub caret boundary is breaking (shown as major). For
        // 0.x versions the first non-zero segment is the breaking one, so 0.13.x -> 0.14.0
        // is a breaking update. This also covers pre-release to stable (2.0.0-beta -> 2.0.0),
        // which reports as a patch update rather than "no update".
        return when {
            isBreakingUpdate(currentVer, latestVer) -> UpdateType.MAJOR
            latestVer.minor > currentVer.minor -> UpdateType.MINOR
            else -> UpdateType.PATCH
        }
    }

    /**
     * Returns true when [latest] falls outside the caret-compatible range of [current],
     * following pub's rules: for x.y.z with x > 0 the major segment is breaking; for 0.y.z
     * the minor segment is breaking; for 0.0.z the patch segment is breaking.
     */
    private fun isBreakingUpdate(current: SemanticVersion, latest: SemanticVersion): Boolean {
        return when {
            current.major > 0 -> latest.major > current.major
            current.minor > 0 -> latest.major > current.major || latest.minor > current.minor
            else -> latest.major > current.major || latest.minor > current.minor || latest.patch > current.patch
        }
    }

    /**
     * Gets the safe upgrade version (highest version without major update).
     * Returns the highest version that doesn't increase the major version.
     */
    fun getSafeUpgradeVersion(current: String, availableVersions: List<String>): String? {
        val currentVer = parseVersion(current) ?: return null
        val currentIsPreRelease = currentVer.preRelease != null

        // A safe upgrade is the highest version that stays inside the caret-compatible
        // range (no breaking change) and, unless already on a pre-release, is stable.
        val safeVersions = availableVersions
            .mapNotNull { parseVersion(it)?.let { ver -> ver to it } }
            .filter { (ver, _) ->
                ver > currentVer &&
                    !isBreakingUpdate(currentVer, ver) &&
                    (currentIsPreRelease || ver.preRelease == null)
            }
            .sortedByDescending { (ver, _) -> ver }

        return safeVersions.firstOrNull()?.second
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