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

class HelloService : Service() {

    override fun onCreate() { }

    private fun createNotificationChannel() : String {
        val name = "Pingbattery"
        val descriptionText = "foreground service notification channel for pingbattery"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("pingbattery", name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        return "pingbattery"
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun createNotification(channel_id: String): Notification {
        // Create the notification
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        return NotificationCompat.Builder(this, channel_id)
            .setContentTitle("Active")
            .setContentText("Switching $socketId on at $turnOnAt% and off at $turnOffAt%")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun getBatteryPercentage(batteryStatus: Intent?): Float? {
        return batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }
    }

    private fun isCharging(batteryStatus: Intent?): Boolean {
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING  || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getBatteryStatus():Intent? {
        return IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { intentfilter: IntentFilter ->
            this.registerReceiver(null, intentfilter)
        }
    }

    private fun checkBattery(){
        val batteryStatus = getBatteryStatus()
        val percentage = getBatteryPercentage(batteryStatus)
        val charging = isCharging(batteryStatus)
        Log.i("Battery", "charging: $charging | level: $percentage% ")
        if(percentage != null && (charging && percentage >= turnOffAt)) {
            req("$host/$socketId/off", "POST")
        } else if (percentage != null && (!charging && percentage <= turnOnAt)) {
            req("$host/$socketId/on", "POST")
        }
    }

    private val client = OkHttpClient()
    // send empty request to URL
    private fun req(url: String, method:String) {
        Log.i("Request", "Request invoked: $method -> '$url'")
        val request = Request.Builder()
            .method(method, "".toRequestBody())
            .url(url)
            .build()

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

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show()

        // get params from intent
        turnOnAt = intent.getIntExtra("on", 40)
        turnOffAt = intent.getIntExtra("off", 90)
        batteryCheckInterval = intent.getIntExtra("interval", 5000).toLong()
        socketId = intent.getStringExtra("socket").toString()
        host = intent.getStringExtra("host").toString()

        startForeground(1, createNotification(createNotificationChannel()))

        batteryCheckThread = Thread(Runnable {
            try {
                while (true){
                    checkBattery()
                    Thread.sleep(batteryCheckInterval)
                }
            } catch (e: InterruptedException) {
                Log.i("batteryCheckThread", "Interrupted")
            }
        })

        batteryCheckThread?.start()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        batteryCheckThread?.interrupt()
        Toast.makeText(this, "Service terminated", Toast.LENGTH_SHORT).show()
    }
}

