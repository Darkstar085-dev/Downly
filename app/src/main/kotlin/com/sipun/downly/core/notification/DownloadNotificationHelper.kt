package com.sipun.downly.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sipun.downly.MainActivity
import com.sipun.downly.R
import com.sipun.downly.core.service.DownloadWorkManager

object DownloadNotificationHelper {
    private const val CHANNEL_ID = "media_downloads"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Media Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress and status of video and audio downloads"
                    setShowBadge(false)
                }
            )
        }
    }

    fun showProgress(
        context: Context,
        notificationId: Int,
        title: String,
        percent: Int,
        detail: String
    ) {
        createChannel(context)
        DownloadWorkManager.notifyProgress(notificationId, percent, detail)
        if (!canNotify(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percent.coerceIn(0, 100), percent == 0)

        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, builder.build())
    }

    fun showComplete(
        context: Context,
        notificationId: Int,
        title: String
    ) {
        if (!canNotify(context)) return
        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, builder.build())
    }

    fun showFailed(
        context: Context,
        notificationId: Int,
        title: String,
        error: String
    ) {
        if (!canNotify(context)) return
        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download Failed")
            .setContentText("$title: $error")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)

        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, builder.build())
    }

    fun cancel(context: Context, notificationId: Int) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).cancel(notificationId)
        }
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}
