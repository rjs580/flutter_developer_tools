package dev.rutvik.flutter_developer_tools.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExtensionGuardTest {

    private fun failAt(message: String): Throwable = try {
        throw IllegalStateException(message)
    } catch (e: IllegalStateException) {
        e
    }

    @Test
    fun sameErrorWithDifferentMessageHasSameSignature() {
        // Same throw site, per-call details in the message (e.g. offsets) must not defeat de-duplication.
        val errors = listOf("offset 10", "offset 42").map { failAt(it) }
        assertEquals(errorSignature("hints", errors[0]), errorSignature("hints", errors[1]))
    }

    @Test
    fun signatureUsesRootCause() {
        val root = NoSuchMethodError("analysis_getHover")
        val wrapped = RuntimeException("wrapper", root)
        assertEquals(errorSignature("hints", root), errorSignature("hints", wrapped))
    }

    @Test
    fun differentFeatureOrExceptionTypeHasDifferentSignature() {
        val error = failAt("x")
        assertNotEquals(errorSignature("hints", error), errorSignature("code vision", error))
        assertNotEquals(errorSignature("hints", error), errorSignature("hints", NoSuchMethodError("x")))
    }
}
