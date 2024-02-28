package com.neofect.devicescanner.bluetooth

interface KnownBluetoothDeviceScannerDelegate {
    fun knownList() :List<KnownBluetoothDeviceData>
}

data class KnownBluetoothDeviceData(
    val macAddress: String
)


