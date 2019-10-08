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




class MainActivity : Activity() {

    private lateinit var button: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button = findViewById(R.id.button)
        button.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 0)
            } else {
                onActivityResult(0, Activity.RESULT_OK, null)
            }
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


    @Throws(IllegalArgumentException::class)
    private fun configure(parameters: String): ParcelFileDescriptor {

        var builder = VpnService().Builder()
        builder.setMtu(1500)
        builder.addAddress("10.0.0.1", 32)
        builder.addRoute("10.0.0.2", 32)
        builder.addDnsServer("8.8.8.8")
//        builder.addSearchDomain(fields[1])
        // Create a new interface using the builder and save the parameters.
        var vpnInterface: ParcelFileDescriptor
        vpnInterface = builder.establish()
//        synchronized(mService) {
//            vpnInterface = builder.establish()
//            if (mOnEstablishListener != null) {
//                mOnEstablishListener!!.onEstablish(vpnInterface)
//            }
//        }
        Log.i("tag ====", "New interface: ($vpnInterface) ($parameters)")
        return vpnInterface
    }

    object Prefs {
        val IPLoop = 0x00010000
    }


    fun int32ByteArray(value: Int): ByteArray {
        val bytes = ByteArray(4)
        bytes[0] = (value and 0xFFFF).toByte()
        bytes[1] = ((value ushr 8) and 0xFFFF).toByte()
        bytes[2] = ((value ushr 16) and 0xFFFF).toByte()
        bytes[3] = ((value ushr 24) and 0xFFFF).toByte()
        return bytes
    }
}
