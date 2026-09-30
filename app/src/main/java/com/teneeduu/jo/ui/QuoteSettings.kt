package com.teneeduu.jo.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teneeduu.jo.quotes.QuotesViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** What to do once notification permission is sorted out. */
private enum class AfterPermission { Enable, Test }

@Composable
fun QuoteSettings(quotes: QuotesViewModel) {
    val context = LocalContext.current
    val all by quotes.quotes.collectAsStateWithLifecycle()
    val custom by quotes.custom.collectAsStateWithLifecycle()
    val reminders by quotes.reminders.collectAsStateWithLifecycle()
    val canNotify by quotes.canNotify.collectAsStateWithLifecycle()
    var pending by rememberSaveable { mutableStateOf<AfterPermission?>(null) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        quotes.refreshPermission()
        deniedOnce = !granted
        if (granted) {
            when (pending) {
                AfterPermission.Enable -> quotes.setRemindersEnabled(true)
                AfterPermission.Test -> quotes.sendTest()
                null -> Unit
            }
        }
        pending = null
    }

    fun withPermission(then: AfterPermission, action: () -> Unit) {
        if (canNotify || Build.VERSION.SDK_INT < 33) {
            action()
        } else {
            pending = then
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("每日名言", style = MaterialTheme.typography.titleLarge)

        Row(Modifier.fillMaxWidth()) {
            Text("名言总数")
            Spacer(Modifier.weight(1f))
            Text("${all.size} 条", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("每天早晚推送一条", Modifier.weight(1f))
            Switch(
                checked = reminders.enabled,
                onCheckedChange = { on ->
                    if (on) withPermission(AfterPermission.Enable) { quotes.setRemindersEnabled(true) }
                    else quotes.setRemindersEnabled(false)
                },
            )
        }

        if (reminders.enabled) {
            TimeRow("早上", reminders.morning) { pickTime(context, reminders.morning, quotes::setMorning) }
            TimeRow("晚上", reminders.evening) { pickTime(context, reminders.evening, quotes::setEvening) }
        }

        OutlinedButton(onClick = { withPermission(AfterPermission.Test) { quotes.sendTest() } }) {
            Text("发一条试试")
        }

        if (deniedOnce && !canNotify) {
            Hint("通知权限没开。去系统设置里允许 Jo 发通知，推送才能到。")
            TextButton(onClick = { openNotificationSettings(context) }) { Text("打开 Jo 的通知设置") }
        } else {
            Hint("推送大约在设定时间到达，可能晚几分钟，这是系统为了省电。锁屏上如果只显示「Jo 的通知」看不到内容，去系统的通知设置里把锁屏通知改成显示内容。")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我的句子 · ${custom.size}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { adding = true }) { Text("添加") }
        }
        custom.forEach { quote ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(quote.text)
                    if (quote.source.isNotBlank()) {
                        Text("—— ${quote.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { quotes.removeCustom(quote) }) {
                    Icon(Icons.Filled.Close, contentDescription = "删除这句")
                }
            }
        }
    }

    if (adding) {
        AddQuoteDialog(
            onAdd = { line ->
                quotes.addCustom(line)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun TimeRow(label: String, time: LocalTime, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(time.format(ClockFormat)) }
    }
}

@Composable
private fun AddQuoteDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加一句自己的") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("句子 —— 出处") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint("出处写在 —— 后面，没有出处就只写句子。")
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun pickTime(context: Context, current: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        current.hour,
        current.minute,
        true,
    ).show()
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
