package com.example.crayfarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Owns notification channels and local pond-alert notifications. */
object CrayFarmNotificationHelper {

    private const val CHANNEL_ID = "crayfarm_pond_alerts"
    private const val CHANNEL_NAME = "Pond Alerts"
    private const val CHANNEL_DESCRIPTION =
        "Local notifications for unsafe CrayFarm pond readings"

    fun createNotificationChannel(
        context: Context
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
        }

        val notificationManager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        notificationManager.createNotificationChannel(
            channel
        )
    }

    fun showAlertNotification(
        context: Context,
        alertId: String,
        farmId: String,
        pondId: String,
        pondName: String,
        title: String,
        message: String
    ) {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra("openAlerts", true)
            putExtra("farmId", farmId)
            putExtra("pondId", pondId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alertId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val safeTitle = title.ifBlank {
            "Pond Alert"
        }

        val safeMessage = message.ifBlank {
            "An unsafe pond reading was detected."
        }

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_notification
                )
                .setContentTitle(
                    "$safeTitle — $pondName"
                )
                .setContentText(safeMessage)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(safeMessage)
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_ALARM
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()

        NotificationManagerCompat
            .from(context)
            .notify(
                alertId.hashCode(),
                notification
            )
    }
}
