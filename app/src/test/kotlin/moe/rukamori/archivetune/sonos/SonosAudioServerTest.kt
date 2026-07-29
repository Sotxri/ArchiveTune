/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sonos

import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class SonosAudioServerTest {

    @Test
    fun `serve returns 404 for unknown uri`() {
        val server = SonosAudioServer(8080) { null }
        val session = mock(NanoHTTPD.IHTTPSession::class.java)
        `when`(session.uri).thenReturn("/unknown")

        val response = server.serve(session)

        assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, response.status)
    }

    @Test
    fun `serve returns 404 for stream when no track provider`() {
        val server = SonosAudioServer(8080) { null }
        val session = mock(NanoHTTPD.IHTTPSession::class.java)
        `when`(session.uri).thenReturn("/stream")

        val response = server.serve(session)

        assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, response.status)
    }

    @Test
    fun `serve returns error for local file track`() {
        val track = SonosAudioServer.TrackInfo("file:///test.mp3", "audio/mpeg")
        val server = SonosAudioServer(8080) { track }
        val session = mock(NanoHTTPD.IHTTPSession::class.java)
        `when`(session.uri).thenReturn("/stream")

        val response = server.serve(session)

        assertEquals(NanoHTTPD.Response.Status.INTERNAL_ERROR, response.status)
    }
}
