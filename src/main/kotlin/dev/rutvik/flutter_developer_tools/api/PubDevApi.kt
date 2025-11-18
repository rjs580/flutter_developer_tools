package dev.rutvik.flutter_developer_tools.api

import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import dev.rutvik.flutter_developer_tools.models.PubPackage
import dev.rutvik.flutter_developer_tools.services.PubPackageCacheService
import kotlinx.coroutines.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


/**
 * API client for interacting with pub.dev package repository.
 * Provides functionality to fetch package names and details from pub.dev,
 * with caching support to minimize network requests.
 * All network operations are performed asynchronously on background threads
 * with callbacks executed on the EDT.
 */
object PubDevApi {
    private val log = Logger.getInstance(PubDevApi::class.java)

    // Coroutine scope for managing background operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Track in-flight requests to prevent duplicate fetches
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<PubPackage?>>()

    fun fetchPackageNames(callback: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val json = HttpRequests.request("https://pub.dev/api/package-name-completion-data")
                    .connect { it.readString() }

                val array = JsonParser.parseString(json).asJsonObject["packages"].asJsonArray
                    .map { it.asString }
                    .toList()

                val cache = PubPackageCacheService.getInstance()
                
                cache.addPackageNames(array)

                // Invoke callback on EDT to prevent UI threading issues
                ApplicationManager.getApplication().invokeLater {
                    callback()
                }
            } catch (e: Exception) {
                log.warn("Failed to fetch package names", e)
            }
        }
    }

    fun waitForPackageInfo(name: String): PubPackage? {
        val cache = PubPackageCacheService.getInstance()
        val cachedInfo = cache.getInfo(name)

        if (cachedInfo?.latestVersion != null) {
            return cachedInfo
        }

        val future = CompletableFuture<PubPackage>()

        requestDetailsIfNeeded(name) { info ->
            cache.updateDetails(info)
            future.complete(info)
        }

        return try {
            future.get(30, TimeUnit.SECONDS)
        } catch (_: Exception) {
            cache.getInfo(name)
        }
    }

    fun requestDetailsIfNeeded(name: String, callback: (PubPackage) -> Unit) {
        val cache = PubPackageCacheService.getInstance()

        if (!cache.shouldRefetchDetails(name)) {
            val info = cache.getInfo(name)
            if (info != null && info.latestVersion != null)
                return // fresh enough
        }

        fetchDetails(name, callback)
    }

    private fun fetchDetails(name: String, callback: (PubPackage) -> Unit) {
        // Check if request is already in-flight
        val existingRequest = inFlightRequests[name]
        if (existingRequest != null && existingRequest.isActive) {
            // Attach to existing request
            scope.launch {
                try {
                    val result = existingRequest.await()
                    if (result != null) {
                        ApplicationManager.getApplication().invokeLater {
                            callback(result)
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Failed to await existing request for $name", e)
                }
            }
            return
        }

        // Create new deferred request
        val deferred = scope.async {
            try {
                val detailsDeferred = async {
                    try {
                        HttpRequests.request("https://pub.dev/api/packages/$name")
                            .connectTimeout(5000)
                            .readTimeout(10000)
                            .connect { it.readString() }
                    } catch (e: Exception) {
                        log.warn("Failed to fetch package details for $name", e)
                        null
                    }
                }

                val scoreDeferred = async {
                    try {
                        HttpRequests.request("https://pub.dev/api/packages/$name/score")
                            .connectTimeout(5000)
                            .readTimeout(10000)
                            .connect { it.readString() }
                    } catch (e: Exception) {
                        log.warn("Failed to fetch package score for $name", e)
                        null
                    }
                }

                val details = detailsDeferred.await()
                val score = scoreDeferred.await()

                // If either request failed, don't proceed
                if (details == null || score == null) {
                    return@async null
                }

                val jsonDetails = JsonParser.parseString(details).asJsonObject
                val latestDetails = jsonDetails["latest"].asJsonObject
                val latestVersion = latestDetails["version"].asString
                val pubspecObj = latestDetails["pubspec"].asJsonObject
                val description = pubspecObj["description"]?.asString
                val repositoryUrl = pubspecObj["repository"]?.asString
                val homepageUrl = pubspecObj["homepage"]?.asString
                val versions = jsonDetails.getAsJsonArray("versions")
                    ?.map { it.asJsonObject["version"].asString }
                    ?.toList()

                println("=========================")
                println("$versions")
                println("=========================")

                val jsonScore = JsonParser.parseString(score).asJsonObject
                val likes = jsonScore["likeCount"]?.asInt ?: 0
                val pubPoints = jsonScore["grantedPoints"]?.asInt ?: 0

                val tags = jsonScore.getAsJsonArray("tags")?.map { it.asString }

                println("=========================")
                println("$tags")
                println("=========================")

                PubPackage(
                    name,
                    latestVersion,
                    versions,
                    description,
                    likes,
                    pubPoints,
                    tags,
                    repositoryUrl,
                    homepageUrl,
                )
            } catch (e: Exception) {
                log.warn("Failed to fetch details for $name", e)
                null
            } finally {
                // Clean up in-flight request
                inFlightRequests.remove(name)
            }
        }

        inFlightRequests[name] = deferred

        // Launch coroutine to handle result
        scope.launch {
            try {
                val result = deferred.await()
                if (result != null) {
                    ApplicationManager.getApplication().invokeLater {
                        callback(result)
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to fetch details for $name", e)
            }
        }
    }
}