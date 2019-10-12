package com.xizi.xvpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Message
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Pair
import android.widget.Toast
import java.io.*

import java.lang.Exception
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.String as String1

class MyVpnService : VpnService(), Handler.Callback {

    private var mHandler: Handler? = null

    private val mConnectingThread = AtomicReference<Thread>()
    private val mConnection = AtomicReference<Connection>()

    private val mNextConnectionId = AtomicInteger(1)

    private var mConfigureIntent: PendingIntent? = null

    private class Connection(thread: Thread, pfd: ParcelFileDescriptor) :
        Pair<Thread, ParcelFileDescriptor>(thread, pfd)

    fun loopAddressNIO(connection: SocketChannel): Pair<String1, String1> {
        var byteBuffer = ByteBuffer.allocate(12)
        val r = connection.read(byteBuffer)

        if (byteBuffer.array().copyOfRange(0, 4).toInt() != Prefs.IPLoop) {
            return Pair("", "")
        }

        return Pair(
            byteBuffer.array().copyOfRange(4, 8).toIP(),
            byteBuffer.array().copyOfRange(8, 12).toIP()
        )
    }

    fun startNIOSocket(): Unit {
        Thread {
            try {
                var address = InetSocketAddress("35.236.153.210", 8080)
//                var address = InetSocketAddress("10.23.103.134", 8080)
                SocketChannel.open(address).use { conn ->
                    val ips = loopAddressNIO(conn)
                    if (!this.protect(conn.socket())) {
                        throw IllegalStateException("Cannot protect the tunnel")
                    }
                    conn.configureBlocking(false)
                    // vpn configration
                    var builder = this.Builder()

//                builder.addAddress(ips.second, 32)
                    builder.addAddress(ips.second, 32)
//                    builder.addAddress("10.0.0.8", 32)

                    builder.addRoute("0.0.0.0", 0)
                    builder.setMtu(1500)
                    builder.addDnsServer("8.8.8.8")

//                    builder.setSession("10.0.0.8").setConfigureIntent(mConfigureIntent!!)

                    builder.setSession(ips.second).setConfigureIntent(mConfigureIntent!!)


                    var vpnInterface: ParcelFileDescriptor = builder.establish()

                    // Packets to be sent are queued in this input stream.
                    val tunReader = FileInputStream(vpnInterface!!.getFileDescriptor())

                    // Packets received need to be written to this output stream.
                    val tunWriter = FileOutputStream(vpnInterface!!.getFileDescriptor())

                    val buf = ByteBuffer.allocate(1500)

                    while (true) {
                        var needIdle = true
                        var count = tunReader.read(buf.array())
                        if (count > 0) {
                            needIdle = false
                            buf.limit(count)
                            conn.write(ByteBuffer.wrap(count.toByteArray()))
                            conn.write(buf)
                            buf.clear()
                        }

                        var headerBuf = ByteBuffer.allocate(4)
                        count = conn.read(headerBuf)
                        if (count > 0) {
//                            conn.configureBlocking(true)
                            count = headerBuf.array().toInt()
                            buf.limit(count)
                            conn.read(buf)
                            tunWriter.write(buf.array())
                            buf.clear()
                            needIdle = false
                        }

                        if (needIdle) {
                            Thread.sleep(100)
                        }
                    }
                }
            } catch (e: Exception) {
                print(e)
            }
        }.start()

    }

    override
    fun onCreate() {
        // The handler is only used to show messages.
        if (mHandler == null) {
            mHandler = Handler(this)
        }

        // Create the intent to "configure" the connection (just start VpnClient.kt).
        mConfigureIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
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

//        startNIOSocket()
        socketStart()

//        startConnection(VpnConnection(
//            this, mNextConnectionId.getAndIncrement(), server!!, port, secret,
//            proxyHost!!, proxyPort, allow, packages!!))
    }

