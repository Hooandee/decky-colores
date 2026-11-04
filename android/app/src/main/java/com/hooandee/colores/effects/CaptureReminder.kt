package com.hooandee.colores.effects

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.hooandee.colores.MainActivity
import com.hooandee.colores.R
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.report.ColoresLog

object CaptureReminder {
    const val EXTRA_RESUME_CAPTURE = "com.hooandee.colores.extra.RESUME_CAPTURE"
    private const val CHANNEL_ID = "colores_capture_reminder"
    private const val NOTIFICATION_ID = 4102

    fun post(
        context: Context,
        mode: AppMode,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) {
            ColoresLog.state("capture_reminder", "notifications_blocked")
            return
        }
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.capture_reminder_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_RESUME_CAPTURE, mode.name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val text = if (mode == AppMode.AUDIO) R.string.capture_reminder_audio else R.string.capture_reminder_ambient
        val notification =
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_colores)
                .setContentTitle(context.getString(R.string.capture_reminder_title))
                .setContentText(context.getString(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onSuccess { ColoresLog.state("capture_reminder", mode.name) }
            .onFailure { ColoresLog.w("CaptureReminder", "reminder not posted", it) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        ColoresLog.state("capture_reminder", null)
    }
}
