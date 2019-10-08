package com.xizi.xvpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
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
                // tcp 写入示例
                // w.write("hello world".toByteArray())
                w.flush()
                // tcp 读取
//                var buffer = ByteBuffer.allocate(4)
//                buffer.putInt(Prefs.IPLoop)
//                var buf = ByteArray(1500)
//                var count = r.read(buf, 0, 16)

                // vpn configration
                var service = VpnService()
                var builder = service.Builder()
                builder.addAddress("10.0.0.9", 32)
                builder.addRoute("0.0.0.0",0)
                builder.setMtu(1500)
                builder.addDnsServer("8.8.8.8")
                builder.addSearchDomain("127.0.0.1")
                // Create the intent to "configure" the connection (just start VpnClient.kt).
//                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
//                var intent = VpnService.prepare(applicationContext)
//
//                startActivityForResult(intent, 0)
//                startService(intent)


//                var pendingIntent = PendingIntent.getActivity(
//                    this,
//                    0,
//                    Intent(this, VpnClient::class.java),
//                    PendingIntent.FLAG_UPDATE_CURRENT
//                )
//                builder.setSession("XVPN").setConfigureIntent(pendingIntent)



                Log.i("tag", "($intent)")
                print("fffffff1111")
                print(intent)
                var vpnInterface: ParcelFileDescriptor = builder.establish()

                // Packets to be sent are queued in this input stream.
                val `in` = FileInputStream(vpnInterface!!.getFileDescriptor())

                // Packets received need to be written to this output stream.
                val out = FileOutputStream(vpnInterface!!.getFileDescriptor())
                while (true) {
                    print(`in`.readBytes())
                }
            } catch (e: Exception) {
                print(e)
            }
        }.start()


        return
    }

    override fun startActivityForResult(intent: Intent?, requestCode: Int) {
        if (intent == null) {
            super.startActivityForResult(Intent(), requestCode)
            return
        }
        super.startActivityForResult(intent, requestCode)
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
