package com.example.gpsproject

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*


class TrackingService : Service() {

    private var firstTimer: Timer? = null
    private var secondTimer: Timer? = null

    private var gpsTimer: Timer? = null


    // Tells whether the timer should be initialzed for notification
//    private var shouldStartTimer = true

//    private var firstNotification = true


    private var isTracking = false
//    var toast :Toast? = null


    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    val listOfLocationPoint = mutableListOf<LocationPoint>()

    private val locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult ?: return
            for (location in locationResult.locations) {
                location?.let {
                    if (isTracking) {

                        if (firstTimer == null) {
                            if (TrackingData.firstTimerShouldInit) {
                                firstTimer = Timer()
                                firstTimer?.schedule(object : TimerTask() {
                                    override fun run() {
                                        firstTimer = null
                                        doOnFirstTimerCompletion()
                                    }
                                }, minutesToMilliSeconds(10.0))
                            }
                        }

                        if (secondTimer == null) {
                            secondTimer = Timer()
                            secondTimer?.schedule(object : TimerTask() {
                                override fun run() {
                                    secondTimer = null
                                    doOnSecondTimerCompletion()
                                }
                            }, minutesToMilliSeconds(10.0))
                        }


                        listOfLocationPoint.add(
                            LocationPoint(
                                latitude = it.latitude,
                                longitude = it.longitude
                            )
                        )

                        resetTimers()
                        TrackingData.dismissDialog?.invoke()

                    }

                    //TODO (Uncomment)
//                    if (DetectedActivityData.activityData?.type == DetectedActivity.IN_VEHICLE ||
//                        DetectedActivityData.activityData?.type == DetectedActivity.ON_BICYCLE ||
//                        DetectedActivityData.activityData?.type == DetectedActivity.ON_FOOT ||
//                        DetectedActivityData.activityData?.type == DetectedActivity.RUNNING ||
//                        DetectedActivityData.activityData?.type == DetectedActivity.WALKING
//                    ) {
                    val lastLocation = SessionUiModel.LocationPointUiModel(
                        latitude = it.latitude,
                        longitude = it.longitude
                    )
                    TrackingData.locationCallback?.invoke(lastLocation, it.bearing, it.accuracy)
//                     }
                }
            }
        }
    }

    private fun doOnFirstTimerCompletion() {

        TrackingData.shouldDisplayFirstDialog = true
        TrackingData.firstTimerShouldInit = false
        firstTimer?.cancel()
        firstTimer = null

        with(NotificationManagerCompat.from(this)) {
            cancel(INT_NOTIFICATION_ID_NOT_TRACKING)
        }


    }

    private fun doOnSecondTimerCompletion() {

        TrackingData.shouldDisplaySecondDialog = true

        with(NotificationManagerCompat.from(this)) {
            cancel(INT_NOTIFICATION_ID_NOT_TRACKING)
        }

        val text = "Tracking has stopped. To continue please restart tracking"


    }

