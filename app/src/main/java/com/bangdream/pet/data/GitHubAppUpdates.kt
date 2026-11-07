package com.bangdream.pet.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal data class AppUpdate(val version: String, val downloadUrl: String)

internal class GitHubAppUpdates(private val client: OkHttpClient = HTTP) {
    suspend fun check(installedVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPOSITORY/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub release check returned HTTP ${response.code}")
            val body = response.body?.source() ?: throw IOException("Missing release response")
            if (body.request(MAX_RESPONSE_BYTES + 1)) throw IOException("Release response is too large")
            selectAppUpdate(JSONObject(body.readUtf8()), installedVersion)
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 512L * 1024
        val HTTP = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    }
}

private const val REPOSITORY = "telecom888/Live2dOnAndroid"

internal fun selectAppUpdate(release: JSONObject, installedVersion: String): AppUpdate? {
    if (release.getBoolean("draft") || release.getBoolean("prerelease")) return null
    val tag = release.getString("tag_name")
    val version = Version.parse(tag)
    if (version <= Version.parse(installedVersion)) return null

    val name = "Live2dOnAndroid-$version-release-arm64.apk"
    val expectedUrl = "https://github.com/$REPOSITORY/releases/download/$tag/$name"
    val assets = release.getJSONArray("assets")
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: continue
        val url = asset.optString("browser_download_url")
        if (asset.optString("name") == name && asset.optString("state") == "uploaded" &&
            asset.optLong("size") > 0 && url == expectedUrl
        ) return AppUpdate(version.toString(), url)
    }
    throw IOException("The release has no matching APK")
}

private data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int = compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("v?([0-9]+)\\.([0-9]+)\\.([0-9]+)")

        fun parse(text: String): Version {
            val match = pattern.matchEntire(text) ?: throw IOException("Invalid release version")
            val (major, minor, patch) = match.destructured
            return Version(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
