/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sonos

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SonosSoapClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: SonosSoapClient
    private val okHttpClient = OkHttpClient()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = SonosSoapClient(okHttpClient)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `setAVTransportURI sends correct XML and headers`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(SUCCESS_RESPONSE))

        val host = mockWebServer.hostName
        val port = mockWebServer.port
        val result = client.setAVTransportURI("$host:$port", "x-rincon-mp3radio://example.com/stream.mp3", "Test Track", "audio/mpeg")

        if (result.isFailure) {
            println("Failure: ${result.exceptionOrNull()?.message}")
        }
        assertTrue("Result should be success", result.isSuccess)
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/MediaRenderer/AVTransport/Control", request.path)
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"", request.getHeader("SOAPACTION"))
        assertTrue(request.body.readUtf8().contains("<CurrentURI>x-rincon-mp3radio://example.com/stream.mp3</CurrentURI>"))
    }

    @Test
    fun `play sends correct XML and headers`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(SUCCESS_RESPONSE))

        val host = mockWebServer.hostName
        val port = mockWebServer.port
        val result = client.play("$host:$port")

        assertTrue(result.isSuccess)
        val request = mockWebServer.takeRequest()
        assertEquals("/MediaRenderer/AVTransport/Control", request.path)
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#Play\"", request.getHeader("SOAPACTION"))
        assertTrue(request.body.readUtf8().contains("<u:Play"))
    }

    @Test
    fun `setVolume sends correct XML and headers`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(SUCCESS_RESPONSE))

        val host = mockWebServer.hostName
        val port = mockWebServer.port
        val result = client.setVolume("$host:$port", 50)

        assertTrue(result.isSuccess)
        val request = mockWebServer.takeRequest()
        assertEquals("/MediaRenderer/RenderingControl/Control", request.path)
        assertEquals("\"urn:schemas-upnp-org:service:RenderingControl:1#SetVolume\"", request.getHeader("SOAPACTION"))
        assertTrue(request.body.readUtf8().contains("<DesiredVolume>50</DesiredVolume>"))
    }

    @Test
    fun `failure returns failure result`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody(ERROR_RESPONSE))

        val host = mockWebServer.hostName
        val port = mockWebServer.port
        val result = client.pause("$host:$port")

        assertTrue("Result should be failure", result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue("Error message should contain 500, but was: $message", message.contains("500"))
        assertTrue("Error message should contain UPnPError, but was: $message", message.contains("UPnPError"))
    }

    companion object {
        private const val SUCCESS_RESPONSE = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetAVTransportURIResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"/>
                </s:Body>
            </s:Envelope>
        """

        private const val ERROR_RESPONSE = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <s:Fault>
                        <faultcode>s:Client</faultcode>
                        <faultstring>UPnPError</faultstring>
                        <detail>
                            <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                                <errorCode>701</errorCode>
                                <errorDescription>Transition not available</errorDescription>
                            </UPnPError>
                        </detail>
                    </s:Fault>
                </s:Body>
            </s:Envelope>
        """
    }
}
