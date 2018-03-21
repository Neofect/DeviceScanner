package com.neofect.devicescanner.usb;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.util.Pair;

import com.neofect.devicescanner.DeviceScanner.Listener;
import com.neofect.devicescanner.DeviceScanner.Scanner;
import com.neofect.devicescanner.ScannedDevice;

import java.util.HashMap;
import java.util.List;

/**
 * @author neo.kim@neofect.com
 * @date Nov 16, 2016
 */
public class UsbScanner implements Scanner {

	public static class UsbScannedDevice extends ScannedDevice {
		public UsbScannedDevice(String identifier, String name, String description, UsbDevice device) {
			super(identifier, name, description, device);
		}
		public UsbDevice getUsbDevice() {
			return (UsbDevice) getDevice();
		}
	}

	private Context context;
	private boolean stopped = false;
	private boolean finished = false;
	private List<Pair<Integer, Integer>> supportedProducts;

	public UsbScanner(Context context, List<Pair<Integer, Integer>> supportedProducts) {
		this.context = context;
		this.supportedProducts = supportedProducts;
	}

	@Override
	public void start(final Listener listener) {
		stopped = false;
		finished = false;

		final Handler handler = new Handler();
		Runnable runnable = () -> {
			UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
			HashMap<String, UsbDevice> devices = usbManager.getDeviceList();

			for (UsbDevice device : devices.values()) {
				if (stopped) {
					break;
				} else if (!isSupportedProduct(device)) {
					continue;
				}
				String identifier = device.getDeviceName();
				String name;
				String description;

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					name = device.getProductName();
					description = name + "(";
					description += "name=" + device.getProductName();
					description += ")";
				} else {
					name = identifier;
					description = name + " (";
					description += "vendor=" + shortToHex((short) device.getVendorId());
					description += ", product=" + shortToHex((short) device.getProductId());
					description += ")";
				}
				final ScannedDevice scannedDevice = new UsbScannedDevice(identifier, name, description, device);
				handler.post(()-> listener.onDeviceScanned(scannedDevice));
			}

			handler.post(() -> {
				finished = true;
				listener.onScanFinished();
			});
		};
		new Thread(runnable).start();
	}

	@Override
	public void stop() {
		stopped = true;
	}

	@Override
	public boolean isFinished() {
		return finished;
	}

	private boolean isSupportedProduct(UsbDevice device) {
		if (supportedProducts == null) {
			return true;
		}

		int vendorId = device.getVendorId();
		int productId = device.getProductId();
		for (Pair<Integer, Integer> filter : supportedProducts) {
			if (filter.first == vendorId) {
				if (filter.second == null || filter.second == productId) {
					return true;
				}
			}
		}
		return false;
	}

	private static String shortToHex(short value) {
		String hex = "0x";
		hex += byteToHex((byte) (value >> 8 & 0xff));
		hex += byteToHex((byte) (value & 0xff));
		return hex;
	}

	private static String byteToHex(byte value) {
		String hex = Integer.toHexString(0xff & value).toUpperCase();
		if (hex.length() == 1) {
			hex = "0" + hex;
		}
		return hex;
	}

}
