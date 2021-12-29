package com.example.pingbattery

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View

import android.annotation.SuppressLint
import android.widget.Button
import android.widget.EditText
import android.app.ActivityManager
import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private var enabled = false
    private var enableButton:Button? = null
    private var intervalNumber:EditText? = null
    private var onNumber:EditText? = null
    private var offNumber:EditText? = null
    private var socketId:EditText? = null
    private var hostText:EditText? = null

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    @SuppressLint("SetTextI18n")
    public fun onEnable (view: View) {
        enabled = !enabled
        if(enabled) {
            Intent(this, HelloService::class.java).also { intent ->
                intent
                    .putExtra("interval", "${intervalNumber?.text}".toInt() * 1000)
                    .putExtra("on", "${onNumber?.text}".toInt())
                    .putExtra("off", "${offNumber?.text}".toInt())
                    .putExtra("socket", "${socketId?.text}")
                    .putExtra("host", "${hostText?.text}")
                startService(intent)
            }
            enableButton?.text = "disable"
        } else {
            Intent(this, HelloService::class.java).also { intent ->
                stopService(intent)
            }
            enableButton?.text = "enable"
        }
    }

    fun forceSwitchOff(view: View) {
        req("${hostText?.text}/${socketId?.text}/off", "POST")
    }

    fun forceSwitchOn(view: View) {
        req("${hostText?.text}/${socketId?.text}/on", "POST")
    }

    fun savePreferences(view: View) {
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putInt("interval", "${intervalNumber?.text}".toInt())
            putInt("off", "${offNumber?.text}".toInt())
            putInt("on", "${onNumber?.text}".toInt())
            putString("socket", "${socketId?.text}")
            putString("host", "${hostText?.text}")

            apply()
        }
        Toast.makeText(this, "Preferences saved!", Toast.LENGTH_SHORT).show()
    }

    private fun loadPreferences(){
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
        intervalNumber?.setText(sharedPref.getInt("interval", 30).toString())
        offNumber?.setText(sharedPref.getInt("off", 90).toString())
        onNumber?.setText(sharedPref.getInt("on", 40).toString())
        socketId?.setText(sharedPref.getString("socket", "1000cc4a9e"))
        hostText?.setText(sharedPref.getString("host", "http://192.168.2.171:8000"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // check if service is already running
        enabled = isServiceRunning(HelloService::class.java)
        // set elements
        enableButton = findViewById<View>(R.id.enable_button) as Button
        intervalNumber = findViewById<View>(R.id.interval_number) as EditText
        offNumber = findViewById<View>(R.id.turn_off_number) as EditText
        onNumber = findViewById<View>(R.id.turn_on_number) as EditText
        socketId = findViewById<View>(R.id.socket_id_text) as EditText
        hostText = findViewById<View>(R.id.host_field) as EditText
        loadPreferences()

        if(enabled)
            enableButton?.text = "disable"

        Log.i("App", "Successfully started!")
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
            override fun onFailure(call: Call, e: IOException) {
                Log.i("REQ", "Failed to invoke request\n$e")
            }
            override fun onResponse(call: Call, response: Response) {
                Log.i("REQ", "Received response: $response")
            }
        })
    }
}