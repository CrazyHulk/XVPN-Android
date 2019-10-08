package com.xizi.xvpn

import android.app.*
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Handler
import android.os.Message
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Pair
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.FileInputStream
import java.io.FileOutputStream

import java.io.IOException
import java.lang.Exception
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MyVpnService : VpnService(), Handler.Callback {

    var socket: Socket? = null

    private var mHandler: Handler? = null

    private val mConnectingThread = AtomicReference<Thread>()
    private val mConnection = AtomicReference<Connection>()

    private val mNextConnectionId = AtomicInteger(1)

    private var mConfigureIntent: PendingIntent? = null

    private class Connection(thread: Thread, pfd: ParcelFileDescriptor) : Pair<Thread, ParcelFileDescriptor>(thread, pfd)

    override
    fun onCreate() {
        // The handler is only used to show messages.
        if (mHandler == null) {
            mHandler = Handler(this)
        }

        // Create the intent to "configure" the connection (just start VpnClient.kt).
        mConfigureIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT)
    }


    override
    fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && ACTION_DISCONNECT.equals(intent!!.getAction())) {
            disconnect()
            return START_NOT_STICKY
        } else {
            connect()
            return START_STICKY
        }
    }

    override
    fun onDestroy() {
        disconnect()
    }

    override
    fun handleMessage(message: Message): Boolean {
        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show()
        if (message.what !== R.string.disconnected) {
            updateForegroundNotification(message.what)
        }
        return true
    }

    private fun connect() {
        // Become a foreground service. Background services can be VPN services too, but they can
        // be killed by background check before getting a chance to receive onRevoke().
        updateForegroundNotification(R.string.connecting)
        mHandler!!.sendEmptyMessage(R.string.connecting)

        // Extract information from the shared preferences.
        val prefs = getSharedPreferences(VpnClient.Prefs.NAME, MODE_PRIVATE)
        val server = prefs.getString(VpnClient.Prefs.SERVER_ADDRESS, "")
        val secret = prefs.getString(VpnClient.Prefs.SHARED_SECRET, "")!!.toByteArray()
        val allow = prefs.getBoolean(VpnClient.Prefs.ALLOW, true)
        val packages = prefs.getStringSet(VpnClient.Prefs.PACKAGES, Collections.emptySet())
        val port = prefs.getInt(VpnClient.Prefs.SERVER_PORT, 0)
        val proxyHost = prefs.getString(VpnClient.Prefs.PROXY_HOSTNAME, "")
        val proxyPort = prefs.getInt(VpnClient.Prefs.PROXY_PORT, 0)

        createTcpConnection()

//        startConnection(VpnConnection(
//            this, mNextConnectionId.getAndIncrement(), server!!, port, secret,
//            proxyHost!!, proxyPort, allow, packages!!))
    }

    private fun startConnection(connection: VpnConnection) {
        // Replace any existing connecting thread with the  new one.
        val thread = Thread(connection, "ToyVpnThread")
        setConnectingThread(thread)

        // Handler to mark as connected once onEstablish is called.
        connection.setConfigureIntent(mConfigureIntent!!)
        connection.setFnOnEstablishListener { tunInterface ->
            mHandler!!.sendEmptyMessage(R.string.connected)

            mConnectingThread.compareAndSet(thread, null)
            setConnection(Connection(thread, tunInterface))
        }
//        connection.setOnEstablishListener({ tunInterface ->
//            mHandler!!.sendEmptyMessage(R.string.connected)
//
//            mConnectingThread.compareAndSet(thread, null)
//            setConnection(Connection(thread, tunInterface))
//        })
        thread.start()
    }

    private fun setConnectingThread(thread: Thread?) {
        val oldThread = mConnectingThread.getAndSet(thread)
        if (oldThread != null) {
            oldThread!!.interrupt()
        }
    }

    private fun setConnection(connection: Connection?) {
        val oldConnection = mConnection.getAndSet(connection)
        if (oldConnection != null) {
            try {
                oldConnection!!.first.interrupt()
                oldConnection!!.second.close()
            } catch (e: IOException) {
                Log.e(TAG, "Closing VPN interface", e)
            }

        }
    }

    private fun disconnect() {
        mHandler!!.sendEmptyMessage(R.string.disconnected)
        setConnectingThread(null)
        setConnection(null)
        stopForeground(true)
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
                w.write(int32ByteArray(MainActivity.Prefs.IPLoop))
                // tcp 写入示例
                // w.write("hello world".toByteArray())
                w.flush()
                // tcp 读取
//                var buffer = ByteBuffer.allocate(4)
//                buffer.putInt(Prefs.IPLoop)
//                var buf = ByteArray(1500)
//                var count = r.read(buf, 0, 16)

                // vpn configration
                var builder = this.Builder()
                builder.addAddress("10.0.0.9", 32)
                builder.addRoute("0.0.0.0",0)
                builder.setMtu(1500)
                builder.addDnsServer("8.8.8.8")
                builder.addSearchDomain("127.0.0.1")


//                var pendingIntent = PendingIntent.getActivity(
//                    this,
//                    0,
//                    Intent(this, VpnClient::class.java),
//                    PendingIntent.FLAG_UPDATE_CURRENT
//                )
                builder.setSession("XVPN").setConfigureIntent(mConfigureIntent!!)

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

    private fun updateForegroundNotification(message: Int) {
        val NOTIFICATION_CHANNEL_ID = "ToyVpn"
        val mNotificationManager = getSystemService(
            NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.createNotificationChannel(NotificationChannel(
            NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
            NotificationManager.IMPORTANCE_DEFAULT))
        startForeground(1, Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
//            .setSmallIcon(R.drawable.ic_vpn)
            .setContentText(getString(message))
            .setContentIntent(mConfigureIntent)
            .build())
    }

    companion object {
        private val TAG = VpnService::class.java!!.getSimpleName()

        val ACTION_CONNECT = "com.xizi.xvpn.START"
        val ACTION_DISCONNECT = "com.xizi.xvpn.STOP"

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
