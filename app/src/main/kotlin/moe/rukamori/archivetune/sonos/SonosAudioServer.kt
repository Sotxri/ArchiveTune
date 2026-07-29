/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sonos

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.net.HttpURLConnection
import java.net.URL

/**
 * Local HTTP server to stream audio to Sonos devices.
 */
class SonosAudioServer(
    port: Int,
    private val currentTrackProvider: () -> TrackInfo?
) : NanoHTTPD(port) {

    data class TrackInfo(
        val url: String,
        val mimeType: String,
        val contentLength: Long = -1
    )

    override fun serve(session: IHTTPSession): Response {
        val headers = session.headers
        Log.e("SonosAudioServer", "--> Incoming Sonos request: ${session.uri} from ${session.remoteIpAddress}")
        Log.d("SonosAudioServer", "Headers: $headers")
        
        if (session.uri == "/stream") {
            val track = currentTrackProvider()
            if (track == null) {
                Log.e("SonosAudioServer", "!!! Rejecting request: No track info available !!!")
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No track playing")
            }

            return proxyRemoteStream(session, track)
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
    }

    private fun proxyRemoteStream(session: IHTTPSession, track: TrackInfo): Response {
        return try {
            val connection = URL(track.url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "ArchiveTune/1.0")
            
            // Forward Range header from Sonos to source if present
            val rangeHeader = session.headers["range"]
            if (rangeHeader != null) {
                Log.d("SonosAudioServer", "Forwarding Range: $rangeHeader")
                connection.setRequestProperty("Range", rangeHeader)
            }
            
            connection.connect()
            val responseCode = connection.responseCode
            
            val inputStream = connection.inputStream
            val contentLength = if (track.contentLength > 0) track.contentLength else connection.contentLengthLong
            val mimeType = track.mimeType.ifEmpty { "audio/mpeg" }

            Log.i("SonosAudioServer", "Proxying stream: status $responseCode, type $mimeType, length $contentLength")
            
            val response = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                val contentRange = connection.getHeaderField("Content-Range")
                newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, inputStream, contentLength).apply {
                    addHeader("Content-Range", contentRange)
                }
            } else {
                newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, contentLength)
            }
            
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Connection", "keep-alive")
            response
        } catch (e: Exception) {
            if (e is java.net.SocketException && e.message?.contains("Broken pipe") == true) {
                Log.w("SonosAudioServer", "Sonos closed the connection (Broken pipe)")
            } else {
                Log.e("SonosAudioServer", "!!! Proxy error: ${e.message} !!!", e)
            }
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Failed to proxy stream")
        }
    }
}
