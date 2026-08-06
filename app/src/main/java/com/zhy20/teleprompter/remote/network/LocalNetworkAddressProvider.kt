package com.zhy20.teleprompter.remote.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Resolves the current usable LAN IPv4 of this device via [ConnectivityManager] +
 * [NetworkCapabilities] + [LinkProperties], with a tethering-aware fallback.
 *
 * Requirements from the remote-control spec:
 *  - exclude loopback, link-local, `0.0.0.0` and non-IPv4;
 *  - prefer a private IPv4 (10/8, 172.16/12, 192.168/16, 100.64/10);
 *  - do not require `NET_CAPABILITY_INTERNET` alone: on the *host* of a phone hotspot the
 *    tethering network may not be the "active" network and may lack the INTERNET capability,
 *    so we also scan every network's link properties for a usable private address;
 *  - never fabricate `192.168.137.1` — the hotspot default is not assumed.
 */
class LocalNetworkAddressProvider(
    private val context: Context,
) {
    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: Flow<List<String>> = _addresses.asStateFlow()

    /** Returns the preferred LAN IPv4, or null when none is available. */
    fun currentAddress(): String? = readAddresses().firstOrNull()

    /** Recomputes and emits the current LAN IPv4 candidates. */
    fun refresh() {
        _addresses.value = readAddresses()
    }

    private fun readAddresses(): List<String> {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return emptyList()

        val activeNetwork = manager.activeNetwork
        val activeCaps = if (activeNetwork != null) manager.getNetworkCapabilities(activeNetwork) else null
        val activeLink = if (activeNetwork != null) manager.getLinkProperties(activeNetwork) else null

        val candidates = LinkedHashSet<String>()

        // 1) The active network's addresses (usually Wi-Fi client / Ethernet).
        activeCandidates(activeCaps, activeLink)?.let { candidates.addAll(it) }

        // 2) Every network's link properties, regardless of capability. This catches the
        //    tethering/hotspot interface on the host device, which is often NOT the active
        //    network and may lack NET_CAPABILITY_INTERNET.
        manager.allNetworks.forEach { network ->
            val link = manager.getLinkProperties(network) ?: return@forEach
            collectUsable(link)?.let { candidates.addAll(it) }
        }

        // 3) Wi-Fi interface fallback (last resort for unusual carrier setups).
        if (candidates.isEmpty()) {
            wifiAddresses()?.let { candidates.addAll(it) }
        }

        // Prefer private ranges (hotspot usually uses 192.168.137.x on the host side); keep
        // stable ordering otherwise.
        val ordered = candidates.toList()
        return ordered.sortedWith(
            compareByDescending<String> { it.startsWith("192.168.") }
                .thenByDescending { it.startsWith("10.") }
                .thenByDescending { it.startsWith("172.") }
                .thenBy { it },
        )
    }

    private fun activeCandidates(
        caps: NetworkCapabilities?,
        link: LinkProperties?,
    ): List<String>? {
        if (caps == null || link == null) return null
        val relevant = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!relevant) return null
        return collectUsable(link)
    }

    private fun collectUsable(link: LinkProperties): List<String>? =
        link.linkAddresses
            .asSequence()
            .filter { it.address is java.net.Inet4Address }
            .map { it.address.hostAddress ?: "" }
            .filter(::isUsableLanIpv4)
            .toList()
            .takeIf { it.isNotEmpty() }

    private fun wifiAddresses(): List<String>? = runCatching {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo ?: return null
        @Suppress("DEPRECATION")
        val ip = info.ipAddress
        if (ip == 0) return null
        val address = String.format(
            java.util.Locale.US,
            "%d.%d.%d.%d",
            ip and 0xff,
            (ip shr 8) and 0xff,
            (ip shr 16) and 0xff,
            (ip shr 24) and 0xff,
        )
        if (!isUsableLanIpv4(address)) return null
        listOf(address)
    }.getOrNull()

    private fun isUsableLanIpv4(host: String): Boolean {
        if (host.isEmpty()) return false
        if (host == "0.0.0.0") return false
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val first = parts[0]
        if (first == 127) return false // loopback
        if (first == 169 && parts[1] == 254) return false // link-local
        if (first == 0) return false // 0.x
        if (first == 224 || first == 255) return false // multicast/broadcast
        return true
    }
}
