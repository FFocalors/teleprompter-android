package com.zhy20.teleprompter.remote.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Resolves the current usable LAN IPv4 of this device via [ConnectivityManager] +
 * [NetworkCapabilities] + [LinkProperties]. Loopback, link-local, `0.0.0.0` and non-IPv4
 * addresses are excluded; the active (validated) Wi-Fi/Ethernet interface is preferred.
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

        val activeCandidates = addressCandidates(activeCaps, activeLink)
        if (activeCandidates.isNotEmpty()) return activeCandidates

        // Fall back to iterating all networks so Ethernet-over-adapter setups still resolve.
        val all = manager.allNetworks
            .asSequence()
            .mapNotNull { network ->
                val caps = manager.getNetworkCapabilities(network)
                val link = manager.getLinkProperties(network)
                addressCandidates(caps, link)
            }
            .flatten()
        return all.distinct().toList()
    }

    private fun addressCandidates(
        caps: NetworkCapabilities?,
        link: LinkProperties?,
    ): List<String> {
        if (caps == null || link == null) return emptyList()
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return emptyList()
        val relevant = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!relevant) return emptyList()

        return link.linkAddresses
            .asSequence()
            .filter { it.address is java.net.Inet4Address }
            .map { it.address.hostAddress ?: "" }
            .filter(::isUsableLanIpv4)
            .sortedByDescending(::isPrivateIpv4)
            .toList()
    }

    private fun isUsableLanIpv4(host: String): Boolean {
        if (host.isEmpty()) return false
        if (host == "0.0.0.0") return false
        val first = host.substringBefore('.').toIntOrNull() ?: return false
        if (first == 127) return false // loopback
        if (first == 169 && host.startsWith("169.254.")) return false // link-local
        return true
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val (a, b) = parts
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 100 && b in 64..127) ||
            (a == 169 && b == 254)
    }
}
