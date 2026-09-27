package dev.rutvik.flutter_developer_tools.utils

import com.intellij.openapi.progress.ProgressManager
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Waits for [future] for at most [timeoutMs], checking for cancellation so a caller holding a read action
 * (e.g. a documentation popup) is released as soon as the IDE cancels it. Returns null on timeout or failure.
 */
internal fun <T> awaitCancellably(future: Future<T>, timeoutMs: Long): T? {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
    while (System.nanoTime() < deadline) {
        ProgressManager.checkCanceled()
        try {
            return future.get(50, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            // keep polling
        } catch (_: ExecutionException) {
            return null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        }
    }
    future.cancel(true)
    return null
}
