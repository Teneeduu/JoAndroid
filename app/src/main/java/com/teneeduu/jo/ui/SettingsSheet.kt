package com.teneeduu.jo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teneeduu.jo.music.MusicViewModel
import com.teneeduu.jo.music.NowPlaying
import com.teneeduu.jo.music.Track
import com.teneeduu.jo.photos.PhotoSlideshowViewModel
import com.teneeduu.jo.update.UpdateState
import com.teneeduu.jo.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    slideshow: PhotoSlideshowViewModel,
    music: MusicViewModel,
    updates: UpdateViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val tracks by music.tracks.collectAsStateWithLifecycle()
    val nowPlaying by music.nowPlaying.collectAsStateWithLifecycle()
    val updateState by updates.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }

    // The system file picker needs no storage permission.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) music.import(uris)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PhotoSettings(slideshow) }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item { Text("音乐", style = MaterialTheme.typography.titleLarge) }

            item { PlayerControls(nowPlaying, hasTracks = tracks.isNotEmpty(), music = music) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("歌曲 · ${tracks.size}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { editing = !editing }) {
                        Text(if (editing) "完成" else "编辑顺序")
                    }
                    IconButton(onClick = { picker.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加歌曲")
                    }
                }
            }

            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(track, index, tracks.size, nowPlaying, editing, music)
            }

            item {
                Hint("从电脑加歌：用数据线把歌拷进手机（比如「音乐」文件夹），再点右上角 ＋ 选中。支持 mp3、m4a、wav、flac、ogg。")
            }

            item { KeepAliveCard() }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item {
                AboutSection(
                    state = updateState,
                    currentBuild = updates.currentBuild,
                    onCheck = { updates.check() },
                    onInstall = { updates.install(context) },
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(nowPlaying: NowPlaying, hasTracks: Boolean, music: MusicViewModel) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            nowPlaying.title ?: "未播放",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            IconButton(onClick = music::stop, enabled = nowPlaying.trackId != null) {
                Icon(JoIcons.Stop, contentDescription = "停止")
            }
            IconButton(onClick = music::previous, enabled = hasTracks) {
                Icon(JoIcons.SkipPrevious, contentDescription = "上一首")
            }
            FilledIconButton(onClick = music::toggle, enabled = hasTracks, modifier = Modifier.size(64.dp)) {
                Icon(
                    if (nowPlaying.isPlaying) JoIcons.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (nowPlaying.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = music::next, enabled = hasTracks) {
                Icon(JoIcons.SkipNext, contentDescription = "下一首")
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    index: Int,
    count: Int,
    nowPlaying: NowPlaying,
    editing: Boolean,
    music: MusicViewModel,
) {
    val isCurrent = nowPlaying.trackId == track.id
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !editing) { music.play(track) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (isCurrent && nowPlaying.isPlaying) JoIcons.Waveform else JoIcons.MusicNote,
            contentDescription = null,
            tint = if (isCurrent) Color(0xFFFF2D55) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(track.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)

        if (editing) {
            IconButton(onClick = { music.move(index, -1) }, enabled = index > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
            }
            IconButton(onClick = { music.move(index, 1) }, enabled = index < count - 1) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
            }
            if (!track.bundled) {
                IconButton(onClick = { music.remove(track) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        } else if (track.bundled) {
            Text("内置", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Xiaomi kills background apps aggressively; this is the one-time fix. */
@Composable
private fun KeepAliveCard() {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("防止音乐被系统停掉", style = MaterialTheme.typography.titleSmall)
            Text(
                "小米会清理后台。在 Jo 的应用信息里把「省电策略」设为「无限制」，并打开「自启动」，锁屏后音乐就不会断。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            }) {
                Text("打开 Jo 的应用信息")
            }
        }
    }
}

@Composable
private fun AboutSection(state: UpdateState, currentBuild: Int, onCheck: () -> Unit, onInstall: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("关于 Jo", style = MaterialTheme.typography.titleLarge)

        Row(Modifier.fillMaxWidth()) {
            Text("当前版本")
            Spacer(Modifier.weight(1f))
            Text("build $currentBuild", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        statusText(state)?.let { status ->
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }

        when (state) {
            is UpdateState.Available ->
                Button(onClick = onInstall) { Text("下载并安装 build ${state.build}") }

            is UpdateState.NeedsInstallPermission ->
                Button(onClick = onInstall) { Text("我已允许，继续更新") }

            is UpdateState.Downloading ->
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())

            else ->
                OutlinedButton(onClick = onCheck, enabled = state !is UpdateState.Checking) {
                    Text(if (state is UpdateState.Checking) "正在检查…" else "检查更新")
                }
        }

        Hint(
            "有新版本时 Jo 会自己从 GitHub 下载，下载完系统弹出安装确认，点「安装」即可，数据会保留。" +
                "第一次更新时需要允许 Jo「安装未知应用」。",
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun statusText(state: UpdateState): String? = when (state) {
    is UpdateState.UpToDate -> "已经是最新版本"
    is UpdateState.Available -> "有新版本：build ${state.build}"
    is UpdateState.Downloading -> "正在下载 ${(state.progress * 100).toInt()}%"
    is UpdateState.NeedsInstallPermission -> "请在刚打开的设置页里允许 Jo 安装应用，然后回来继续"
    is UpdateState.Failed -> "检查失败：${state.message}"
    else -> null
}
