package com.neofect.devicescanner.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.neofect.devicescanner.DeviceScanner
import com.neofect.devicescanner.DeviceScanner.Scanner
import com.neofect.devicescanner.ScannedDevice
import com.neofect.devicescanner.bluetooth.BluetoothLeScanner.BluetoothLeScannedDevice
import com.neofect.devicescanner.bluetooth.BluetoothScanner.BluetoothScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Created by jhchoi on 2/27/24
 * jhchoi@neofect.com
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class KnownBluetoothDeviceScanner(
    context: Context
) : Scanner {
    private val knownDeviceList = mutableListOf<KnownBluetoothDeviceData>()
    private var listener: DeviceScanner.Listener? = null
    private var scanJob: Job? = null
    private val coroutineContext = SupervisorJob() + Dispatchers.Default
    private val context = context.applicationContext
    private var receiverRegistered: Boolean = false

    companion object {
        private const val LOG_TAG = "KnownBtDeviceScanner"
    }

    fun prepare(deviceList: List<KnownBluetoothDeviceData>) {
        knownDeviceList.addAll(deviceList)
    }

    @Synchronized
    override fun start(listener: DeviceScanner.Listener?) {
        this.listener = listener

        unregisterReceiver()
        registerReceiver()

        scanJob?.cancel()
        scanJob = CoroutineScope(coroutineContext).launch {
            try {
                Log.d(LOG_TAG, "scan start.")
                for (knownDeviceInfo in knownDeviceList) {
                    for (tryCount in 0 until 10) {
                        val device =
                            BluetoothAdapter.getDefaultAdapter()
                                .getRemoteDevice(knownDeviceInfo.macAddress)
                        Log.d(
                            LOG_TAG,
                            "device name requested. deviceName=${device.name}, deviceAddr=${device.address}"
                        )
                        if (device.name == null) {
                            device.fetchUuidsWithSdp()
                            delay(1000)
                            continue
                        }
                        val scannedDevice: ScannedDevice = createScannedDevice(device)
                        withContext(Dispatchers.Main) {
                            listener?.onDeviceChanged(scannedDevice)
                        }
                        break
                    }
                }
                //device name 요청 대기.
                delay(10000)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "device scan failed. ${e.message}")
                e.printStackTrace()
            } finally {
                Log.d(LOG_TAG, "scan finish.")
                unregisterReceiver()
                listener?.onScanFinished()
            }
        }
    }

    private fun createScannedDevice(device: BluetoothDevice): ScannedDevice {
        val description = "${device.name} (${device.address})"
        return if (device.type == BluetoothDevice.DEVICE_TYPE_LE || device.type == BluetoothDevice.DEVICE_TYPE_DUAL)
            BluetoothLeScannedDevice(
                device.address, device.name, description, device
            )
        else BluetoothScannedDevice(device).apply {
            this.description = description
        }
    }


    private fun registerReceiver() {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_UUID)
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED)

        context.registerReceiver(receiver, filter)
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "receiver unregister failed. ${e.message}")
            }
            receiverRegistered = false
        }
    }


    @Synchronized
    override fun stop() {
        Log.d(LOG_TAG, "stop.")
        scanJob?.takeIf { it.isActive }?.cancel()
        scanJob = null
        unregisterReceiver()
    }

    override val isFinished: Boolean
        get() = scanJob?.isActive != true

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val intentDevice =
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            val device =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice(intentDevice.address)
            Log.d(
                LOG_TAG,
                "Bluetooth discovery action received. action=$action, deviceName=${device.name}, deviceAddr=${device.address}"
            )

            val scannedDevice = when (action) {
                BluetoothDevice.ACTION_UUID,
                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    if (device.name == null) {
                        null
                    } else {
                        createScannedDevice(device)
                    }
                }

                else -> {
                    null
                }
            }
            if (scannedDevice == null) {
                device.fetchUuidsWithSdp()
                return
            }

            listener?.onDeviceChanged(scannedDevice)
        }
    }
}
