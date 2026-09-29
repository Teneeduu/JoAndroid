package com.teneeduu.jo.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teneeduu.jo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val build: Int) : UpdateState
    data class Downloading(val build: Int, val progress: Float) : UpdateState
    data class NeedsInstallPermission(val build: Int) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Unlike iOS, Android lets an app hand a newer APK of itself to the system
 * installer, so Jo updates without a computer. The system only accepts it when
 * the new APK is signed with the same key and has a larger versionCode.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentBuild: Int = BuildConfig.VERSION_CODE

    private var latest: Release? = null

    private data class Release(val build: Int, val apkUrl: String)

    /** [quiet] is for the launch-time check: stay silent unless there is something new. */
    fun check(quiet: Boolean = false) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return

        _state.value = UpdateState.Checking
        viewModelScope.launch {
            _state.value = runCatching { fetchLatest() }.fold(
                onSuccess = { release ->
                    latest = release
                    when {
                        release != null && release.build > currentBuild -> UpdateState.Available(release.build)
                        quiet -> UpdateState.Idle
                        release == null -> UpdateState.Failed("最新版本里没找到 $APK_NAME")
                        else -> UpdateState.UpToDate
                    }
                },
                onFailure = { error ->
                    if (quiet) UpdateState.Idle else UpdateState.Failed(error.message ?: "网络错误")
                },
            )
        }
    }

    fun install(context: Context) {
        val release = latest ?: return
        if (release.build <= currentBuild || _state.value is UpdateState.Downloading) return

        // Android asks once per app whether it may install other APKs.
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission(release.build)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }

        viewModelScope.launch {
            _state.value = UpdateState.Downloading(release.build, 0f)
            runCatching { download(release) }
                .onSuccess { apk ->
                    // If the user backs out of the system dialog they can tap again.
                    _state.value = UpdateState.Available(release.build)
                    launchInstaller(context, apk)
                }
                .onFailure { error ->
                    _state.value = UpdateState.Failed(error.message ?: "下载失败")
                }
        }
    }

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode != 200) error("GitHub 返回 ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val build = json.getString("tag_name").removePrefix("build-").toIntOrNull()
                ?: return@withContext null

            val assets = json.getJSONArray("assets")
            (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name") == APK_NAME }
                ?.let { Release(build, it.getString("browser_download_url")) }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "Jo-${release.build}.apk")

        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode != 200) error("下载失败：${connection.responseCode}")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            _state.value = UpdateState.Downloading(release.build, copied.toFloat() / total)
                        }
                    }
                }
            }
            target
        } finally {
            connection.disconnect()
        }
    }

    private fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        const val APK_NAME = "Jo.apk"
        const val LATEST_RELEASE_API = "https://api.github.com/repos/Teneeduu/JoAndroid/releases/latest"
    }
}
