package com.neofect.devicescanner.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Log
import com.neofect.devicescanner.DeviceScanner
import com.neofect.devicescanner.DeviceScanner.Scanner
import com.neofect.devicescanner.bluetooth.BluetoothLeScanner.BluetoothLeScannedDevice
import com.neofect.devicescanner.bluetooth.BluetoothScanner.BluetoothScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Created by jhchoi on 2/27/24
 * jhchoi@neofect.com
 */
class KnownBluetoothDeviceScanner(
    private val knownDeviceList: List<KnownBluetoothDeviceData>
) : Scanner {
    private var listener: DeviceScanner.Listener? = null
    private var scanJob: Job? = null
    private val coroutineContext = SupervisorJob() + Dispatchers.Default

    companion object {
        private const val LOG_TAG = "KnownBtDeviceScanner"
    }


    @Synchronized
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun start(listener: DeviceScanner.Listener?) {
        this.listener = listener
        scanJob?.cancel()
        scanJob = CoroutineScope(coroutineContext).launch {
            try {
                Log.d(LOG_TAG, "scan start.")
                for (knownDeviceInfo in knownDeviceList) {
                    val device =
                        BluetoothAdapter.getDefaultAdapter()
                            .getRemoteDevice(knownDeviceInfo.macAddress)
                    val deviceName = device.name
                    Log.d(LOG_TAG, "bluetooth device found. name: $deviceName")

                    withContext(Dispatchers.Main) {
                        val description = deviceName + " (" + device.address + ")"
                        val scannedDevice = if (knownDeviceInfo.isBle)
                            BluetoothLeScannedDevice(
                                device.address, device.name, description, device
                            )
                        else BluetoothScannedDevice(device).apply {
                            this.description = description
                        }
                        listener?.onDeviceScanned(scannedDevice)
                    }
                }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "device scan failed. ${e.message}")
                e.printStackTrace()
            } finally {
                Log.d(LOG_TAG, "scan finish.")
                listener?.onScanFinished()
            }
        }
    }

    @Synchronized
    override fun stop() {
        Log.d(LOG_TAG, "stop.")
        scanJob?.takeIf { it.isActive }?.cancel()
        scanJob = null
    }

    override fun isFinished(): Boolean {
        return scanJob?.isActive != true
    }
}