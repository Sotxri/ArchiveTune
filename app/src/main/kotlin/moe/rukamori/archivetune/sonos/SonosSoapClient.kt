/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sonos

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SOAP Client for controlling Sonos devices.
 */
@Singleton
class SonosSoapClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    suspend fun setAVTransportURI(deviceIp: String, uri: String, title: String, mimeType: String): Result<Unit> {
        val metadata = """
            <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
              <item id="1" parentID="-1" restricted="1">
                <dc:title>$title</dc:title>
                <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                <res protocolInfo="http-get:*:$mimeType:*">$uri</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()

        val escapedMetadata = escapeXml(metadata)

        val body = "<u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID><CurrentURI>$uri</CurrentURI><CurrentURIMetaData>$escapedMetadata</CurrentURIMetaData></u:SetAVTransportURI>"

        return sendRequest(
            deviceIp,
            "AVTransport",
            "SetAVTransportURI",
            body
        )
    }

    suspend fun play(deviceIp: String): Result<Unit> {
        val body = "<u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID><Speed>1</Speed></u:Play>"

        return sendRequest(
            deviceIp,
            "AVTransport",
            "Play",
            body
        )
    }

    suspend fun pause(deviceIp: String): Result<Unit> {
        val body = "<u:Pause xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID></u:Pause>"

        return sendRequest(
            deviceIp,
            "AVTransport",
            "Pause",
            body
        )
    }

    suspend fun setVolume(deviceIp: String, volume: Int): Result<Unit> {
        val body = "<u:SetVolume xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\"><InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>$volume</DesiredVolume></u:SetVolume>"

        return sendRequest(
            deviceIp,
            "RenderingControl",
            "SetVolume",
            body
        )
    }

    suspend fun getTransportInfo(deviceIp: String): Result<String> {
        val body = "<u:GetTransportInfo xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID></u:GetTransportInfo>"

        val response = sendRequestWithBody(
            deviceIp,
            "AVTransport",
            "GetTransportInfo",
            body
        )

        return response.map { responseBody ->
            extractValue(responseBody, "CurrentTransportState") ?: "UNKNOWN"
        }
    }

    private suspend fun sendRequest(
        deviceIp: String,
        serviceType: String,
        action: String,
        body: String
    ): Result<Unit> {
        return sendRequestWithBody(deviceIp, serviceType, action, body).map { Unit }
    }

    private suspend fun sendRequestWithBody(
        deviceIp: String,
        serviceType: String,
        action: String,
        body: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val soapRequest = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>$body</s:Body></s:Envelope>"""

        val endpoint = when (serviceType) {
            "AVTransport" -> "/MediaRenderer/AVTransport/Control"
            "RenderingControl" -> "/MediaRenderer/RenderingControl/Control"
            else -> throw IllegalArgumentException("Unknown service type: $serviceType")
        }

        val url = if (deviceIp.contains(":")) {
            "http://$deviceIp$endpoint"
        } else {
            "http://$deviceIp:1400$endpoint"
        }

        Log.d("SonosSoapClient", "--> Outgoing SOAP Request ($action) to $url")

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
            .addHeader("Connection", "close")
            .addHeader("User-Agent", "ArchiveTune/1.0")
            .addHeader("SOAPACTION", "\"urn:schemas-upnp-org:service:$serviceType:1#$action\"")
            .post(soapRequest.toRequestBody("text/xml".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d("SonosSoapClient", "<-- Incoming HTTP Response ($action): ${response.code}\n$responseBody")
                
                if (response.isSuccessful) {
                    Result.success(responseBody)
                } else {
                    Result.failure(IOException("SOAP Request failed with code ${response.code}: $responseBody"))
                }
            }
        } catch (e: Exception) {
            Log.e("SonosSoapClient", "!!! Request error ($action): ${e.message} !!!", e)
            Result.failure(e)
        }
    }

    private fun escapeXml(xml: String): String {
        return xml.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun extractValue(xml: String, tag: String): String? {
        val pattern = "<$tag>(.*?)</$tag>".toRegex()
        return pattern.find(xml)?.groupValues?.get(1)
    }
}
