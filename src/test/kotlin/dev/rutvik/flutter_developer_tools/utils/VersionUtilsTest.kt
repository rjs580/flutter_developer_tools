package dev.rutvik.flutter_developer_tools.utils

import dev.rutvik.flutter_developer_tools.utils.VersionUtils.UpdateType
import org.junit.Assert.assertEquals
import org.junit.Test

class VersionUtilsTest {

    @Test
    fun majorBumpIsMajor() {
        assertEquals(UpdateType.MAJOR, VersionUtils.getUpdateType("1.2.3", "2.0.0"))
    }

    @Test
    fun minorBumpIsMinor() {
        assertEquals(UpdateType.MINOR, VersionUtils.getUpdateType("1.2.3", "1.3.0"))
    }

    @Test
    fun patchBumpIsPatch() {
        assertEquals(UpdateType.PATCH, VersionUtils.getUpdateType("1.2.3", "1.2.4"))
    }

    @Test
    fun equalVersionsAreNoUpdate() {
        assertEquals(UpdateType.NONE, VersionUtils.getUpdateType("1.2.3", "1.2.3"))
    }

    @Test
    fun downgradeIsNoUpdate() {
        assertEquals(UpdateType.NONE, VersionUtils.getUpdateType("2.0.0", "1.9.9"))
    }

    /** Under pub caret rules, ^0.13.0 allows only < 0.14.0, so 0.14.0 is a breaking change. */
    @Test
    fun zeroXMinorBumpIsBreaking() {
        assertEquals(UpdateType.MAJOR, VersionUtils.getUpdateType("0.13.0", "0.14.0"))
    }

    @Test
    fun zeroXPatchBumpIsPatch() {
        assertEquals(UpdateType.PATCH, VersionUtils.getUpdateType("0.13.0", "0.13.5"))
    }

    /** For 0.0.z each patch is breaking under pub caret rules. */
    @Test
    fun zeroZeroPatchBumpIsBreaking() {
        assertEquals(UpdateType.MAJOR, VersionUtils.getUpdateType("0.0.3", "0.0.4"))
    }

    @Test
    fun preReleaseToStableIsAnUpdate() {
        assertEquals(UpdateType.PATCH, VersionUtils.getUpdateType("2.0.0-beta.1", "2.0.0"))
    }

    @Test
    fun safeUpgradeStaysWithinMajor() {
        val safe = VersionUtils.getSafeUpgradeVersion("1.2.3", listOf("1.3.0", "1.9.9", "2.0.0"))
        assertEquals("1.9.9", safe)
    }

    @Test
    fun safeUpgradeDoesNotCrossZeroXMinorBoundary() {
        val safe = VersionUtils.getSafeUpgradeVersion("0.13.0", listOf("0.13.5", "0.14.0", "0.99.0"))
        assertEquals("0.13.5", safe)
    }

    @Test
    fun safeUpgradeSkipsPreReleases() {
        val safe = VersionUtils.getSafeUpgradeVersion("1.5.0", listOf("1.6.0", "1.10.0-dev.1"))
        assertEquals("1.6.0", safe)
    }

    @Test
    fun safeUpgradeReturnsNullWhenOnlyBreakingAvailable() {
        val safe = VersionUtils.getSafeUpgradeVersion("1.2.3", listOf("2.0.0", "3.0.0"))
        assertEquals(null, safe)
    }
}