    private fun startConnection(connection: VpnConnection) {
        // Replace any existing connecting thread with the  new one.
        val thread = Thread(connection, "VpnThread")
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

    private fun socketStart() {
        Thread {
            try {
//            var socket = Socket("10.23.103.134", 8080)
                val socket = Socket("35.236.153.210", 8080)
                socket.keepAlive = true
                socket.tcpNoDelay = true
                socket.reuseAddress = true
                if (!this.protect(socket)) {
                    throw IllegalStateException("Cannot protect the tunnel")
                }

                var socketReader = DataInputStream(socket.getInputStream())
                var socketWriter = DataOutputStream(socket.getOutputStream())

                val ips = loopAddress(socket)

                Log.d("ip address ====", ips.first)
                Log.d("ip address ====", ips.second)
                mHandler!!.sendEmptyMessage(R.string.connected)

                // vpn configration
                var builder = this.Builder()
                builder.addAddress(ips.second, 32)
//            builder.addAddress("10.0.0.8", 32)
                builder.addRoute("0.0.0.0", 0)
                builder.setMtu(1500)
                builder.addDnsServer("8.8.8.8")
//                builder.addSearchDomain("127.0.0.1")

                builder.setSession(ips.second).setConfigureIntent(mConfigureIntent!!)

                var vpnInterface: ParcelFileDescriptor = builder.establish()
                Log.d("new tun interface =======", vpnInterface.toString())

//                // Packets to be sent are queued in this input stream.
//                val tunReader = FileInputStream(vpnInterface!!.getFileDescriptor())

                // Packets received need to be written to this output stream.
//                val tunWriter = FileOutputStream(vpnInterface!!.getFileDescriptor())

//                var headerBuf = ByteArray(4)
//                var packet = ByteArray(1500)

                // 读取 tcp 数据 -> tun interface
                Thread {
                    while (true) {
                        val tunWriter = FileOutputStream(vpnInterface!!.getFileDescriptor())
                        // 自定义协议头
                        var header = ByteArray(4)
                        var tcpPacket = ByteArray(2500)
                        var count = socketReader.read(header, 0, 4)

                        if (count == -1) {
                            Log.d("tcp read EOF", "")
                        }
                        if (count > 0) {
                            val len = header.toInt()

                            if (len > 1500 || len < 0) {
                                Log.d("header ======", Arrays.toString(header))
                                Log.d("header value ======", len.toString())
                                continue
                            }
                            count = socketReader.read(tcpPacket, 0, len)
                            while (count < len) {
                                val left = socketReader.read(tcpPacket, count, len - count)
                                count += left
                            }
                            Log.d("read tcp count ======", count.toString())
                            Log.d(
                                "read tcp header byte count ======",
                                header.toInt().toString()
                            )
                            Log.d("read tcp ======", Arrays.toString(tcpPacket))
                            tunWriter.write(tcpPacket, 0, count)
                        } else {
                            Thread.sleep(2000)
                        }
                    }
                }.start()

                // 读取 tun interface 数据 -> tcp
                while (true) {
                    // Packets to be sent are queued in this input stream.
                    val tunReader = FileInputStream(vpnInterface!!.getFileDescriptor())
                    var tcpPacket = ByteArray(1500)
                    var count = tunReader.read(tcpPacket)

                    if (count > 0) {
                        socketWriter.write(count.toByteArray())
                        socketWriter.write(tcpPacket, 0, count)
                    }
                }

            } catch (e: Exception) {
                print(e)
            }
        }.start()


        return
    }

    // 获取客户端 ip 地址
    fun loopAddress(connection: Socket): Pair<String1, String1> {
        val r = connection.getInputStream()
        var packet = ByteArray(4)
        r.read(packet)

        if (packet.toInt() != Prefs.IPLoop) {
            return Pair("", "")
        }

        var IPs = ByteArray(8)
        r.read(IPs)
        return Pair(IPs.copyOfRange(0, 4).toIP(), IPs.copyOfRange(4, 8).toIP())
    }

    private fun updateForegroundNotification(message: Int) {
        val NOTIFICATION_CHANNEL_ID = "ToyVpn"
        val mNotificationManager = getSystemService(
            NOTIFICATION_SERVICE
        ) as NotificationManager
        mNotificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        startForeground(
            1, Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
//            .setSmallIcon(R.drawable.ic_vpn)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .build()
        )
    }

    companion object {
        private val TAG = VpnService::class.java!!.getSimpleName()

        val ACTION_CONNECT = "com.xizi.xvpn.START"
        val ACTION_DISCONNECT = "com.xizi.xvpn.STOP"

    }

    object Prefs {
        val IPLoop = 0x00010000
    }


    fun Int.toByteArray(): ByteArray {
        val bytes = ByteArray(4)
        bytes[0] = (this and 0x000000FF.toInt()).toByte()
        bytes[1] = ((this and 0x0000FF00.toInt()) ushr 8).toByte()
        bytes[2] = ((this and 0x00FF0000.toInt()) ushr 16).toByte()
        bytes[3] = ((this and 0xFF000000.toInt()) ushr 24).toByte()
        return bytes
    }

    @ExperimentalUnsignedTypes
    fun ByteArray.toInt(): Int {
        if (count() < 4) {
            return 0
        }

        val one = this[0].toUByte().toInt()
        val two = this[1].toUByte().toInt().shl(8)
        val three = this[2].toUByte().toInt().shl(16)
        val four = this[3].toUByte().toInt().shl(24)
        return one or two or three or four
    }

    fun ByteArray.toIP(): String1 {
        if (count() < 4) {
            return "0.0.0.0"
        }

//        return this[0].toUInt().toString() + this[1].toUInt().toString() + this[2].toUInt().toString() + this[3].toUInt().toString()
        return "${this[0].toUByte()}.${this[1].toUByte()}.${this[2].toUByte()}.${this[3].toUByte()}"
    }
}
