package com.xizi.xvpn

import android.app.Activity
import android.os.Parcel
import android.os.Parcelable
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast

import java.util.Arrays
import java.util.Collections
import java.util.stream.Collectors

class VpnClient: Activity() {
    private val serviceIntent: Intent get() = Intent(this, com.xizi.xvpn.VpnService::class.java)
    interface Prefs {
        companion object {
            val NAME = "connection"
            val SERVER_ADDRESS = "server.address"
            val SERVER_PORT = "server.port"
            val SHARED_SECRET = "shared.secret"
            val PROXY_HOSTNAME = "proxyhost"
            val PROXY_PORT = "proxyport"
            val ALLOW = "allow"
            val PACKAGES = "packages"
        }
    }


    private fun checkProxyConfigs(proxyHost: String, proxyPort: String): Boolean {
        val hasIncompleteProxyConfigs = proxyHost.isEmpty() !== proxyPort.isEmpty()
        if (hasIncompleteProxyConfigs) {
            Toast.makeText(this, R.string.incomplete_proxy_settings, Toast.LENGTH_SHORT).show()
        }
        return !hasIncompleteProxyConfigs
    }

}

