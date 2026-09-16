package com.arms.responder.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arms.responder.MainActivity
import com.arms.responder.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ARMSFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope =
        CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        serviceScope.launch {
            try {
                val repository =
                    com.arms.responder.data.ARMSRepository()

                repository.registerFcmToken(token)
            } catch (_: Exception) {
                // Token registration will be retried when Firebase
                // provides a new token.
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title =
            message.notification?.title ?: "ARMS Dispatch"

        val body =
            message.notification?.body ?: "New dispatch received."

        val channel = NotificationChannel(
            "arms_dispatch",
            "ARMS Dispatch",
            NotificationManager.IMPORTANCE_HIGH
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        val incidentId =
            message.data["incident_id"]

        val intent = Intent(
            this,
            MainActivity::class.java
        ).apply {
            putExtra(
                MainActivity.EXTRA_INCIDENT_ID,
                incidentId
            )
            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            NotificationCompat.Builder(this, "arms_dispatch")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat
            .from(this)
            .notify(
                System.currentTimeMillis().toInt(),
                notification
            )
    }
}