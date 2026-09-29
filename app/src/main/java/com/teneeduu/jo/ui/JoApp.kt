package com.teneeduu.jo.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teneeduu.jo.music.MusicViewModel
import com.teneeduu.jo.update.UpdateState
import com.teneeduu.jo.update.UpdateViewModel

// The same system colours the iOS home screen cycles through.
private val Rainbow = listOf(
    Color(0xFFFF2D55), Color(0xFFAF52DE), Color(0xFF5856D6), Color(0xFF007AFF),
    Color(0xFF32ADE6), Color(0xFF34C759), Color(0xFFFFCC00), Color(0xFFFF9500),
    Color(0xFFFF3B30), Color(0xFFFF2D55),
)

@Composable
fun JoApp(
    openSettingsAtLaunch: Boolean = false,
    updates: UpdateViewModel = viewModel(),
    music: MusicViewModel = viewModel(),
) {
    val updateState by updates.state.collectAsStateWithLifecycle()
    val nowPlaying by music.nowPlaying.collectAsStateWithLifecycle()
    val tracks by music.tracks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSettings by rememberSaveable { mutableStateOf(openSettingsAtLaunch) }

    LaunchedEffect(Unit) { updates.check(quiet = true) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            RainbowBackdrop()

            Greeting(Modifier.align(Alignment.Center))

            UpdateBanner(
                state = updateState,
                onInstall = { updates.install(context) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp),
            )

            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = Color.White)
            }

            GlassPill(
                onClick = music::toggle,
                enabled = tracks.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp),
            ) {
                Icon(
                    if (nowPlaying.isPlaying) JoIcons.Waveform else JoIcons.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(if (nowPlaying.isPlaying) "正在播放" else "播放音乐", fontSize = 15.sp)
            }
        }

        if (showSettings) {
            SettingsSheet(music = music, updates = updates, onDismiss = { showSettings = false })
        }
    }
}

@Composable
private fun GlassPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.18f),
        contentColor = Color.White,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun RainbowBackdrop() {
    val transition = rememberInfiniteTransition(label = "backdrop")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing)),
        label = "angle",
    )
    // Rotating a sweep gradient stands in for SwiftUI's hueRotation.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationZ = angle
                scaleX = 1.8f
                scaleY = 1.8f
                alpha = 0.6f
            }
            .blur(70.dp)
            .background(Brush.sweepGradient(Rainbow)),
    )
}

@Composable
private fun Greeting(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "greeting")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing)),
        label = "shift",
    )
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Text(
        text = "Hello,\nworld!",
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        style = TextStyle(
            brush = flowingRainbow(shift),
            fontSize = 64.sp,
            lineHeight = 70.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 6f), 18f),
        ),
    )
}

/**
 * A rainbow exactly as wide as the text, like SwiftUI's leading-to-trailing
 * gradient. It slides two widths per cycle: with mirror tiling that is one
 * full period, so the loop restarts without a visible jump.
 */
private fun flowingRainbow(shift: Float): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val start = -2f * size.width * shift
        return LinearGradientShader(
            from = Offset(start, 0f),
            to = Offset(start + size.width, 0f),
            colors = Rainbow,
            tileMode = TileMode.Mirror,
        )
    }
}

@Composable
private fun UpdateBanner(state: UpdateState, onInstall: () -> Unit, modifier: Modifier) {
    val text = when (state) {
        is UpdateState.Available -> "发现新版本 build ${state.build} · 点这里更新"
        is UpdateState.Downloading -> "正在下载 build ${state.build} · ${(state.progress * 100).toInt()}%"
        is UpdateState.NeedsInstallPermission -> "允许 Jo 安装应用后，点这里继续更新"
        else -> return
    }
    GlassPill(onClick = onInstall, enabled = state !is UpdateState.Downloading, modifier = modifier) {
        Text(text, fontSize = 14.sp)
    }
}
