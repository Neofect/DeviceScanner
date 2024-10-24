package com.neofect.devicescanner.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.neofect.devicescanner.DeviceScanner
import com.neofect.devicescanner.ScannedDevice

/**
 * Created by Neo on 2018/03/02.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
@SuppressLint("MissingPermission")
class BluetoothLeScanner(
    context: Context,
    scanFilters: List<ScanFilter>?,
    scanSettings: ScanSettings?
) : DeviceScanner.Scanner {
    class BluetoothLeScannedDevice(
        identifier: String?,
        name: String?,
        description: String?,
        device: BluetoothDevice?
    ) :
        ScannedDevice(identifier, name, description, device) {
        val bluetoothDevice: BluetoothDevice
            get() = device as BluetoothDevice
    }

    private val context: Context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var listener: DeviceScanner.Listener? = null
    private val scannedDevices: MutableList<BluetoothDevice> = ArrayList()
    private var scanning = false
    override var isFinished: Boolean = false
        private set
    private var scanFilters: List<ScanFilter>? = null
    private var scanSettings: ScanSettings? = null
    private var bleScanner: android.bluetooth.le.BluetoothLeScanner? = null

    override fun start(listener: DeviceScanner.Listener?) {
        this.listener = listener
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            finish(Exception("Bluetooth LE is not supported for Android version " + Build.VERSION.SDK_INT))
            return
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            finish(Exception("DeviceScanner does not support BLE for Android version " + Build.VERSION.SDK_INT))
            return
        } else if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish(Exception("Bluetooth LE is not supported by the device!"))
            return
        }

        if (scanning) {
            Log.e(LOG_TAG, "start: Scanning is in progress!")
            return
        }
        isFinished = false
        val unavailableReason = BluetoothScanner.checkBluetoothAvailability()
        if (unavailableReason != null) {
            finish(unavailableReason)
            return
        }

        startBleScan()
    }

    override fun stop() {
        if (isFinished) {
            Log.w(LOG_TAG, "stop: Already finished.")
            return
        }
        if (bleScanner != null) {
            bleScanner?.stopScan(scanCallback)
        }
        finish(null)
    }

    private fun finish(exception: Exception?) {
        scanning = false
        handler.post {
            isFinished = true
            if (exception == null) {
                listener?.onScanFinished()
            } else {
                listener?.onExceptionRaised(
                    Exception(
                        "Exception from BluetoothLeScanner",
                        exception
                    )
                )
            }
        }
    }

    private fun startBleScan() {
        if (bleScanner == null) {
            bleScanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
        }
        scannedDevices.clear()
        scanning = true

        bleScanner?.startScan(scanFilters, scanSettings, scanCallback)

        // Schedule to stop scanning
        handler.postDelayed({ this.stop() }, SCAN_DURATION.toLong())
    }

    private fun onDeviceScanned(scanResult: ScanResult) {
        val device = scanResult.device
        val rssi = scanResult.rssi
        val deviceName = device.name
        Log.i(
            LOG_TAG,
            "Bluetooth LE device is scanned. name=" + deviceName + ", address=" + device.address + ", rssi=" + rssi
        )

        // Check duplicates
        if (scannedDevices.contains(device)) {
            return
        }
        scannedDevices.add(device)

        handler.post {
            val description = deviceName + " (" + device.address + ")"
            val scannedDevice: ScannedDevice =
                BluetoothLeScannedDevice(device.address, deviceName, description, device)
            listener?.onDeviceScanned(scannedDevice)
        }
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            onDeviceScanned(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                onDeviceScanned(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(LOG_TAG, "Bluetooth LE scan failed! errorCode=$errorCode")
            val message = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "SCAN_FAILED_ALREADY_STARTED"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "SCAN_FAILED_FEATURE_UNSUPPORTED"
                SCAN_FAILED_INTERNAL_ERROR -> "SCAN_FAILED_INTERNAL_ERROR"
                else -> "UNKNOWN"
            }
            finish(Exception(message))
        }
    }

    init {
        if (scanFilters != null) {
            this.scanFilters = scanFilters
        } else {
            this.scanFilters = ArrayList()
        }
        if (scanSettings != null) {
            this.scanSettings = scanSettings
        } else {
            this.scanSettings = createDefaultScanSettings()
        }
    }

    companion object {
        private const val LOG_TAG = "BluetoothLeScanner"

        private const val SCAN_DURATION = 3000

        private fun createDefaultScanSettings(): ScanSettings {
            return ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
        }
    }
}
