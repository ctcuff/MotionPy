package com.cameron.motionpy

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.util.Log

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.squareup.picasso.Picasso

class NotificationService : FirebaseMessagingService() {

    private val tag = NotificationService::class.java.simpleName

    companion object {
        const val DATA_MSG_BROADCAST = "DataMessage"
    }

    override fun onMessageReceived(msg: RemoteMessage?) {
        val data = msg?.data ?: return

        if (data.isEmpty()) {
            Log.i(tag, "There was no data to extract")
            return
        }

        val isDataMsg = data["is_data_message"]?.toBoolean() ?: false

        if (isDataMsg) {
            val intent = Intent(DATA_MSG_BROADCAST)
            intent.putExtra("isPaused", data["is_paused"]?.toBoolean())
            intent.putExtra("delay", data["delay"]?.toFloat())
            intent.putExtra("pics_taken", data["pics_taken"]?.toInt())
            sendBroadcast(intent)
            return
        }

        Log.i(tag, data.toString())

        val imgUrl = data["img_url"] ?: ""
        val isError = data["is_error"]?.toBoolean() ?: false
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val builder = NotificationCompat.Builder(this, "MotionPy")
        val notifId = 101

        if (imgUrl.isEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle()
                    .setSummaryText(data["summary"])
                    .bigText(data["body"])
                    .setBigContentTitle(data["title"]))
        }

        builder.setContentText(data["body"])
        builder.setContentTitle(data["title"])
        builder.setShowWhen(true)
        builder.setAutoCancel(true)
        builder.setSmallIcon(R.drawable.ic_eye_white)
        builder.color = resources.getColor(R.color.colorPrimaryDark)

        builder.setLights(
                if (isError) Color.argb(255, 255, 0, 0)
                else Color.argb(255, 0, 87, 75),
                1000, 1000
        )

        // Don't vibrate if the user's device is on silent
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL,
            AudioManager.RINGER_MODE_VIBRATE ->
                // The vibration pattern is {delay, vibrate, sleep, vibrate}
                builder.setVibrate(longArrayOf(0, 250, 250, 250))
            AudioManager.RINGER_MODE_SILENT -> builder.setVibrate(longArrayOf(0))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.priority = NotificationManager.IMPORTANCE_HIGH
        }

        if (imgUrl.isNotEmpty()) {
            val bitMap = Picasso.get().load(imgUrl).get()
            builder.setStyle(NotificationCompat.BigPictureStyle()
                    .setSummaryText(data["summary"])
                    .setBigContentTitle(data["title"])
                    .bigPicture(bitMap))
        }

        notifManager.notify(notifId, builder.build())
    }

    override fun onNewToken(token: String) {
        Log.i(tag, "Token: $token")
    }
}
