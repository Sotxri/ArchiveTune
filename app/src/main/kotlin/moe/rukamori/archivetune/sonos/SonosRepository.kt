/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sonos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SonosRepository @Inject constructor() {
    private val _selectedDevice = MutableStateFlow<SonosDevice?>(null)
    val selectedDevice = _selectedDevice.asStateFlow()

    fun setSelectedDevice(device: SonosDevice?) {
        _selectedDevice.value = device
    }
}
