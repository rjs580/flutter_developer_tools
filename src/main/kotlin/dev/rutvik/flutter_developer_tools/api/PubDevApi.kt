package dev.rutvik.flutter_developer_tools.api

import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import dev.rutvik.flutter_developer_tools.models.PubPackage
import dev.rutvik.flutter_developer_tools.services.PubPackageCacheService
import kotlinx.coroutines.*
import java.util.concurrent.CompletableFuture
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
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                runBlocking {
                    val detailsDeferred = async(Dispatchers.IO) {
                        HttpRequests.request("https://pub.dev/api/packages/$name")
                            .connect { it.readString() }
                    }

                    val scoreDeferred = async(Dispatchers.IO) {
                        HttpRequests.request("https://pub.dev/api/packages/$name/score")
                            .connect { it.readString() }
                    }

                    val details = detailsDeferred.await()
                    val score = scoreDeferred.await()

                    val jsonDetails = JsonParser.parseString(details).asJsonObject
                    val latestDetails = jsonDetails["latest"].asJsonObject
                    val latestVersion = latestDetails["version"].asString
                    val pubspecObj = latestDetails["pubspec"].asJsonObject
                    val description = pubspecObj["description"]?.asString
                    val repositoryUrl = pubspecObj["repository"]?.asString
                    val homepageUrl = pubspecObj["homepage"]?.asString

                    val jsonScore = JsonParser.parseString(score).asJsonObject
                    val likes = jsonScore["likeCount"]?.asInt ?: 0
                    val pubPoints = jsonScore["grantedPoints"]?.asInt ?: 0

                    val tags = jsonScore.getAsJsonArray("tags")?.map { it.asString }

                    // Invoke callback on EDT to prevent UI threading issues
                    ApplicationManager.getApplication().invokeLater {
                        callback(
                            PubPackage(
                                name,
                                latestVersion,
                                description,
                                likes,
                                pubPoints,
                                tags,
                                repositoryUrl,
                                homepageUrl,
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to fetch details for $name", e)
            }
        }
    }
}