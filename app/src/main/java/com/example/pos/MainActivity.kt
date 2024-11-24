package com.example.pos

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.pos.ui.theme.POSTheme
import java.io.IOException
import java.util.*

data class DeviceInfo(
    val name: String,
    val address: String,
    val device: BluetoothDevice
)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val targetPrinterName = "JK-80PL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        setContent {
            POSTheme {
                var pairedDevices = remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
                var connectionStatus = remember { mutableStateOf("Not Connected") }

                LaunchedEffect(Unit) {
                    requestPermissions()
                    if (hasBluetoothPermissions()) {
                        updatePairedDevices { devices ->
                            if (ActivityCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                pairedDevices.value = devices
                                    .filter { device ->
                                        device.name?.contains(targetPrinterName, ignoreCase = true) == true
                                    }
                                    .map { device ->
                                        DeviceInfo(
                                            name = device.name ?: "Unknown Device",
                                            address = device.address,
                                            device = device
                                        )
                                    }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Status Card
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(connectionStatus.value)
                            }
                        }

                        // Device List
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pairedDevices.value) { deviceInfo ->
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text(deviceInfo.name)
                                        Text(
                                            deviceInfo.address,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Button(
                                            onClick = {
                                                connectToDevice(deviceInfo.device) { success ->
                                                    connectionStatus.value = if (success) {
                                                        "Connected to ${deviceInfo.name}"
                                                    } else {
                                                        "Failed to connect to ${deviceInfo.name}"
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Connect")
                                        }
                                    }
                                }
                            }
                        }

                        // Test Print Button
                        Button(
                            onClick = { printTest() },
                            modifier = Modifier.fillMaxWidth(),

                        ) {
                            Text("Test Print")
                        }
                    }
                }
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            checkPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                    checkPermission(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            checkPermission(Manifest.permission.BLUETOOTH) &&
                    checkPermission(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
                ),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun updatePairedDevices(onDevicesUpdated: (Set<BluetoothDevice>) -> Unit) {
        if (!hasBluetoothPermissions()) {
            onDevicesUpdated(emptySet())
            return
        }

        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                @Suppress("DEPRECATION")
                bluetoothAdapter?.bondedDevices?.let { devices ->
                    onDevicesUpdated(devices)
                } ?: onDevicesUpdated(emptySet())
            }
        } catch (e: SecurityException) {
            Log.e("Bluetooth", "Security exception: ${e.message}")
            onDevicesUpdated(emptySet())
        }
    }

    private fun connectToDevice(device: BluetoothDevice, onResult: (Boolean) -> Unit) {
        if (!hasBluetoothPermissions()) {
            onResult(false)
            return
        }

        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothSocket?.close()
                @Suppress("DEPRECATION")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(sppUuid)

                Thread {
                    try {
                        bluetoothSocket?.connect()
                        runOnUiThread { onResult(true) }
                    } catch (e: IOException) {
                        Log.e("Bluetooth", "Connection failed: ${e.message}")
                        bluetoothSocket?.close()
                        bluetoothSocket = null
                        runOnUiThread { onResult(false) }
                    }
                }.start()
            }
        } catch (e: SecurityException) {
            Log.e("Bluetooth", "Security error: ${e.message}")
            onResult(false)
        }
    }

    private fun printTest() {
        if (!hasBluetoothPermissions() || bluetoothSocket?.isConnected != true) {
            return
        }

        try {
            bluetoothSocket?.let { socket ->
                val outputStream = socket.outputStream

                // Initialize printer
                outputStream.write(byteArrayOf(0x1B, 0x40))  // ESC @ - Initialize printer

                // Test print content
                val testData = """
                    |--------------------------------
                    |        TEST PRINT ADALID SE LA COME
                    |        JK-80PL
                    |--------------------------------
                    |Date: ${Date()}
                    |
                    |Test successful!
                    |--------------------------------
                    |
                    |
                    |
                """.trimMargin()

                outputStream.write(testData.toByteArray())

                // Feed and cut commands
                outputStream.write(byteArrayOf(0x0A, 0x0A, 0x0A))  // Line feeds
                outputStream.write(byteArrayOf(0x1D, 0x56, 0x41, 0x00))  // GS V A - Full cut

                outputStream.flush()
            }
        } catch (e: IOException) {
            Log.e("Bluetooth", "Print error: ${e.message}")
        }
    }

    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 1
    }
}