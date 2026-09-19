package org.akuatech.ksupatcher.network

import org.akuatech.ksupatcher.data.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class GitHubReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val tagName: String,
)

class GitHubReleaseRepository(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchLatestTag(owner: String, repo: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/${owner}/${repo}/releases/latest"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "ksupatcher")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Release check failed: ${response.code}")
                }
                val body = response.body?.string() ?: error("Empty response")
                val release = JSONObject(body)
                release.optString("tag_name").ifBlank {
                    error("Missing tag name")
                }
            }
        }
    }

    /**
     * Find an exact asset name in the newest releases that contain it.
     *
     * Some forks publish a manager/ksud-only release before publishing the
     * matching LKM. Looking through a small window of releases keeps the
     * downloader usable without guessing a tag or constructing a URL for an
     * asset that is not present.
     */
    suspend fun fetchLatestAsset(
        owner: String,
        repo: String,
        assetNames: List<String>,
        perPage: Int = 20,
    ): Result<GitHubReleaseAsset> = withContext(Dispatchers.IO) {
        runCatching {
            require(assetNames.isNotEmpty()) { "No release asset candidates were supplied" }
            val url = "https://api.github.com/repos/${owner}/${repo}/releases?per_page=$perPage"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "ksupatcher")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Release asset lookup failed: ${response.code}")
                }
                val body = response.body?.string() ?: error("Empty response")
                val releases = JSONArray(body)
                for (releaseIndex in 0 until releases.length()) {
                    val release = releases.optJSONObject(releaseIndex) ?: continue
                    val assets = release.optJSONArray("assets") ?: continue
                    val tag = release.optString("tag_name").ifBlank { "unknown" }
                    for (candidate in assetNames) {
                        for (assetIndex in 0 until assets.length()) {
                            val asset = assets.optJSONObject(assetIndex) ?: continue
                            if (asset.optString("name") == candidate) {
                                val downloadUrl = asset.optString("browser_download_url")
                                    .ifBlank { error("Release asset has no download URL") }
                                return@use GitHubReleaseAsset(candidate, downloadUrl, tag)
                            }
                        }
                    }
                }
                error("No matching release asset found in $owner/$repo")
            }
        }
    }

    suspend fun fetchAppUpdateInfo(owner: String, repo: String, currentBuildHash: String): Result<AppUpdateInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://api.github.com/repos/${owner}/${repo}/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "ksupatcher")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Release check failed: ${response.code}")
                    }
                    val body = response.body?.string() ?: error("Empty response")
                    val release = JSONObject(body)
                    val latestReleaseHash = release.optString("tag_name").ifBlank {
                        error("Missing tag name")
                    }
                    val assets = release.optJSONArray("assets") ?: JSONArray()
                    val apkAsset = findAsset(assets, ".apk")
                    val checksumAsset = findAsset(assets, ".apk.sha256")
                    AppUpdateInfo(
                        currentBuildHash = currentBuildHash,
                        latestReleaseHash = latestReleaseHash,
                        isUpdateAvailable = latestReleaseHash != currentBuildHash,
                        apkAssetName = apkAsset?.optString("name")?.ifBlank { null },
                        apkDownloadUrl = apkAsset?.optString("browser_download_url")?.ifBlank { null },
                        checksumDownloadUrl = checksumAsset?.optString("browser_download_url")?.ifBlank { null },
                        releaseUrl = release.optString("html_url").ifBlank { null },
                        publishedAt = release.optString("published_at").ifBlank { null },
                        notes = release.optString("body").trim().ifBlank { null }
                    )
                }
            }
        }

    private fun findAsset(assets: JSONArray, suffix: String): JSONObject? {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(suffix)) {
                return asset
            }
        }
        return null
    }
}
