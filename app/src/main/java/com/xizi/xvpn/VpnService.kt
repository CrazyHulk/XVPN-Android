package com.xizi.xvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Handler
import android.os.Message
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Pair
import android.widget.Toast

import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class VpnService : VpnService(), Handler.Callback {

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
        mConfigureIntent = PendingIntent.getActivity(this, 0, Intent(this, VpnClient::class.java),
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
        startConnection(VpnConnection(
            this, mNextConnectionId.getAndIncrement(), server!!, port, secret,
            proxyHost!!, proxyPort, allow, packages!!))
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
}
