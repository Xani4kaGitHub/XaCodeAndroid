package com.xanichka.xacode.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.xanichka.xacode.MainActivity
import com.xanichka.xacode.R

class AgentForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "XaCode работает" }
        startForeground(WORK_NOTIFICATION_ID, workingNotification(this, title))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val WORK_CHANNEL = "agent_work"
        private const val DONE_CHANNEL = "agent_done"
        private const val WORK_NOTIFICATION_ID = 9101
        private const val DONE_NOTIFICATION_ID = 9102

        fun start(context: Context, title: String) {
            createChannels(context)
            ContextCompat.startForegroundService(
                context,
                Intent(context, AgentForegroundService::class.java).putExtra(EXTRA_TITLE, title)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }

        fun notifyFinished(context: Context, title: String, success: Boolean) {
            createChannels(context)
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val text = if (success) "Задача завершена — результат уже в чате" else "Задача остановлена с ошибкой — откройте чат"
            val notification = NotificationCompat.Builder(context, DONE_CHANNEL)
                .setSmallIcon(R.drawable.xacode_logo)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(contentIntent(context))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(DONE_NOTIFICATION_ID + title.hashCode(), notification)
        }

        private fun workingNotification(context: Context, title: String) = NotificationCompat.Builder(context, WORK_CHANNEL)
            .setSmallIcon(R.drawable.xacode_logo)
            .setContentTitle(title)
            .setContentText("Агент работает в фоне. Можно открыть другое приложение.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(WORK_CHANNEL, "Работа агента", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(DONE_CHANNEL, "Завершение задач", NotificationManager.IMPORTANCE_HIGH))
        }
    }
}
