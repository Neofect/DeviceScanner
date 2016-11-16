package com.neofect.devicescanner;

import android.hardware.usb.UsbDevice;

/**
 * @author neo.kim@neofect.com
 * @date Nov 16, 2016
 */
public class UsbScannedDevice extends ScannedDevice {

	public UsbScannedDevice(String identifier, String name, String description, UsbDevice device) {
		super(identifier, name, description, device);
	}

	public UsbDevice getUsbDevice() {
		return (UsbDevice) getDevice();
	}

}
