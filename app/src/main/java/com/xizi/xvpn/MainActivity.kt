package com.xizi.xvpn

import android.net.VpnService
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.lang.Exception
import java.net.Socket
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var button: Button

    var socket: Socket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button = findViewById(R.id.button)
        button.setOnClickListener {
            createTcpConnection()
        }
    }

    fun createTcpConnection() {
        Thread {
            try {
                socket = Socket("10.23.103.134", 8080)
//                socket = Socket("35.236.153.210", 8080)
//                var reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
//                var writer = PrintWriter(socket!!.getOutputStream())
                var r = socket!!.getInputStream()
                var w = socket!!.getOutputStream()
                w.write(int32ByteArray(Prefs.IPLoop))
                w.flush()
//                var buffer = ByteBuffer.allocate(4)
//                buffer.putInt(Prefs.IPLoop)
                var buf = ByteArray(1500)
                var count = r.read(buf, 0, 16)
                print(buf)
            } catch (e: Exception) {
                print(e)
            }
        }.start()


        return
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
