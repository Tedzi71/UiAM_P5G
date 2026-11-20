package uiam.p5g.qos

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

class GatewayDetector(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    data class GatewayInfo(
        val gatewayIp: String?,
        val interfaceName: String?,
        val localIp: String?,
        val mtu: Int,
        val isCellular: Boolean
    )

    fun detectDefaultGateway(): GatewayInfo {
        val activeNetwork: Network? = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            return GatewayInfo(null, null, null, 0, false)
        }

        val capabilities: NetworkCapabilities? = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
        if (linkProperties == null) {
            return GatewayInfo(null, null, null, 0, isCellular)
        }

        val interfaceName = linkProperties.interfaceName
        val mtu = linkProperties.mtu

        // Find default route gateway (prefer IPv4)
        var ipv4Gateway: String? = null
        var ipv6Gateway: String? = null

        for (route in linkProperties.routes) {
            if (route.isDefaultRoute) {
                val gateway: InetAddress? = route.gateway
                if (gateway != null && !gateway.isAnyLocalAddress) {
                    val host = gateway.hostAddress ?: continue
                    // Clean IPv6 scope id if present
                    val cleanHost = host.substringBefore('%')
                    if (gateway is Inet4Address) {
                        ipv4Gateway = cleanHost
                        break
                    } else if (ipv6Gateway == null) {
                        ipv6Gateway = cleanHost
                    }
                }
            }
        }

        val gatewayIp = ipv4Gateway ?: ipv6Gateway

        // Local IP address
        var localIp: String? = null
        for (linkAddress in linkProperties.linkAddresses) {
            val addr = linkAddress.address
            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                localIp = addr.hostAddress
                break
            }
        }

        return GatewayInfo(
            gatewayIp = gatewayIp,
            interfaceName = interfaceName,
            localIp = localIp,
            mtu = mtu,
            isCellular = isCellular
        )
    }
}
