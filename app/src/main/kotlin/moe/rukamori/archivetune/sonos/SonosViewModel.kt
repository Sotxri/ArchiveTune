/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sonos

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.utils.getLocalIpv4Address
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SonosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val discoveryService: SonosDiscoveryService,
    private val soapClient: SonosSoapClient,
    private val repository: SonosRepository,
) : ViewModel() {

    private val _permissionGranted = MutableStateFlow(hasLocalNetworkPermission())
    val permissionGranted = _permissionGranted.asStateFlow()

    init {
        Log.e("SonosViewModel", "!!! SonosViewModel initialized !!!")
    }

    val discoveredDevices: StateFlow<List<SonosDevice>> = permissionGranted
        .flatMapLatest { granted ->
            if (granted) {
                discoveryService.discoverSonosDevices()
            } else {
                flowOf(emptyList())
            }
        }
        .onEach { devices -> Log.e("SonosViewModel", "Flow emission: found ${devices.size} devices") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedDevice = repository.selectedDevice

    fun updatePermissionStatus() {
        _permissionGranted.value = hasLocalNetworkPermission()
    }

    private fun hasLocalNetworkPermission(): Boolean {
        // ACCESS_LOCAL_NETWORK is only required for Android 17 (API 35+) and targetSdk 37+
        if (Build.VERSION.SDK_INT < 35) return true
        
        return ContextCompat.checkSelfPermission(
            context,
            "android.permission.ACCESS_LOCAL_NETWORK"
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun selectDevice(device: SonosDevice?, trackTitle: String, mimeType: String) {
        viewModelScope.launch {
            repository.setSelectedDevice(device)
            if (device != null) {
                val localIp = getLocalIpv4Address() ?: "127.0.0.1"
                val localStreamUrl = "http://$localIp:8080/stream"
                
                // Force AAC for Sonos
                val fixedMimeType = if (mimeType.contains("webm")) "audio/mp4" else mimeType
                
                Log.e("SonosViewModel", "!!! Starting cast to ${device.ip} !!!")
                Log.e("SonosViewModel", "!!! Sending cast URL: $localStreamUrl (Mime: $fixedMimeType) !!!")
                Timber.d("Starting cast to ${device.ip}")
                
                // Start the local streaming service
                val intent = Intent(context, AudioStreamingService::class.java)
                context.startForegroundService(intent)
                
                delay(1000) // Wait for service to initialize

                val setUriResult = soapClient.setAVTransportURI(device.ip, localStreamUrl, trackTitle, fixedMimeType)
                if (setUriResult.isSuccess) {
                    soapClient.play(device.ip)
                } else {
                    Log.e("SonosViewModel", "!!! Failed to set AVTransportURI: ${setUriResult.exceptionOrNull()?.message} !!!")
                }
                
                // Verify transport state
                delay(2000) // Increased delay to allow server and buffering
                val stateResult = soapClient.getTransportInfo(device.ip)
                val state = stateResult.getOrNull()
                Log.e("SonosViewModel", "!!! Sonos Transport State: $state !!!")
                
                if (state != "PLAYING" && state != "TRANSITIONING") {
                    Log.e("SonosViewModel", "!!! Sonos failed to start playing (State: $state) !!!")
                }
            } else {
                Timber.d("Stopping cast")
                context.stopService(Intent(context, AudioStreamingService::class.java))
            }
        }
    }

    fun play() {
        val device = selectedDevice.value ?: return
        viewModelScope.launch {
            soapClient.play(device.ip)
        }
    }

    fun pause() {
        val device = selectedDevice.value ?: return
        viewModelScope.launch {
            soapClient.pause(device.ip)
        }
    }

    fun setVolume(volume: Int) {
        val device = selectedDevice.value ?: return
        viewModelScope.launch {
            soapClient.setVolume(device.ip, volume)
        }
    }
}