//    private fun doOnTimerComplete() {
//
//        TrackingData.firstTimerShouldInit = false
//        firstTimer?.cancel()
//        firstTimer = null
//
//        with(NotificationManagerCompat.from(this)) {
//            cancel(INT_NOTIFICATION_ID_NOT_TRACKING)
//        }
//
//        val minuteText = if (secondTimerDuration < 2) "minute" else "minutes"
//
//        val notificationText =
//            if (firstNotification) "You have been idle for ${secondTimerDuration.roundToInt()} $minuteText. Do you want to continue tracking?"
//            else "Tracking has stopped. To continue please restart tracking"
//
//
//        if (!TrackingData.homeFragmentVisible) {
//
//            val intent = Intent(this, MainActivity::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
//            intent.putExtra(PENDING_INTENT_TRACKING_NOTIFICATION, "")
//
//            (application as? CruzeApplication)?.let {
//                if (it.appInBackground) {
//                    intent.putExtra(PENDING_INTENT_TRACKING_NOTIFICATION_APP_IN_BG, true)
//                }
//            }
//
//            val noPendingIntent = PendingIntent.getActivity(
//                this,
//                System.currentTimeMillis().toInt(),
//                intent,
//                PendingIntent.FLAG_CANCEL_CURRENT
//            )
//
//
//            val yesIntent = Intent(
//                this,
//                YesBroadcastReceiver::class.java
//            )
//            intent.action = "notification_cancelled"
//            val yesPendingIntent = PendingIntent.getBroadcast(
//                this,
//                System.currentTimeMillis().toInt(),
//                yesIntent,
//                PendingIntent.FLAG_CANCEL_CURRENT
//            )
//
//
//            val notification =
//                NotificationCompat.Builder(this, Channels.ID_TRACKING_NOTIFICATIONS).apply {
//                    setContentTitle("Cruze4Cash")
//                    setContentText(notificationText)
//
//                    setSmallIcon(R.drawable.logo)
//                    priority = NotificationCompat.PRIORITY_HIGH
//                    setAutoCancel(true)
//                    setStyle(
//                        NotificationCompat.BigTextStyle()
//                            .bigText(notificationText)
//                    )
//
//                    if (firstNotification) {
////                        setOngoing(true)
//                        addAction(-1, "Yes", yesPendingIntent)
//                        addAction(-1, "No", noPendingIntent)
//                    } else {
//                        intent.putExtra(PENDING_INTENT_TRACKING_ALREADY_DISABLED, true)
//                        val pendingIntent = PendingIntent.getActivity(
//                            this@TrackingService,
//                            System.currentTimeMillis().toInt(),
//                            intent,
//                            PendingIntent.FLAG_CANCEL_CURRENT
//                        )
//                        setContentIntent(pendingIntent)
//                        TrackingData.disableTrackingFromService?.invoke(true)
//
//                    }
//
//                }.build()
//
//            with(NotificationManagerCompat.from(this)) {
//                // notificationId is a unique int for each notification that you must define
//                notify(INT_NOTIFICATION_ID_NOT_TRACKING, notification)
//            }
//        } else {
//
//            if (firstNotification) {
//                TrackingData.notTracking?.invoke(notificationText, false)
//            } else {
//                TrackingData.notTracking?.invoke(notificationText, true)
//            }
//        }
//        firstNotification = false
//        secondTimerDuration = secondDuration
//    }

    private fun resetTimers() {
        with(NotificationManagerCompat.from(this@TrackingService)) {
            cancel(INT_NOTIFICATION_ID_NOT_TRACKING)
        }
        TrackingData.shouldDisplayFirstDialog = false
        TrackingData.firstTimerShouldInit = true
        firstTimer?.cancel()
        firstTimer = null
        secondTimer?.cancel()
        secondTimer = null
//        firstNotification = true

    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest()
            .setSmallestDisplacement(5f)
            .setInterval(LOCATION_INTERVAL_IN_MILLISECONDS)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

        TrackingData.trackingRunningCallback = {
            isTracking = it
            // Tracking is off then therefore create session
            resetTimers()

        }

        TrackingData.trackingNotiYesClicked = {
            TrackingData.shouldDisplayFirstDialog = false
        }

        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceRunning = true
        showTrackingNotification()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopLocationUpdates()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        firstTimer?.cancel()
        secondTimer?.cancel()

        gpsTimer?.cancel()

        TrackingData.shouldDisplaySecondDialog = false
        GlobalScope.launch {
            stopLocationUpdates()
            stopForeground(true)
            super.onDestroy()
        }
    }

    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun minutesToMilliSeconds(minutes: Double): Long {
        return minutes.times(60000).toLong()
    }

    companion object {

        var isServiceRunning = false

        //        const val LOCATION_INTERVAL_IN_MILLISECONDS = 4000L
        const val LOCATION_INTERVAL_IN_MILLISECONDS = 250L
        private const val INT_NOTIFICATION_ID = 99999
        const val INT_NOTIFICATION_ID_NOT_TRACKING = 72439
        const val CHUNK_SIZE = 2000


        const val PENDING_INTENT_TRACKING_NOTIFICATION = "pendingIntentTrackingNotification"
        const val PENDING_INTENT_TRACKING_NOTIFICATION_APP_IN_BG = "pendingIntentAppInBackground"
        const val PENDING_INTENT_TRACKING_ALREADY_DISABLED = "pendingIntentTrackingAlreadyDisabled"

    }

    private fun showTrackingNotification() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )

        val notification =
            NotificationCompat.Builder(this, Channels.ID_SILENT_NOTIFICATIONS).apply {
                setContentTitle("Cruze4Cash")
                setContentText("")
                setSmallIcon(R.mipmap.ic_launcher)
                priority = NotificationCompat.PRIORITY_LOW
                setContentIntent(pendingIntent)
            }.build()

        startForeground(INT_NOTIFICATION_ID, notification)
    }

}
