/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sonos

/**
 * Data class representing a Sonos device found on the network.
 */
data class SonosDevice(
    val ip: String,
    val location: String,
    val usn: String,
    val server: String? = null,
    val householdId: String? = null,
    val modelName: String? = null,
)
