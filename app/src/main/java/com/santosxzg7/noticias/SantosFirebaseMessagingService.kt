package com.santosxzg7.noticias

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import android.provider.Settings
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class SantosFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d("SANTOS_FCM", "Token nativo recebido: $token")

        val dados = hashMapOf(
            "token" to token,
            "tipo" to "android_app",
            "atualizadoEm" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("subscribers")
            .document(token)
            .set(dados)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val titulo = message.notification?.title ?: "SANTOSXZG7 Notícias"
        val corpo = message.notification?.body ?: "Nova publicação disponível"
        val link = message.data["url"] ?: "https://santosxzg7-noticias.netlify.app/"

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("url", link)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canalId = "santosxzg7_noticias"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                canalId,
                "Notícias SANTOSXZG7",
                NotificationManager.IMPORTANCE_HIGH
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(canal)
        }

        val notificacao = NotificationCompat.Builder(this, canalId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notificacao)
    }
}