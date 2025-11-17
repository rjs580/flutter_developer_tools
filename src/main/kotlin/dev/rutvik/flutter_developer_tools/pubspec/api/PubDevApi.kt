package dev.rutvik.flutter_developer_tools.pubspec.api

import com.google.gson.JsonParser
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import dev.rutvik.flutter_developer_tools.pubspec.models.PubPackage
import dev.rutvik.flutter_developer_tools.pubspec.services.PubPackageCacheService
import kotlinx.coroutines.*

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
                    val description = latestDetails["pubspec"].asJsonObject["description"].asString

                    val jsonScore = JsonParser.parseString(score).asJsonObject
                    val likes = jsonScore["likeCount"]?.asInt ?: 0
                    val pubPoints = jsonScore["grantedPoints"]?.asInt ?: 0

                    val tags = jsonScore.getAsJsonArray("tags")?.map { it.asString } ?: emptyList()
                    val isFlutterFavorite = tags.contains("is:flutter-favorite")

                    // Invoke callback on EDT to prevent UI threading issues
                    ApplicationManager.getApplication().invokeLater {
                        callback(PubPackage(name, latestVersion, description, isFlutterFavorite, likes, pubPoints))
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to fetch details for $name", e)
            }
        }
    }
}