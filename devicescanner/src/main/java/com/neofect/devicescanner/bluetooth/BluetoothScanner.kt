package com.neofect.devicescanner.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.neofect.devicescanner.DeviceScanner
import com.neofect.devicescanner.ScannedDevice

/**
 * @author neo.kim@neofect.com
 * @date Nov 16, 2016
 */
@SuppressLint("MissingPermission")
class BluetoothScanner(context: Context) : DeviceScanner.Scanner {
    class BluetoothScannedDevice @JvmOverloads constructor(
        device: BluetoothDevice,
        var rssi: Int = 0
    ) : ScannedDevice(device.address, device.name, null, device) {
        val bluetoothDevice: BluetoothDevice
            get() = device as BluetoothDevice
    }

    private val context: Context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var listener: DeviceScanner.Listener? = null
    private var scannedDevices = mutableMapOf<BluetoothDevice, BluetoothScannedDevice>()
    override var isFinished = false
        private set
    private var receiverRegistered = false

    override fun start(listener: DeviceScanner.Listener?) {
        Log.i(LOG_TAG, "start")
        this.listener = listener
        isFinished = false
        scannedDevices = LinkedHashMap()

        val unavailableReason = checkBluetoothAvailability()
        if (unavailableReason != null) {
            finish(unavailableReason)
            return
        }

        registerReceiver()

        BluetoothAdapter.getDefaultAdapter().startDiscovery()
    }

    override fun stop() {
        Log.i(LOG_TAG, "stop")
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null && adapter.isDiscovering) {
            adapter.cancelDiscovery()
        } else {
            isFinished = true
        }
    }

    private fun finish(exception: Exception?) {
        if (receiverRegistered) {
            Log.i(LOG_TAG, "discovery receiver removed")
            context.unregisterReceiver(discoveryReceiver)
            receiverRegistered = false
        }

        handler.post {
            isFinished = true
            if (exception == null) {
                listener?.onScanFinished()
            } else {
                listener?.onExceptionRaised(
                    Exception(
                        "Exception from BluetoothScanner",
                        exception
                    )
                )
            }
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) {
            Log.w(LOG_TAG, "registerReceiver: Already registered.")
            return
        }

        // Register a receiver for broadcasts
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        context.registerReceiver(discoveryReceiver, filter)

        Log.i(LOG_TAG, "discovery receiver registered")
        receiverRegistered = true
    }

    private val discoveryReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        private val unknownNearDeviceMacAddrs = mutableListOf<String>()

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(LOG_TAG, "Bluetooth discovery action received. action=$action")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

            // Device type
            if (device != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                val deviceType = device.type
                if (deviceType != BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                    Log.d(
                        LOG_TAG,
                        "Only accept classic bluetooth devices. Skip this type of device. deviceType=$deviceType"
                    )
                    return
                }
            }

            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val rssi = intent.getShortExtra(
                        BluetoothDevice.EXTRA_RSSI, 0.toByte().toShort()
                    ).toInt()
                    Log.d(
                        LOG_TAG,
                        "Bluetooth device is found. deviceName=${device.name}, deviceAddr=${device.address}, rssi=$rssi"
                    )
                    onDeviceFound(device, rssi)
                }


                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    Log.d(
                        LOG_TAG,
                        "Bluetooth device name changed. deviceName=${device.name}, deviceAddr=${device.address}"
                    )
                    onDeviceNameChanged(device)
                }


                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(LOG_TAG, "Bluetooth discovery finished")
                    finish(null)
                }
            }
        }
    }

    private fun createDescription(device: BluetoothDevice): String {
        return "${device.name} (${device.address})"
    }

    private fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
        val deviceName = device.name
        Log.i(
            LOG_TAG,
            "Bluetooth device is found. name=$deviceName, address=${device.address}, rssi=$rssi"
        )
        val scannedDevice = addOrUpdateScannedDevice(device)
        scannedDevice.rssi = rssi
        handler.post { listener?.onDeviceScanned(scannedDevice) }
    }

    private fun onDeviceNameChanged(device: BluetoothDevice) {
        val deviceName = device.name
        Log.i(
            LOG_TAG,
            "Bluetooth device name changed. name=$deviceName, address=${device.address}"
        )
        val scannedDevice: ScannedDevice = addOrUpdateScannedDevice(device)
        handler.post { listener?.onDeviceChanged(scannedDevice) }
    }

    private fun addOrUpdateScannedDevice(device: BluetoothDevice): BluetoothScannedDevice {
        var scannedDevice = scannedDevices[device]
        if (scannedDevice == null) {
            scannedDevice = BluetoothScannedDevice(device)
            scannedDevices[device] = scannedDevice
        }
        scannedDevice.name = device.name
        scannedDevice.description = createDescription(device)
        return scannedDevice
    }

    companion object {
        private const val LOG_TAG = "BluetoothScanner"

        fun checkBluetoothAvailability(): Exception? {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                return Exception("Bluetooth is not supported by the device!")
            } else if (!adapter.isEnabled) {
                return Exception("Bluetooth adapter is not enabled!")
            }
            return null
        }
    }
}
