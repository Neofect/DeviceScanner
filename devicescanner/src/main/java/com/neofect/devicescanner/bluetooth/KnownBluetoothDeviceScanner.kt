package com.neofect.devicescanner.bluetooth

import android.bluetooth.BluetoothAdapter
import android.util.Log
import com.neofect.devicescanner.DeviceScanner
import com.neofect.devicescanner.DeviceScanner.Scanner
import com.neofect.devicescanner.bluetooth.BluetoothScanner.BluetoothScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Created by jhchoi on 2/27/24
 * jhchoi@neofect.com
 */
class KnownBluetoothDeviceScanner(
    private val scannerDelegate: KnownBluetoothDeviceScannerDelegate
) : Scanner {
    private var scanJob: Job? = null

    companion object {
        private const val LOG_TAG = "KnownBtDeviceScanner"
    }


    override fun start(listener: DeviceScanner.Listener?) {
        //"B6:15:3B:9F:A4:02"
        scanJob?.cancel()
        scanJob = CoroutineScope(Dispatchers.Default).launch {
            val knownDeviceList = scannerDelegate.knownList()
            for (knownDeviceInfo in knownDeviceList) {
                try {
                    val device =
                        BluetoothAdapter.getDefaultAdapter()
                            .getRemoteDevice(knownDeviceInfo.macAddress)
                    val deviceName = device.name
                    Log.d(LOG_TAG, "bluetooth device found. name: $deviceName")

                    listener?.onDeviceScanned(BluetoothScannedDevice(device))

                } catch (e: Exception) {
                    Log.w(LOG_TAG, "device connect failed. ${e.message}")
                }
            }
        }
    }

    override fun stop() {
        scanJob?.cancel()
        scanJob = null
    }

    override fun isFinished(): Boolean {
        return scanJob?.isActive == true
    }
}