package com.bangdream.pet.ui.settings

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bangdream.pet.I18n
import com.bangdream.pet.SETTINGS_PREFS
import com.bangdream.pet.data.AppUpdate
import com.bangdream.pet.data.GitHubAppUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class AppUpdatePrompt(val release: AppUpdate, val allowSkip: Boolean)

internal class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val releases = GitHubAppUpdates()
    private var triedAutomatically = false

    @Suppress("DEPRECATION")
    val installedVersion = application.packageManager.getPackageInfo(application.packageName, 0).versionName.orEmpty()

    var autoCheckEnabled by mutableStateOf(preferences.getBoolean(AUTO_CHECK, true))
        private set
    var checking by mutableStateOf(false)
        private set
    var prompt by mutableStateOf<AppUpdatePrompt?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    fun setAutomaticCheck(enabled: Boolean) {
        autoCheckEnabled = enabled
        preferences.edit().putBoolean(AUTO_CHECK, enabled).apply()
    }

    fun checkAutomatically() {
        if (triedAutomatically) return
        triedAutomatically = true
        if (autoCheckEnabled) check(manual = false)
    }

    fun checkManually() = check(manual = true)

    private fun check(manual: Boolean) {
        if (checking) return
        checking = true
        viewModelScope.launch {
            try {
                val latest = releases.check(installedVersion)
                if (manual) {
                    if (latest != null) {
                        prompt = AppUpdatePrompt(latest, allowSkip = false)
                    } else {
                        notice = "update_no_new_version"
                    }
                } else if (autoCheckEnabled && latest != null && latest.version !in ignoredVersions()) {
                    prompt = AppUpdatePrompt(latest, allowSkip = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (manual) notice = "update_check_failed"
            } finally {
                checking = false
            }
        }
    }

    fun dismissUpdate() { prompt = null }
    fun dismissNotice() { notice = null }

    fun ignoreVersion() {
        val current = prompt ?: return
        if (!current.allowSkip) return
        val version = current.release.version
        preferences.edit().putStringSet(IGNORED_VERSIONS, ignoredVersions() + version).apply()
        dismissUpdate()
    }

    private fun ignoredVersions(): Set<String> = preferences.getStringSet(IGNORED_VERSIONS, emptySet()).orEmpty()

    fun openDownload(context: Context) {
        val url = prompt?.release?.downloadUrl ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            // Match the default HTTPS browser without resolving a GitHub App Link.
            selector = Intent(Intent.ACTION_VIEW, Uri.parse("https:"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
        }
        dismissUpdate()
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            notice = "update_browser_unavailable"
        }
    }

    private companion object {
        const val AUTO_CHECK = "github_app_update_auto_check"
        const val IGNORED_VERSIONS = "github_app_update_ignored_versions"
    }
}

@Composable
internal fun AppUpdateDialogs(updates: AppUpdateViewModel) {
    val context = LocalContext.current
    updates.prompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = updates::dismissUpdate,
            title = { Text(I18n.t("update_available", prompt.release.version)) },
            text = {
                Column {
                    Text(I18n.t("update_download_prompt", updates.installedVersion))
                    if (prompt.allowSkip) {
                        TextButton(onClick = updates::ignoreVersion) { Text(I18n.t("update_ignore_version")) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { updates.openDownload(context) }) { Text(I18n.t("update_download")) }
            },
            dismissButton = { TextButton(onClick = updates::dismissUpdate) { Text(I18n.t("update_later")) } },
        )
    } ?: updates.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = updates::dismissNotice,
            title = { Text(I18n.t("update_check")) },
            text = { Text(I18n.t(notice)) },
            confirmButton = { TextButton(onClick = updates::dismissNotice) { Text(I18n.t("confirm")) } },
        )
    }
}
