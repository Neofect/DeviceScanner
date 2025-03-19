package com.neofect.devicescanner.bluetooth

import android.os.Build
import android.util.Log
import com.neofect.devicescanner.DeviceScanner
import com.neofect.devicescanner.ScannedDevice
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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

    private var scanListener: DeviceScanner.Listener? = null
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(LOG_TAG, "Exception raised", throwable)
        scanListener?.onExceptionRaised(Exception("BluetoothCombinedScanner", throwable))
//        scanListener?.onScanFinished()
    }
    private val scope = CoroutineScope(Dispatchers.Default + exceptionHandler)

    @Synchronized
    override fun start(listener: DeviceScanner.Listener?) {
        scanListener = listener
        runBlocking { scanJob?.cancelAndJoin() }
        scanJob = scope.launch {
            suspendCancellableCoroutine<Any> { continuation ->
                if (bleScanner == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    continuation.resume(Unit)
                    return@suspendCancellableCoroutine
                }
                bleScanner.start(object : DeviceScanner.Listener {
                    override fun onDeviceScanned(device: ScannedDevice) {
                        scanListener?.onDeviceScanned(device)
                    }

                    override fun onDeviceChanged(device: ScannedDevice) {
                        scanListener?.onDeviceChanged(device)
                    }

                    override fun onExceptionRaised(exception: Exception) {
                        continuation.resumeWithException(exception)
                    }

                    override fun onScanFinished() {
                        continuation.resume(Unit)
                    }
                })
            }

            suspendCancellableCoroutine<Any> { continuation ->
                bluetoothScanner.start(object : DeviceScanner.Listener {
                    override fun onDeviceScanned(device: ScannedDevice) {
                        scanListener?.onDeviceScanned(device)
                    }

                    override fun onDeviceChanged(device: ScannedDevice) {
                        scanListener?.onDeviceChanged(device)
                    }

                    override fun onExceptionRaised(exception: Exception) {
                        continuation.resumeWithException(exception)
                    }

                    override fun onScanFinished() {
                        continuation.resume(Unit)
                    }
                })
            }

            scanListener?.onScanFinished()
        }
    }

    @Synchronized
    override fun stop() {
        runBlocking { scanJob?.cancelAndJoin() }
        scanJob = null
    }

    private var scanJob: Job? = null
    override val isFinished: Boolean
        get() = scanJob?.isActive != true

}