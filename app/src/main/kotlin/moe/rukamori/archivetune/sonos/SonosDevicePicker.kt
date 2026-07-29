/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.sonos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.ListItem

@Composable
fun SonosDevicePicker(
    devices: List<SonosDevice>,
    selectedDevice: SonosDevice?,
    permissionGranted: Boolean,
    onPermissionResult: () -> Unit,
    onDeviceSelected: (SonosDevice?) -> Unit,
    onDismiss: () -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onPermissionResult()
    }

    ListDialog(
        onDismiss = onDismiss,
        content = {
            item {
                Text(
                    text = "Cast to Sonos",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (!permissionGranted) {
                item {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Local network access is required to find Sonos devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(onClick = {
                            permissionLauncher.launch("android.permission.ACCESS_LOCAL_NETWORK")
                        }) {
                            Text("Allow Access")
                        }
                    }
                }
            } else {
                if (selectedDevice != null) {
                    item {
                        ListItem(
                            title = "Stop Casting",
                            subtitle = "Currently casting to ${selectedDevice.modelName ?: selectedDevice.ip}",
                            modifier = Modifier.clickable {
                                onDeviceSelected(null)
                                onDismiss()
                            },
                            thumbnailContent = {
                                Icon(Icons.Default.Speaker, contentDescription = null)
                            }
                        )
                    }
                }

                if (devices.isEmpty()) {
                    item {
                        Text(
                            text = "Searching for devices...",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(devices) { device ->
                        ListItem(
                            title = device.modelName ?: "Sonos Device",
                            subtitle = device.ip,
                            modifier = Modifier.clickable {
                                onDeviceSelected(device)
                                onDismiss()
                            },
                            thumbnailContent = {
                                Icon(Icons.Default.Speaker, contentDescription = null)
                            },
                            trailingContent = {
                                if (device.usn == selectedDevice?.usn) {
                                    Text(
                                        text = "Connected",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}
