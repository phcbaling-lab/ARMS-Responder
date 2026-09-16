package com.arms.responder.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.arms.responder.data.ARMSRepository
import com.arms.responder.R
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private lateinit var fused: FusedLocationProviderClient

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository = ARMSRepository()

    private val callback = object : LocationCallback() {

        override fun onLocationResult(result: LocationResult) {

            for (location in result.locations) {

                val latitude = location.latitude
                val longitude = location.longitude

                val accuracy =
                    if (location.hasAccuracy()) {
                        location.accuracy.toDouble()
                    } else {
                        null
                    }

                val speedKmh =
                    if (location.hasSpeed()) {
                        (location.speed * 3.6).toDouble()
                    } else {
                        null
                    }

                val heading =
                    if (location.hasBearing()) {
                        location.bearing.toDouble()
                    } else {
                        null
                    }

                serviceScope.launch {

                    repository.sendLocation(
                        latitude = latitude,
                        longitude = longitude,
                        accuracyMeters = accuracy,
                        speedKmh = speedKmh,
                        heading = heading
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        fused = LocationServices.getFusedLocationProviderClient(this)

        val channel = NotificationChannel(
            "arms_location",
            "ARMS Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(
            this,
            "arms_location"
        )
            .setContentTitle("ARMS location tracking")
            .setContentText(
                "Ambulance location is being shared with dispatch."
            )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        ServiceCompat.startForeground(
            this,
            1001,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    fun startUpdates() {

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10_000L
        )
            .setMinUpdateIntervalMillis(5_000L)
            .build()

        fused.requestLocationUpdates(
            request,
            callback,
            mainLooper
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        startUpdates()

        return START_STICKY
    }

    override fun onDestroy() {

        fused.removeLocationUpdates(callback)

        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
