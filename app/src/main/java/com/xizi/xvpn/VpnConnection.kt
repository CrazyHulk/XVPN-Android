package com.xizi.xvpn

import java.nio.charset.StandardCharsets.US_ASCII

import android.app.PendingIntent
import android.content.pm.PackageManager
import android.net.ProxyInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.util.Log

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class VpnTcpConnection() : Runnable {
    override fun run() {

    }

    @Throws(IOException::class, InterruptedException::class, IllegalArgumentException::class)
    private fun run(server: SocketAddress): Boolean {
        var iface: ParcelFileDescriptor? = null
        var connected = false
        // Create a DatagramChannel as the VPN tunnel.

        return connected
    }
}

class VpnConnection(private val mService: VpnService, private val mConnectionId: Int,
                       private val mServerName: String, private val mServerPort: Int, private val mSharedSecret: ByteArray,
                       proxyHostName: String, proxyHostPort: Int, // Allowed/Disallowed packages for VPN usage
                       private val mAllow: Boolean,
                       private val mPackages: Set<String>) : Runnable {

    private var mConfigureIntent: PendingIntent? = null
    private var mOnEstablishListener: OnEstablishListener? = null
    private var fnOnEstablishListener: ((ParcelFileDescriptor) -> Unit)? = null

    // Proxy settings
    private var mProxyHostName: String? = null
    private var mProxyHostPort: Int = 0

    private val tag: String
        get() = VpnConnection::class.java!!.getSimpleName() + "[" + mConnectionId + "]"

    /**
     * Callback interface to let the [ToyVpnService] know about new connections
     * and update the foreground notification with connection status.
     */
    interface OnEstablishListener {
        fun onEstablish(tunInterface: ParcelFileDescriptor)
    }

    init {

        if (!TextUtils.isEmpty(proxyHostName)) {
            mProxyHostName = proxyHostName
        }
        if (proxyHostPort > 0) {
            // The port value is always an integer due to the configured inputType.
            mProxyHostPort = proxyHostPort
        }
    }

    /**
     * Optionally, set an intent to configure the VPN. This is `null` by default.
     */
    fun setConfigureIntent(intent: PendingIntent) {
        mConfigureIntent = intent
    }

    fun setOnEstablishListener(listener: OnEstablishListener) {
        mOnEstablishListener = listener
    }

    fun setFnOnEstablishListener(fn: (ParcelFileDescriptor) -> Unit) {
        fnOnEstablishListener = fn
    }

    override fun run() {
        try {
            Log.i(tag, "Starting")

            // If anything needs to be obtained using the network, get it now.
            // This greatly reduces the complexity of seamless handover, which
            // tries to recreate the tunnel without shutting down everything.
            // In this demo, all we need to know is the server address.
            val serverAddress = InetSocketAddress(mServerName, mServerPort)

            // We try to create the tunnel several times.
            // TODO: The better way is to work with ConnectivityManager, trying only when the
            // network is available.
            // Here we just use a counter to keep things simple.
            var attempt = 0
            while (attempt < 10) {
                // Reset the counter if we were connected.
                if (run(serverAddress)) {
                    attempt = 0
                }

                // Sleep for a while. This also checks if we got interrupted.
                Thread.sleep(3000)
                ++attempt
            }
            Log.i(tag, "Giving up")
        } catch (e: IOException) {
            Log.e(tag, "Connection failed, exiting", e)
        } catch (e: InterruptedException) {
            Log.e(tag, "Connection failed, exiting", e)
        } catch (e: IllegalArgumentException) {
            Log.e(tag, "Connection failed, exiting", e)
        }

    }

    @Throws(IOException::class, InterruptedException::class, IllegalArgumentException::class)
    private fun run(server: SocketAddress): Boolean {
        var iface: ParcelFileDescriptor? = null
        var connected = false

        configure("")
        // Create a DatagramChannel as the VPN tunnel.
        try {

            DatagramChannel.open().use({ tunnel ->

                // Protect the tunnel before connecting to avoid loopback.
                if (!mService.protect(tunnel.socket())) {
                    throw IllegalStateException("Cannot protect the tunnel")
                }

                // Connect to the server.
                tunnel.connect(server)

                // For simplicity, we use the same thread for both reading and
                // writing. Here we put the tunnel into non-blocking mode.
                tunnel.configureBlocking(false)

                // Authenticate and configure the virtual network interface.
                iface = handshake(tunnel)

                // Now we are connected. Set the flag.
                connected = true

                // Packets to be sent are queued in this input stream.
                val `in` = FileInputStream(iface!!.getFileDescriptor())

                // Packets received need to be written to this output stream.
                val out = FileOutputStream(iface!!.getFileDescriptor())

                // Allocate the buffer for a single packet.
                val packet = ByteBuffer.allocate(MAX_PACKET_SIZE)

                // Timeouts:
                //   - when data has not been sent in a while, send empty keepalive messages.
                //   - when data has not been received in a while, assume the connection is broken.
                var lastSendTime = System.currentTimeMillis()
                var lastReceiveTime = System.currentTimeMillis()

                // We keep forwarding packets till something goes wrong.
                while (true) {
                    // Assume that we did not make any progress in this iteration.
                    var idle = true

                    // Read the outgoing packet from the input stream.
                    var length = `in`.read(packet.array())
                    if (length > 0) {
                        // Write the outgoing packet to the tunnel.
                        packet.limit(length)
                        tunnel.write(packet)
                        packet.clear()

                        // There might be more outgoing packets.
                        idle = false
                        lastReceiveTime = System.currentTimeMillis()
                    }

                    // Read the incoming packet from the tunnel.
                    length = tunnel.read(packet)
                    if (length > 0) {
                        // Ignore control messages, which start with zero.
                        if (packet.get(0).toInt() !== 0) {
                            // Write the incoming packet to the output stream.
                            out.write(packet.array(), 0, length)
                        }
                        packet.clear()

                        // There might be more incoming packets.
                        idle = false
                        lastSendTime = System.currentTimeMillis()
                    }

                    // If we are idle or waiting for the network, sleep for a
                    // fraction of time to avoid busy looping.
                    if (idle) {
                        Thread.sleep(IDLE_INTERVAL_MS)
                        val timeNow = System.currentTimeMillis()

                        if (lastSendTime + KEEPALIVE_INTERVAL_MS <= timeNow) {
                            // We are receiving for a long time but not sending.
                            // Send empty control messages.
                            packet.put(0.toByte()).limit(1)
                            for (i in 0..2) {
                                packet.position(0)
                                tunnel.write(packet)
                            }
                            packet.clear()
                            lastSendTime = timeNow
                        } else if (lastReceiveTime + RECEIVE_TIMEOUT_MS <= timeNow) {
                            // We are sending for a long time but not receiving.
                            throw IllegalStateException("Timed out")
                        }
                    }
                }
            })
        } catch (e: SocketException) {
            Log.e(tag, "Cannot use socket", e)
        } finally {
            if (iface != null) {
                try {
                    iface!!.close()
                } catch (e: IOException) {
                    Log.e(tag, "Unable to close interface", e)
                }

            }
        }
        return connected
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun handshake(tunnel: DatagramChannel): ParcelFileDescriptor {
        // To build a secured tunnel, we should perform mutual authentication
        // and exchange session keys for encryption. To keep things simple in
        // this demo, we just send the shared secret in plaintext and wait
        // for the server to send the parameters.

        // Allocate the buffer for handshaking. We have a hardcoded maximum
        // handshake size of 1024 bytes, which should be enough for demo
        // purposes.
        val packet = ByteBuffer.allocate(1024)

        // Control messages always start with zero.
        packet.put(0.toByte()).put(mSharedSecret).flip()

        // Send the secret several times in case of packet loss.
        for (i in 0..2) {
            packet.position(0)
            tunnel.write(packet)
        }
        packet.clear()

        // Wait for the parameters within a limited time.
        for (i in 0 until MAX_HANDSHAKE_ATTEMPTS) {
            Thread.sleep(IDLE_INTERVAL_MS)

            // Normally we should not receive random packets. Check that the first
            // byte is 0 as expected.
            val length = tunnel.read(packet)
            if (length > 0 && packet.get(0).toInt() === 0) {
                return configure(String(packet.array(), 1 , length -1, StandardCharsets.US_ASCII).trim())
            }
        }
        throw IOException("Timed out")
    }

    @Throws(IllegalArgumentException::class)
    private fun configure(parameters: String): ParcelFileDescriptor {
        // Configure a builder while parsing the parameters.
        val builder = mService.Builder()
//        for (parameter in parameters.split(" ")) {
//            val fields = parameter.split(",")
//            try {
//                when (fields[0].first()) {
//                    'm' -> builder.setMtu(1500)
//                    'a' -> builder.addAddress(fields[1], Integer.parseInt(fields[2]))
//                    'r' -> builder.addRoute(fields[1], Integer.parseInt(fields[2]))
//                    'd' -> builder.addDnsServer(fields[1])
//                    's' -> builder.addSearchDomain(fields[1])
//                }
//            } catch (e: NumberFormatException) {
//                throw IllegalArgumentException("Bad parameter: $parameter")
//            }
//
//        }
        builder.addAddress("10.0.0.9", 32)
        builder.addRoute("0.0.0.0",0)
        builder.setMtu(1500)
        builder.addDnsServer("8.8.8.8")
        builder.addSearchDomain("127.0.0.1")

        // Create a new interface using the builder and save the parameters.
        var vpnInterface: ParcelFileDescriptor
        for (packageName in mPackages) {
            try {
                if (mAllow) {
                    builder.addAllowedApplication(packageName)
                } else {
                    builder.addDisallowedApplication(packageName)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(tag, "Package not available: $packageName", e)
            }

        }
        builder.setSession(mServerName).setConfigureIntent(mConfigureIntent)
//        if (!TextUtils.isEmpty(mProxyHostName)) {
//            builder.setHttpProxy(ProxyInfo.buildDirectProxy(mProxyHostName, mProxyHostPort))
//        }
        synchronized(mService) {
            vpnInterface = builder.establish()
            if (mOnEstablishListener != null) {
                mOnEstablishListener!!.onEstablish(vpnInterface)
            }
        }
        Log.i(tag, "New interface: $vpnInterface ($parameters)")
        return vpnInterface
    }

    companion object {

        /** Maximum packet size is constrained by the MTU, which is given as a signed short.  */
//        private val MAX_PACKET_SIZE = Short.MAX_VALUE
        private val MAX_PACKET_SIZE = 1500

        /** Time to wait in between losing the connection and retrying.  */
        private val RECONNECT_WAIT_MS = TimeUnit.SECONDS.toMillis(3)

        /** Time between keepalives if there is no traffic at the moment.
         *
         * TODO: don't do this; it's much better to let the connection die and then reconnect when
         * necessary instead of keeping the network hardware up for hours on end in between.
         */
        private val KEEPALIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(15)

        /** Time to wait without receiving any response before assuming the server is gone.  */
        private val RECEIVE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20)

        /**
         * Time between polling the VPN interface for new traffic, since it's non-blocking.
         *
         * TODO: really don't do this; a blocking read on another thread is much cleaner.
         */
        private val IDLE_INTERVAL_MS = TimeUnit.MILLISECONDS.toMillis(100)

        /**
         * Number of periods of length {@IDLE_INTERVAL_MS} to wait before declaring the handshake a
         * complete and abject failure.
         *
         * TODO: use a higher-level protocol; hand-rolling is a fun but pointless exercise.
         */
        private val MAX_HANDSHAKE_ATTEMPTS = 50
    }
}
