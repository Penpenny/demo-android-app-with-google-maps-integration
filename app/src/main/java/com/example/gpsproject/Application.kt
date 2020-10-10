package com.example.gpsproject

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.LifecycleObserver
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class Application : Application(), LifecycleObserver {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(applicationContext)
            modules(modules)
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


        // Channel for Silent Group Notifications
        notificationManager.createNotificationChannel(
            NotificationChannel(
                Channels.ID_SILENT_NOTIFICATIONS,
                Channels.NAME_SILENT_NOTIFICATIONS,
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

}
