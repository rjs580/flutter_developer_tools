package dev.rutvik.flutter_developer_tools.pubspec.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import dev.rutvik.flutter_developer_tools.pubspec.models.PubPackage
import dev.rutvik.flutter_developer_tools.pubspec.models.PubSearchResponse
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class PubDevApiService {
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val packageDetailsCache = ConcurrentHashMap<String, PubPackage>()
    private val listeners = ConcurrentHashMap.newKeySet<(PubPackage) -> Unit>()

    companion object {
        private val LOG = Logger.getInstance(PubDevApiService::class.java)
        private const val CACHE_DURATION_MS = 300_000L // 5 minutes cache duration
        private const val PUB_API_BASE = "https://pub.dev/api"
    }

    data class CacheEntry(val data: List<PubPackage>, val timestamp: Long)

    fun searchPackages(query: String): List<PubPackage> {
        if (query.length < 2) return emptyList()

        val cacheKey = query.lowercase()
        cache[cacheKey]?.let { entry ->
            if (System.currentTimeMillis() - entry.timestamp < CACHE_DURATION_MS) {
                return entry.data
            }
        }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$PUB_API_BASE/search?q=$encodedQuery")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            val response = connection.inputStream.bufferedReader().readText()
            val searchResponse = gson.fromJson(response, PubSearchResponse::class.java)

            val packages = searchResponse.packages.take(20).mapNotNull { result ->
                fetchPackageDetails(result.`package`)
            }.sortedWith(compareByDescending<PubPackage> { it.isFlutterFavorite }
                .thenByDescending { it.likes })

            cache[cacheKey] = CacheEntry(packages, System.currentTimeMillis())
            return packages

        } catch (e: Exception) {
            LOG.warn("Failed to search packages: ${e.message}")
            return emptyList()
        }
    }

    private fun fetchPackageDetails(packageName: String): PubPackage? {
        packageDetailsCache[packageName]?.let { return it }

        try {
            val url = URL("$PUB_API_BASE/packages/$packageName")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2000
            connection.readTimeout = 2000

            val response = connection.inputStream.bufferedReader().readText()
            val json = gson.fromJson(response, JsonObject::class.java)

            val latest = json.getAsJsonObject("latest")
            val version = latest.getAsJsonPrimitive("version").asString
            val pubspec = latest.getAsJsonObject("pubspec")
            val description = pubspec.getAsJsonPrimitive("description")?.asString

            // Fetch score data
            val scoreUrl = URL("$PUB_API_BASE/packages/$packageName/score")
            val scoreConnection = scoreUrl.openConnection() as HttpURLConnection
            scoreConnection.requestMethod = "GET"
            scoreConnection.connectTimeout = 2000
            scoreConnection.readTimeout = 2000

            val scoreResponse = scoreConnection.inputStream.bufferedReader().readText()
            val scoreJson = gson.fromJson(scoreResponse, JsonObject::class.java)

            val likeCount = scoreJson.getAsJsonPrimitive("likeCount")?.asInt ?: 0
            val grantedPoints = scoreJson.getAsJsonPrimitive("grantedPoints")?.asInt ?: 0
            val tags = scoreJson.getAsJsonArray("tags")?.map { it.asString } ?: emptyList()

            val isFlutterFavorite = tags.contains("is:flutter-favorite")

            val pkg = PubPackage(
                name = packageName,
                latest = version,
                description = description,
                isFlutterFavorite = isFlutterFavorite,
                likes = likeCount,
                pubPoints = grantedPoints
            )

            packageDetailsCache[packageName] = pkg
            return pkg

        } catch (e: Exception) {
            LOG.warn("Failed to fetch package details for $packageName: ${e.message}")
            return null
        }
    }
}