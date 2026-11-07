package com.bangdream.pet.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubAppUpdatesTest {
    private val apkName = "Live2dOnAndroid-1.1.10-release-arm64.apk"
    private val download = "https://github.com/telecom888/Live2dOnAndroid/releases/download/v1.1.10/$apkName"

    @Test
    fun checkUsesTheLatestReleaseJsonAndReturnsTheExactApkDownloadUrl() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("GET", request.method)
            assertEquals("https://api.github.com/repos/telecom888/Live2dOnAndroid/releases/latest", request.url.toString())
            assertEquals("application/vnd.github+json", request.header("Accept"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(release().toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        assertEquals(AppUpdate("1.1.10", download), GitHubAppUpdates(http).check("1.1.9"))
    }

    @Test
    fun skipsInstalledOlderDraftAndPrereleaseVersions() {
        assertNull(selectAppUpdate(release(), "1.1.10"))
        assertNull(selectAppUpdate(release(), "1.2.0"))
        assertNull(selectAppUpdate(release().put("draft", true), "1.1.9"))
        assertNull(selectAppUpdate(release().put("prerelease", true), "1.1.9"))
    }

    @Test
    fun rejectsMissingOrUntrustedDownloadAssets() {
        assertThrows(IOException::class.java) {
            selectAppUpdate(release().put("assets", JSONArray()), "1.1.9")
        }
        assertThrows(IOException::class.java) {
            selectAppUpdate(release(download.replace("github.com/", "github.com.example.org/")), "1.1.9")
        }
    }

    private fun release(url: String = download): JSONObject = JSONObject()
        .put("tag_name", "v1.1.10").put("draft", false).put("prerelease", false)
        .put("assets", JSONArray().put(JSONObject().put("name", "build-provenance.json"))
            .put(JSONObject().put("name", apkName).put("state", "uploaded").put("size", 1).put("browser_download_url", url)))
}
