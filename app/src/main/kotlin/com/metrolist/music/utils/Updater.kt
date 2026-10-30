/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.music.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val architecture: String,
    val variant: String // "foss" or "gms"
)

object Updater {
    private val client = HttpClient()
    var lastCheckTime = -1L
        private set
    
    private var cachedReleaseInfo: ReleaseInfo? = null
    private var cachedAllReleases: List<ReleaseInfo> = emptyList()
    
    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    private const val GITHUB_API_BASE = "https://api.github.com/repos/CoderBhoid/mercury-music"
    private const val KMP_LATEST_RELEASE_URL = "https://api.github.com/repos/CoderBhoid/mercury-music/releases/latest"
    private const val KMP_APK_NAME = "Mercury-Music-latest.apk"

    /**
     * Compares two version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = v1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = v2.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        
        for (i in 0 until maxLength) {
            val part1 = v1Parts.getOrNull(i) ?: 0
            val part2 = v2Parts.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    /**
     * Checks if the latest version is newer than the current version.
     * Returns true if an update is available (latestVersion > currentVersion)
     */
    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(latestVersion, currentVersion) > 0
    }

    /**
     * Get the current app's architecture and variant
     */
    private fun getCurrentAppVariant(): Pair<String, String> {
        val architecture = BuildConfig.ARCHITECTURE
        val variant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
        return architecture to variant
    }

    /**
     * Parse release assets from GitHub API response
     */
    private fun parseAssets(assetsArray: JSONArray): List<ReleaseAsset> {
        val assets = mutableListOf<ReleaseAsset>()
        
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.getString("name")
            
            // Skip non-APK files
            if (!name.endsWith(".apk")) continue
            
            val downloadUrl = asset.getString("browser_download_url")
            val size = asset.getLong("size")
            
            // Parse architecture and variant from filename
            val (arch, variant) = when {
                name.equals("Mercury.apk", ignoreCase = true) ||
                name.equals("Mercury-Music.apk", ignoreCase = true) ||
                name.equals("Mercury-Music-latest.apk", ignoreCase = true) ||
                name == "Metrolist.apk" -> "universal" to "foss"
                name.contains("Google-Cast", ignoreCase = true) -> "universal" to "gms"
                name.startsWith("app-") && name.endsWith("-release.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-release.apk")
                    arch to "foss"
                }
                name.startsWith("app-") && name.endsWith("-with-Google-Cast.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-with-Google-Cast.apk")
                    arch to "gms"
                }
                else -> "universal" to "foss"
            }
            
            if (arch != null && variant != null) {
                assets.add(ReleaseAsset(name, downloadUrl, size, arch, variant))
            }
        }
        
        return assets
    }

    /**
     * Fetch latest release from GitHub API
     */
    suspend fun getLatestRelease(forceRefresh: Boolean = false): Result<ReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Return cached if available and not forcing refresh
                if (cachedReleaseInfo != null && !forceRefresh) {
                    return@runCatching cachedReleaseInfo
                }
                
                val httpResponse = client.get("$GITHUB_API_BASE/releases/latest")
                if (httpResponse.status.value !in 200..299) {
                    return@runCatching null
                }

                val response = httpResponse.bodyAsText()
                if (response.isBlank() || !response.trim().startsWith("{")) {
                    return@runCatching null
                }

                val json = JSONObject(response)
                if (!json.has("tag_name") || json.isNull("tag_name")) {
                    return@runCatching null
                }
                
                val releaseInfo = ReleaseInfo(
                    tagName = json.getString("tag_name"),
                    versionName = json.optString("name", json.getString("tag_name")),
                    description = json.optString("body", ""),
                    releaseDate = json.optString("published_at", ""),
                    assets = parseAssets(json.optJSONArray("assets") ?: JSONArray())
                )
                
                cachedReleaseInfo = releaseInfo
                lastCheckTime = System.currentTimeMillis()
                releaseInfo
            }
        }

    /**
     * Fetch all releases from GitHub API (paginated)
     */
    suspend fun getAllReleases(forceRefresh: Boolean = false): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedAllReleases.isNotEmpty() && !forceRefresh) {
                    return@runCatching cachedAllReleases
                }
                
                val releases = mutableListOf<ReleaseInfo>()
                var page = 1
                var hasMore = true
                
                while (hasMore && page <= 10) { // Limit to 10 pages
                    val httpResponse = client.get("$GITHUB_API_BASE/releases?page=$page&per_page=30")
                    if (httpResponse.status.value !in 200..299) {
                        hasMore = false
                        break
                    }
                    val response = httpResponse.bodyAsText()
                    if (response.isBlank() || !response.trim().startsWith("[")) {
                        hasMore = false
                        break
                    }
                    val json = JSONArray(response)
                    
                    if (json.length() == 0) {
                        hasMore = false
                        break
                    }
                    
                    for (i in 0 until json.length()) {
                        val releaseObj = json.getJSONObject(i)
                        releases.add(ReleaseInfo(
                            tagName = releaseObj.optString("tag_name", ""),
                            versionName = releaseObj.optString("name", releaseObj.optString("tag_name", "")),
                            description = releaseObj.optString("body", ""),
                            releaseDate = releaseObj.optString("published_at", ""),
                            assets = parseAssets(releaseObj.optJSONArray("assets") ?: JSONArray())
                        ))
                    }
                    
                    page++
                }
                
                cachedAllReleases = releases
                releases
            }
        }

    internal fun parseKmpRelease(response: String): ReleaseInfo? {
        val release = JSONObject(response)
        val assets = parseAssets(release.getJSONArray("assets")).filter { it.name == KMP_APK_NAME }
        val tagName = release.getString("tag_name")

        return ReleaseInfo(
            tagName = tagName,
            versionName = tagName.removePrefix("v"),
            description = release.optString("body").takeUnless { release.isNull("body") }.orEmpty(),
            releaseDate = release.getString("published_at"),
            assets = assets,
        ).takeIf { assets.isNotEmpty() }
    }

    /**
     * Mercury does not track Metrolist KMP releases.
     */
    suspend fun getLatestKmpRelease(): Result<ReleaseInfo?> = Result.success(null)

    /**
     * Get the download URL for the correct app variant
     */
    fun getDownloadUrlForCurrentVariant(releaseInfo: ReleaseInfo): String? {
        val (currentArch, currentVariant) = getCurrentAppVariant()
        
        return releaseInfo.assets
            .find { it.architecture == currentArch && it.variant == currentVariant }
            ?.downloadUrl
    }

    /**
     * Check if update is needed (respects 2-hour cache)
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<ReleaseInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Check if we should fetch (2 hour interval)
                val shouldFetch = forceRefresh || 
                    (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS
                
                if (!shouldFetch && cachedReleaseInfo != null) {
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.BASE_VERSION_NAME,
                        cachedReleaseInfo!!.versionName
                    )
                    return@runCatching cachedReleaseInfo to hasUpdate
                }
                
                val result = getLatestRelease(forceRefresh = true)
                val releaseInfo = result.getOrNull()
                if (releaseInfo != null) {
                    val hasUpdate = isUpdateAvailable(
                        BuildConfig.BASE_VERSION_NAME,
                        releaseInfo.versionName
                    )
                    releaseInfo to hasUpdate
                } else {
                    null to false
                }
            }
        }

    /**
     * Get the latest release info (cached)
     */
    fun getCachedLatestRelease(): ReleaseInfo? = cachedReleaseInfo
}
