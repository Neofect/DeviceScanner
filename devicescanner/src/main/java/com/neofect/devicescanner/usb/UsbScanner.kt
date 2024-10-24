package com.neofect.devicescanner.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.util.Pair
import com.neofect.devicescanner.DeviceScanner
import com.neofect.devicescanner.ScannedDevice

/**
 * @author neo.kim@neofect.com
 * @date Nov 16, 2016
 */
class UsbScanner(
    private val context: Context,
    private val supportedProducts: List<Pair<Int, Int>>?
) : DeviceScanner.Scanner {
    class UsbScannedDevice(
        identifier: String?,
        name: String?,
        description: String?,
        device: UsbDevice?
    ) : ScannedDevice(identifier, name, description, device) {
        val usbDevice: UsbDevice
            get() = device as UsbDevice
    }

    private val handler = Handler()
    private var stopped = false
    override var isFinished: Boolean = false
        private set

    override fun start(listener: DeviceScanner.Listener?) {
        stopped = false
        isFinished = false

        val runnable = Runnable {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val devices = usbManager.deviceList

            for (device in devices.values) {
                if (stopped) {
                    break
                } else if (!isSupportedProduct(device)) {
                    continue
                }
                val identifier = device.deviceName
                var name: String
                var description: String

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    name = device.productName ?: "-"
                    description = "$name("
                    description += "name=" + device.productName
                    description += ")"
                } else {
                    name = identifier
                    description = "$name ("
                    description += "vendor=" + shortToHex(device.vendorId.toShort())
                    description += ", product=" + shortToHex(device.productId.toShort())
                    description += ")"
                }
                val scannedDevice: ScannedDevice =
                    UsbScannedDevice(identifier, name, description, device)
                handler.post { listener?.onDeviceScanned(scannedDevice) }
            }
            handler.post {
                isFinished = true
                listener?.onScanFinished()
            }
        }
        Thread(runnable).start()
    }

    override fun stop() {
        stopped = true
    }

    private fun isSupportedProduct(device: UsbDevice): Boolean {
        if (supportedProducts == null) {
            return true
        }

        val vendorId = device.vendorId
        val productId = device.productId
        for (filter in supportedProducts) {
            if (filter.first == vendorId) {
                if (filter.second == null || filter.second == productId) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private fun shortToHex(value: Short): String {
            var hex = "0x"
            hex += byteToHex((value.toInt() shr 8 and 0xff).toByte())
            hex += byteToHex((value.toInt() and 0xff).toByte())
            return hex
        }

        private fun byteToHex(value: Byte): String {
            var hex = Integer.toHexString(0xff and value.toInt()).toUpperCase()
            if (hex.length == 1) {
                hex = "0$hex"
            }
            return hex
        }
    }
}
