package com.neofect.devicescanner;

import android.bluetooth.BluetoothDevice;

/**
 * @author neo.kim@neofect.com
 * @date Nov 16, 2016
 */
public class BluetoothScannedDevice extends ScannedDevice {

	public BluetoothScannedDevice(String identifier, String name, String description, BluetoothDevice device) {
		super(identifier, name, description, device);
	}

	public BluetoothDevice getBluetoothDevice() {
		return (BluetoothDevice) getDevice();
	}

}
