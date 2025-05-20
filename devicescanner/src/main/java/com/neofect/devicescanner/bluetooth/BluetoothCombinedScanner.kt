package com.neofect.devicescanner.bluetooth

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.neofect.devicescanner.DeviceScanner
import com.neofect.devicescanner.ScannedDevice
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Created by jhchoi on 2025. 2. 7.
 * jhchoi@neofect.com
 */

class BluetoothCombinedScanner(
    private val bluetoothScanner: BluetoothScanner,
    private val bleScanner: BluetoothLeScanner?
) : DeviceScanner.Scanner {
    companion object {
        private const val LOG_TAG = "BTCombineScanner"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var scanListener: DeviceScanner.Listener? = null
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(LOG_TAG, "Exception raised", throwable)
        scanListener?.onExceptionRaised(Exception("BluetoothCombinedScanner", throwable))
        stopScanners()
    }
    private var scanJob: Job? = null

    override var isFinished: Boolean = true
        private set

    @Synchronized
    override fun start(listener: DeviceScanner.Listener?) {
        isFinished = false
        scanListener = listener
        runBlocking { scanJob?.cancelAndJoin() }
        scanJob = CoroutineScope(Dispatchers.Default + exceptionHandler).launch {
            startBleScan(scanListener)
            delay(1000)

            //android 10부터 bt spp는 2회 이상 호출시 device name을 리턴한다.
            for (btScanCount in 0 until 10) {
                val unknownNearBtDevices = startBtScan(scanListener)
                if (unknownNearBtDevices.isEmpty()) break
                delay(1000)
            }

            stopScanners()
        }
    }

    private suspend fun startBtScan(scanListener: DeviceScanner.Listener?): Set<String> {
        Log.i(LOG_TAG, "bt scan start")

        val unknownDevices = suspendCancellableCoroutine<Set<String>> { continuation ->
            val unknownNearDevices = mutableSetOf<String>()
            bluetoothScanner.start(object : DeviceScanner.Listener {
                override fun onDeviceScanned(device: ScannedDevice) {
                    Log.i(
                        LOG_TAG,
                        "bt device scanned - deviceName: ${device.name}, identifier: ${device.identifier}"
                    )
                    scanListener?.onDeviceScanned(device)

                    if (device is BluetoothScanner.BluetoothScannedDevice) {
                        if (device.rssi > -50 && device.name == null) {
                            Log.i(
                                LOG_TAG,
                                "unknown near bt device - deviceName: ${device.name}, identifier: ${device.identifier}"
                            )
                            unknownNearDevices.add(device.bluetoothDevice.address)
                        }
                    }
                }

                override fun onDeviceChanged(device: ScannedDevice) {
                    scanListener?.onDeviceChanged(device)
                    if (device.name != null) {
                        unknownNearDevices.remove(device.identifier)
                    }
                }

                override fun onExceptionRaised(exception: Exception) {
                    if (continuation.isActive)
                        continuation.resumeWithException(exception)
                }

                override fun onScanFinished() {
                    if(continuation.isActive) {
                        continuation.resume(unknownNearDevices)
                    }
                }
            })
        }
        return unknownDevices
    }

    private suspend fun startBleScan(scanListener: DeviceScanner.Listener?) {
        suspendCancellableCoroutine<Any> { continuation ->
            if (bleScanner == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }
            Log.d(LOG_TAG, "ble scan start")
            bleScanner.start(object : DeviceScanner.Listener {
                override fun onDeviceScanned(device: ScannedDevice) {
                    Log.i(
                        LOG_TAG,
                        "ble device scanned - deviceName: ${device.name}, identifier: ${device.identifier}"
                    )
                    scanListener?.onDeviceScanned(device)
                }

                override fun onDeviceChanged(device: ScannedDevice) {
                    scanListener?.onDeviceChanged(device)
                }

                override fun onExceptionRaised(exception: Exception) {
                    if (continuation.isActive)
                        continuation.resumeWithException(exception)
                }

                override fun onScanFinished() {
                    if (continuation.isActive)
                        continuation.resume(Unit)
                }
            })
        }
    }

    @Synchronized
    override fun stop() {
        runBlocking { scanJob?.cancelAndJoin() }
        stopScanners()
        scanJob = null
    }

    private fun stopScanners() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bleScanner?.stop()
        }
        bluetoothScanner.stop()
        isFinished = true
        scanListener?.onScanFinished()
    }
}