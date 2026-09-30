package com.teneeduu.jo.quotes

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.teneeduu.jo.MainActivity
import com.teneeduu.jo.R
import java.time.LocalTime
import java.time.ZonedDateTime

data class ReminderSettings(
    val enabled: Boolean = false,
    val morning: LocalTime = LocalTime.of(8, 0),
    val evening: LocalTime = LocalTime.of(22, 0),
)

/**
 * Pushes a random quote to the notification shade and lock screen every morning
 * and evening. Alarms are one-shot and re-queued after each delivery, after a
 * reboot and after an app update, since Android drops them in those cases.
 */
object QuoteReminders {

    enum class Slot(val id: Int, val title: String) {
        Morning(1, "早安"),
        Evening(2, "晚安"),
    }

    private const val CHANNEL_ID = "daily-quote"
    private const val TEST_ID = 3
    private const val EXTRA_SLOT = "slot"

    fun settings(context: Context): ReminderSettings {
        val prefs = prefs(context)
        return ReminderSettings(
            enabled = prefs.getBoolean("enabled", false),
            morning = LocalTime.ofSecondOfDay(prefs.getInt("morning", 8 * 3600).toLong()),
            evening = LocalTime.ofSecondOfDay(prefs.getInt("evening", 22 * 3600).toLong()),
        )
    }

    fun save(context: Context, settings: ReminderSettings) {
        prefs(context).edit()
            .putBoolean("enabled", settings.enabled)
            .putInt("morning", settings.morning.toSecondOfDay())
            .putInt("evening", settings.evening.toSecondOfDay())
            .apply()
        reschedule(context)
    }

    fun reschedule(context: Context) {
        val settings = settings(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (slot in Slot.entries) {
            val trigger = alarmIntent(context, slot)
            alarms.cancel(trigger)
            if (!settings.enabled) continue
            val time = if (slot == Slot.Morning) settings.morning else settings.evening
            // Inexact but still fires in Doze; exact alarms need a permission
            // Android 14 denies by default, and a few minutes late is fine here.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(time), trigger)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context, slot: Slot) = post(context, slot.title, slot.id)

    fun postTest(context: Context) = post(context, "来一句", TEST_ID)

    private fun post(context: Context, title: String, id: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val quote = QuoteLibrary(context).all().randomOrNull() ?: return
        ensureChannel(context)

        val body = if (quote.source.isBlank()) quote.text else "${quote.text}\n—— ${quote.source}"
        val openJo = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jo)
            .setContentTitle(title)
            .setContentText(quote.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openJo)
            .setAutoCancel(true)
            // A quote isn't private; let the lock screen show the words.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    internal fun slotOf(intent: Intent): Slot? = Slot.entries.getOrNull(intent.getIntExtra(EXTRA_SLOT, -1))

    private fun nextTrigger(time: LocalTime, now: ZonedDateTime = ZonedDateTime.now()): Long {
        var at = now.with(time).withSecond(0).withNano(0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    private fun alarmIntent(context: Context, slot: Slot): PendingIntent = PendingIntent.getBroadcast(
        context,
        slot.id,
        Intent(context, QuoteAlarmReceiver::class.java).putExtra(EXTRA_SLOT, slot.ordinal),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "每日名言", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每天早晚推送一条名言"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences("quotes", Context.MODE_PRIVATE)
}

class QuoteAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = QuoteReminders.slotOf(intent) ?: return
        QuoteReminders.post(context, slot)
        QuoteReminders.reschedule(context)
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            QuoteReminders.reschedule(context)
        }
    }
}
