@file:OptIn(ExperimentalMaterial3Api::class)

package com.teneeduu.jo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.teneeduu.jo.photos.LibraryAccess
import com.teneeduu.jo.photos.PhotoSlideshowViewModel
import com.teneeduu.jo.photos.PhotoSource
import java.io.File

private val Intervals = listOf(15 to "15 秒", 30 to "30 秒", 60 to "1 分钟", 300 to "5 分钟")

@Composable
fun PhotoSettings(slideshow: PhotoSlideshowViewModel) {
    val context = LocalContext.current
    val settings by slideshow.settings.collectAsStateWithLifecycle()
    val access by slideshow.access.collectAsStateWithLifecycle()
    val available by slideshow.available.collectAsStateWithLifecycle()
    val joPhotos by slideshow.joPhotos.collectAsStateWithLifecycle()
    var askedAndDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        askedAndDenied = results.values.none { it }
        slideshow.refresh()
    }

    // The system photo picker needs no permission at all.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        if (uris.isNotEmpty()) slideshow.importPhotos(uris)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("背景照片", style = MaterialTheme.typography.titleLarge)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("用照片当背景", Modifier.weight(1f))
            Switch(checked = settings.enabled, onCheckedChange = slideshow::setEnabled)
        }

        Text("照片从哪来", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PhotoSource.entries.forEachIndexed { index, source ->
                SegmentedButton(
                    selected = settings.source == source,
                    onClick = { slideshow.setSource(source) },
                    shape = SegmentedButtonDefaults.itemShape(index, PhotoSource.entries.size),
                ) {
                    Text(source.label)
                }
            }
        }
        Hint(explain(settings.source, available))

        if (settings.source != PhotoSource.Jo) {
            when (access) {
                LibraryAccess.None -> {
                    OutlinedButton(onClick = { permissionLauncher.launch(slideshow.permissionsToRequest) }) {
                        Text("允许访问相册")
                    }
                    if (askedAndDenied) {
                        Hint("系统没弹窗、或者你之前点过拒绝的话，得去 Jo 的应用信息 → 权限 → 照片和视频里手动打开。")
                        TextButton(onClick = { openAppDetails(context) }) { Text("打开 Jo 的应用信息") }
                    }
                }
                LibraryAccess.Partial ->
                    Hint("现在只允许了部分照片，Jo 只能看到你当时选的那几张。想用更多，去 Jo 的应用信息 → 权限 → 照片和视频改成「始终允许全部」。")
                LibraryAccess.Full -> Unit
            }
        }

        Text("多久换一张", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Intervals.forEachIndexed { index, (seconds, label) ->
                SegmentedButton(
                    selected = settings.intervalSeconds == seconds,
                    onClick = { slideshow.setInterval(seconds) },
                    shape = SegmentedButtonDefaults.itemShape(index, Intervals.size),
                ) {
                    Text(label)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Jo 相册 · ${joPhotos.size}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Text("添加照片")
            }
        }
        if (joPhotos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(joPhotos, key = { it.name }) { file ->
                    Thumbnail(file, onDelete = { slideshow.deletePhoto(file) })
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(file: File, onDelete: () -> Unit) {
    Box {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "删除这张", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

private fun explain(source: PhotoSource, available: Int): String = when (source) {
    PhotoSource.Camera ->
        "只抽手机相机拍的照片（DCIM/Camera），截图、微信存的图、下载的广告图都混不进来。现在有 $available 张。"
    PhotoSource.Library ->
        "从整个相册随机抽，也包括截图和其他 App 存进相册的图。现在有 $available 张。"
    PhotoSource.Jo ->
        "只显示你挑进 Jo 的照片，完全不读系统相册，也不需要任何权限。现在有 $available 张。"
}

private fun openAppDetails(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
