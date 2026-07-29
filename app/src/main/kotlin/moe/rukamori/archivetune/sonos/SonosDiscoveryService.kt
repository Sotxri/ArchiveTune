/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sonos

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SonosDiscoveryService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    init {
        Log.e("SonosDiscovery", "SonosDiscoveryService initialized")
    }

    fun discoverSonosDevices(): Flow<List<SonosDevice>> = callbackFlow {
        Log.e("SonosDiscovery", "!!! Starting Sonos discovery flow (Subscriber joined) !!!")
        Timber.d("Starting Sonos discovery")
        
        val multicastLock = wifiManager.createMulticastLock("SonosDiscoveryLock").apply {
            setReferenceCounted(true)
            acquire()
        }
        Log.e("SonosDiscovery", "MulticastLock acquired: ${multicastLock.isHeld}")

        val devices = mutableMapOf<String, SonosDevice>()

        val discoveryJob = launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    Log.d("SonosDiscovery", "Performing discovery loop iteration...")
                    performDiscovery(devices) {
                        Log.e("SonosDiscovery", "Found ${devices.size} total Sonos devices")
                        trySend(devices.values.toList())
                    }
                    delay(RESCAN_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e("SonosDiscovery", "CRITICAL: Error during discovery loop", e)
                Timber.e(e, "Error during Sonos discovery")
            }
        }

        awaitClose {
            Log.e("SonosDiscovery", "Stopping Sonos discovery flow (Subscriber left)")
            Timber.d("Stopping Sonos discovery")
            discoveryJob.cancel()
            if (multicastLock.isHeld) {
                multicastLock.release()
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun performDiscovery(
        devices: MutableMap<String, SonosDevice>,
        onUpdate: () -> Unit,
    ) {
        // Check permission before each attempt
        if (Build.VERSION.SDK_INT >= 35 && ContextCompat.checkSelfPermission(
                context,
                "android.permission.ACCESS_LOCAL_NETWORK"
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("SonosDiscovery", "Skipping discovery: ACCESS_LOCAL_NETWORK not granted")
            return
        }

        val message = """
            M-SEARCH * HTTP/1.1
            HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT
            MAN: "ssdp:discover"
            MX: 2
            ST: $SONOS_ST
            
        """.trimIndent().replace("\n", "\r\n") + "\r\n"

        try {
            val group = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
            
            // Using MulticastSocket as it's better suited for SSDP responses
            MulticastSocket().use { socket ->
                socket.soTimeout = 3000
                socket.reuseAddress = true
                
                val packet = DatagramPacket(
                    message.toByteArray(),
                    message.length,
                    group,
                    SSDP_PORT,
                )
                
                Log.d("SonosDiscovery", "Sending SSDP M-SEARCH from port ${socket.localPort}")
                socket.send(packet)

                val buffer = ByteArray(8192)
                val startTime = System.currentTimeMillis()

                while ((System.currentTimeMillis() - startTime) < RECEIVE_WINDOW_MS) {
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(responsePacket)
                        val response = String(responsePacket.data, 0, responsePacket.length)
                        val ip = responsePacket.address?.hostAddress ?: continue
                        Log.v("SonosDiscovery", "Received UDP packet from $ip")
                        
                        parseSsdpResponse(response, ip)?.let { device ->
                            if (!devices.containsKey(device.usn)) {
                                Log.e("SonosDiscovery", "SUCCESS! Found Sonos: ${device.modelName} at ${device.ip}")
                                devices[device.usn] = device
                                onUpdate()
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        break
                    } catch (e: Exception) {
                        Log.w("SonosDiscovery", "Error receiving packet", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SonosDiscovery", "Discovery error", e)
        }
    }

    private fun parseSsdpResponse(response: String, ip: String): SonosDevice? {
        val lines = response.split("\r\n")
        var location = ""
        var usn = ""
        var server: String? = null
        var householdId: String? = null
        var modelName: String? = null

        for (line in lines) {
            val key = line.substringBefore(":").trim().uppercase()
            val value = line.substringAfter(":").trim()
            when (key) {
                "LOCATION" -> location = value
                "USN" -> usn = value
                "SERVER" -> server = value
                "X-RINCON-HOUSEHOLD" -> householdId = value
            }
        }

        if (location.isEmpty() || usn.isEmpty()) {
            return null
        }

        // Extract IP and port from location URL if possible
        val resolvedIp = try {
            val url = URL(location)
            val host = url.host
            val port = url.port
            if (port != -1 && port != 80) "$host:$port" else host
        } catch (e: Exception) {
            ip
        }

        server?.let {
            if (it.contains("Sonos", ignoreCase = true)) {
                modelName = it.substringBefore("(").trim()
                if (it.contains("(") && it.contains(")")) {
                    val modelCode = it.substringAfter("(").substringBefore(")")
                    modelName = "Sonos $modelCode"
                }
            }
        }

        return SonosDevice(
            ip = resolvedIp,
            location = location,
            usn = usn,
            server = server,
            householdId = householdId,
            modelName = modelName ?: "Sonos Device"
        )
    }

    companion object {
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SONOS_ST = "urn:schemas-upnp-org:device:ZonePlayer:1"
        private const val RESCAN_INTERVAL_MS = 10000L
        private const val RECEIVE_TIMEOUT_MS = 2000
        private const val RECEIVE_WINDOW_MS = 3000L
    }
}
