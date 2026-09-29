package com.teneeduu.jo.photos

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class PhotoSource(val label: String) {
    /** DCIM/Camera only — keeps screenshots and images other apps saved out of it. */
    Camera("相机照片"),
    Library("全部照片"),
    /** Photos the user picked into Jo; never touches the system library, needs no permission. */
    Jo("Jo 相册"),
}

enum class LibraryAccess { Full, Partial, None }

data class PhotoSettings(
    val enabled: Boolean = true,
    val source: PhotoSource = PhotoSource.Camera,
    val intervalSeconds: Int = 30,
)

class PhotoSlideshowViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("slideshow", Context.MODE_PRIVATE)
    private val joFolder = File(application.filesDir, "Photos").apply { mkdirs() }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<PhotoSettings> = _settings.asStateFlow()

    private val _photo = MutableStateFlow<ImageBitmap?>(null)
    val photo: StateFlow<ImageBitmap?> = _photo.asStateFlow()

    private val _available = MutableStateFlow(0)
    val available: StateFlow<Int> = _available.asStateFlow()

    private val _access = MutableStateFlow(currentAccess())
    val access: StateFlow<LibraryAccess> = _access.asStateFlow()

    private val _joPhotos = MutableStateFlow<List<File>>(emptyList())
    val joPhotos: StateFlow<List<File>> = _joPhotos.asStateFlow()

    /** What to ask for so Android 14's "select photos" option shows up too. */
    val permissionsToRequest: Array<String> = when {
        Build.VERSION.SDK_INT >= 34 ->
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private var candidates: List<Uri> = emptyList()
    private var lastShown: Uri? = null
    private var loop: Job? = null
    private var visible = false

    fun onVisible() {
        visible = true
        // Permission or camera roll may have changed while Jo was in the background.
        refresh()
    }

    fun onHidden() {
        visible = false
        loop?.cancel()
    }

    fun setEnabled(enabled: Boolean) {
        update(_settings.value.copy(enabled = enabled))
        restartLoop()
    }

    fun setSource(source: PhotoSource) {
        update(_settings.value.copy(source = source))
        _photo.value = null
        lastShown = null
        refresh()
    }

    fun setInterval(seconds: Int) {
        update(_settings.value.copy(intervalSeconds = seconds))
        restartLoop()
    }

    fun refresh() {
        viewModelScope.launch {
            val source = _settings.value.source
            val (uris, jo) = withContext(Dispatchers.IO) { loadCandidates(source) to listJoPhotos() }
            _access.value = currentAccess()
            _joPhotos.value = jo
            candidates = uris
            _available.value = uris.size
            if (uris.isEmpty()) _photo.value = null
            restartLoop()
        }
    }

    fun importPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                uris.forEach { uri ->
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)) ?: "jpg"
                    val target = File(joFolder, "${UUID.randomUUID()}.$extension")
                    // Picker grants are temporary, so keep our own copy (EXIF included).
                    resolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            refresh()
        }
    }

    fun deletePhoto(file: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { file.delete() }
            if (lastShown == Uri.fromFile(file)) _photo.value = null
            refresh()
        }
    }

    private fun restartLoop() {
        loop?.cancel()
        if (!visible || !_settings.value.enabled || candidates.isEmpty()) return
        loop = viewModelScope.launch {
            if (_photo.value == null) showNext()
            while (isActive) {
                delay(_settings.value.intervalSeconds * 1_000L)
                showNext()
            }
        }
    }

    private suspend fun showNext() {
        val pool = candidates
        if (pool.isEmpty()) return

        var index = pool.indices.random()
        if (pool.size > 1 && pool[index] == lastShown) index = (index + 1) % pool.size
        val next = pool[index]

        val app = getApplication<Application>()
        val metrics = app.resources.displayMetrics
        val request = ImageRequest.Builder(app)
            .data(next)
            .size(metrics.widthPixels, metrics.heightPixels)
            .scale(Scale.FILL)
            .allowHardware(false)
            .build()

        val result = SingletonImageLoader.get(app).execute(request) as? SuccessResult ?: return
        lastShown = next
        _photo.value = result.image.toBitmap().asImageBitmap()
    }

    private fun loadCandidates(source: PhotoSource): List<Uri> = when (source) {
        PhotoSource.Jo -> listJoPhotos().map { Uri.fromFile(it) }
        PhotoSource.Camera -> queryLibrary(cameraOnly = true)
        PhotoSource.Library -> queryLibrary(cameraOnly = false)
    }

    private fun queryLibrary(cameraOnly: Boolean): List<Uri> {
        if (currentAccess() == LibraryAccess.None) return emptyList()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = if (cameraOnly) "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?" else null
        val args = if (cameraOnly) arrayOf("Camera") else null

        return getApplication<Application>().contentResolver
            .query(collection, arrayOf(MediaStore.Images.Media._ID), selection, args, null)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                buildList {
                    while (cursor.moveToNext()) add(ContentUris.withAppendedId(collection, cursor.getLong(idColumn)))
                }
            }
            .orEmpty()
    }

    private fun listJoPhotos(): List<File> =
        joFolder.listFiles().orEmpty()
            .filter { it.extension.lowercase() in IMAGE_EXTENSIONS }
            .sortedBy { it.lastModified() }

    private fun currentAccess(): LibraryAccess {
        val app = getApplication<Application>()
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

        return when {
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> LibraryAccess.Full
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> LibraryAccess.Partial
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> LibraryAccess.Full
            else -> LibraryAccess.None
        }
    }

    private fun loadSettings() = PhotoSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        source = runCatching { PhotoSource.valueOf(prefs.getString(KEY_SOURCE, null) ?: "") }.getOrDefault(PhotoSource.Camera),
        intervalSeconds = prefs.getInt(KEY_INTERVAL, 30),
    )

    private fun update(settings: PhotoSettings) {
        _settings.value = settings
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_SOURCE, settings.source.name)
            .putInt(KEY_INTERVAL, settings.intervalSeconds)
            .apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_SOURCE = "source"
        const val KEY_INTERVAL = "interval"
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
    }
}
