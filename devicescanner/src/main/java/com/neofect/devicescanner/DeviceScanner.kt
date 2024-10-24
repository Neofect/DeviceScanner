package com.neofect.devicescanner

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import android.util.Pair
import androidx.annotation.RequiresApi
import com.neofect.devicescanner.bluetooth.BluetoothLeScanner
import com.neofect.devicescanner.bluetooth.BluetoothScanner
import com.neofect.devicescanner.bluetooth.KnownBluetoothDeviceData
import com.neofect.devicescanner.bluetooth.KnownBluetoothDeviceScanner
import com.neofect.devicescanner.usb.UsbScanner

/**
 * @author neo.kim@neofect.com
 * @date Nov 15, 2016
 */
class DeviceScanner private constructor(
    private val context: Context,
    private val listener: Listener?,
    private val scanners: MutableList<Scanner>
) {
    interface Listener {
        fun onDeviceScanned(device: ScannedDevice)

        fun onDeviceChanged(device: ScannedDevice)

        fun onExceptionRaised(exception: Exception)

        fun onScanFinished()
    }

    interface Scanner {
        fun start(listener: Listener?)

        fun stop()

        val isFinished: Boolean
    }

    class Builder(context: Context) {
        private val context: Context = context.applicationContext
        private var listener: Listener? = null
        private val scanners: MutableList<Scanner> = ArrayList()

        fun listen(listener: Listener?): Builder {
            this.listener = listener
            return this
        }

        fun addBluetooth(): Builder {
            scanners.add(BluetoothScanner(context))
            return this
        }

        fun addKnownBluetooth(knownDeviceDataList: List<KnownBluetoothDeviceData>): Builder {
            scanners.add(KnownBluetoothDeviceScanner(knownDeviceDataList))
            return this
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        fun addBluetoothLe(): Builder {
            return addBluetoothLe(null, null)
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        fun addBluetoothLe(scanFilters: List<ScanFilter>?, scanSettings: ScanSettings?): Builder {
            scanners.add(
                BluetoothLeScanner(
                    context = context,
                    scanFilters = scanFilters,
                    scanSettings = scanSettings
                )
            )
            return this
        }

        fun addUsb(supportedProducts: List<Pair<Int, Int>>?): Builder {
            scanners.add(UsbScanner(context, supportedProducts))
            return this
        }

        fun build(): DeviceScanner {
            return DeviceScanner(context, listener, ArrayList(scanners))
        }
    }

    var isScanning: Boolean = false
        private set

    fun start(): Boolean {
        if (isScanning) {
            Log.w(
                LOG_TAG,
                "start() Scanning is in progress. Need to call stop() first and wait for onScanFinished() event."
            )
            return false
        } else if (listener == null) {
            Log.e(LOG_TAG, "Listener is not set!")
            return false
        }
        isScanning = true
        startScanners()
        return true
    }

    fun stop() {
        for (scanner in scanners) {
            scanner.stop()
        }

        isScanning = !scanners.all { it.isFinished }
    }

    private fun startScanners() {
        for (scanner in scanners) {
            scanner.start(object : Listener {
                override fun onDeviceScanned(device: ScannedDevice) {
                    listener?.onDeviceScanned(device)
                }

                override fun onDeviceChanged(device: ScannedDevice) {
                    listener?.onDeviceChanged(device)
                }

                override fun onScanFinished() {
                    Log.d(LOG_TAG, "onScanFinished check all finish...")
                    val notFinishedScanners =
                        scanners.filter { !it.isFinished }.takeIf { it.isNotEmpty() }
                            ?.joinToString(",") { it.javaClass.simpleName }
                    if (notFinishedScanners != null) {
                        Log.d(LOG_TAG, "onScanFinished not finish scanner. $notFinishedScanners")
                        return
                    }
                    Log.w(LOG_TAG, "onScanFinished all.")
                    this@DeviceScanner.isScanning = false
                    listener?.onScanFinished()
                }

                override fun onExceptionRaised(exception: Exception) {
                    Log.w(LOG_TAG, "onExceptionRaised. e: ${exception.message}")
                    listener?.onExceptionRaised(exception)
                }
            })
        }
    }

    fun addScanner(scanner: Scanner): Boolean {
        return scanners.add(scanner)
    }

    companion object {
        private const val LOG_TAG = "DeviceScanner"
    }
}
