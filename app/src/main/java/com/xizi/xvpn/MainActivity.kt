package com.xizi.xvpn

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Button
import androidx.core.content.ContextCompat
import java.io.*
import java.lang.Exception
import java.net.Socket
import java.security.Permission
import java.util.jar.Manifest
import android.net.VpnService
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import androidx.core.app.ActivityCompat


class MainActivity : Activity() {

    private lateinit var button: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button = findViewById(R.id.button)
        button.setOnClickListener {
            requestPermission()

            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                onActivityResult(0, Activity.RESULT_OK, null)
            }

            button.text = "已连接"
        }

    }


    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            var intent = Intent(this, com.xizi.xvpn.MyVpnService::class.java).setAction(MyVpnService.ACTION_CONNECT)
            this.startService(intent)
        }
    }

    fun requestPermission(): Unit {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BIND_VPN_SERVICE)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.BIND_VPN_SERVICE)) {
                Log.i("ff", "111")

            } else {
                var res: Int = 1
                ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.CALL_PHONE),
                    res)
                print(res)
            }

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_NETWORK_STATE)) {
                Log.i("ff", "111")

            }else {
                var res: Int = 1
                ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.ACCESS_NETWORK_STATE),
                    res)
                print(res)
            }

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.INTERNET)) {
                Log.i("ff", "111")
            }else {
                var res: Int = 1
                ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.INTERNET),
                    res)
                print(res)
            }
        }
    }
}
