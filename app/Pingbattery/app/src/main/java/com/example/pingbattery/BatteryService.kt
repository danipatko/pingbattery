package com.example.pingbattery

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class BatteryService : Service() {

    override fun onCreate() { }

    // creates a notification channel for foreground service notification. returns the ID of the notification channel
    private fun createNotificationChannel() : String {
        val channel = NotificationChannel("pingbattery", "Pingbattery", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Foreground service notification channel for pingbattery"
        }
        // register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        return "pingbattery"
    }

    // create a notification containing the actual socket ID, and percentages to switch on/off
    @SuppressLint("UnspecifiedImmutableFlag")
    private fun createNotification(channel_id: String): Notification {
        // set pending intent
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        // build notification
        return NotificationCompat.Builder(this, channel_id)
            .setContentTitle("Active")
            .setContentText("Switching $socketId on at $turnOnAt% and off at $turnOffAt%")
            .setSmallIcon(R.drawable.pb_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    // get the percentage from the batterystatus sticky intent
    private fun getBatteryPercentage(batteryStatus: Intent?): Float? {
        return batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }
    }

    // check if device is charging front intent
    private fun isCharging(batteryStatus: Intent?): Boolean {
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING  || status == BatteryManager.BATTERY_STATUS_FULL
    }

    // get ACTION_BATTERY_CHANGED sticky intent
    private fun getBatteryStatus():Intent? {
        return IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { intentfilter: IntentFilter ->
            this.registerReceiver(null, intentfilter)
        }
    }

    private fun checkBattery(){
        val batteryStatus = getBatteryStatus()
        val percentage = getBatteryPercentage(batteryStatus)
        val charging = isCharging(batteryStatus)
        // Log.i("Battery", "charging: $charging | level: $percentage% ") // DEBUG
        // if we're charging and above limit, turn socket off
        if(percentage != null && (charging && percentage >= turnOffAt)) {
            req("$host/$socketId/off", "POST")
            // if we're not charging and percentage is under the lower limit, turn on socket
        } else if (percentage != null && (!charging && percentage <= turnOnAt)) {
            req("$host/$socketId/on", "POST")
        }
    }

    /// NETWORKING
    // note: to send requests to http protocol urls, you must define a network security config in app/res/xml and permit cleartext traffic
    // requests are made using the okhttp library
    private val client = OkHttpClient()
    // send an empty request to url
    private fun req(url: String, method:String) {
        Log.i("Request", "Request invoked: $method -> '$url'")
        val request = Request.Builder()
            .method(method, "".toRequestBody())
            .url(url)
            .build()
        // empty callback
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) = println(response.body?.string())
        })
    }

    private var batteryCheckThread:Thread? = null
    private var turnOnAt:Int = 40
    private var turnOffAt:Int = 90
    private var batteryCheckInterval:Long = 5000
    private var socketId:String = "1000cc4a9e"    // id of socket to ping
    private var host:String = "http://192.168.2.171:8000"   // address to ping

    // called after intent.startService -> onCreate
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // get params from intent
        turnOnAt = intent.getIntExtra("on", 40)
        turnOffAt = intent.getIntExtra("off", 90)
        batteryCheckInterval = intent.getIntExtra("interval", 5000).toLong()
        socketId = intent.getStringExtra("socket").toString()
        host = intent.getStringExtra("host").toString()
        // start the service on foreground
        // user is able to see if app is running + android is less likely to kill the process when freeing resources
        startForeground(1, createNotification(createNotificationChannel()))
        // create new background thread, that gets interrupted when service is destroyed (otherwise it would run infinitely)
        batteryCheckThread = Thread(Runnable {
            try {
                while (true){
                    checkBattery()
                    Thread.sleep(batteryCheckInterval)
                }
            } catch (e: InterruptedException) {
                Log.i("batteryCheckThread", "Interrupted")  // DEBUG
            }
        })
        // start the thread
        batteryCheckThread?.start()
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show()
        // if we get killed here, restart
        return START_STICKY
    }

    // we don't provide binding, so return null
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // called after intent.stopService (or when android kills the process)
    override fun onDestroy() {
        batteryCheckThread?.interrupt()     // stop thread
        Toast.makeText(this, "Service terminated", Toast.LENGTH_SHORT).show()
    }
}

