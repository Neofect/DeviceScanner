package com.neofect.devicescanner.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.neofect.devicescanner.DeviceScanner.Listener;
import com.neofect.devicescanner.DeviceScanner.Scanner;
import com.neofect.devicescanner.ScannedDevice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author neo.kim@neofect.com
 * @date Nov 16, 2016
 */
@SuppressLint("MissingPermission")
public class BluetoothScanner implements Scanner {

	private static final String LOG_TAG = "BluetoothScanner";

	public static class BluetoothScannedDevice extends ScannedDevice {
		private int rssi;

		public BluetoothScannedDevice(BluetoothDevice device) {
			this(device, -1);
		}
		public BluetoothScannedDevice(BluetoothDevice device, int rssi) {
			super(device.getAddress(), device.getName(), null, device);
			this.rssi = rssi;
		}
		public BluetoothDevice getBluetoothDevice() {
			return (BluetoothDevice) getDevice();
		}

		public int getRssi() {
			return rssi;
		}
	}

	private Context context;
	private Handler handler;
	private Listener listener;
	private Map<BluetoothDevice, ScannedDevice> scannedDevices;
	private boolean finished = false;
	private boolean receiverRegistered = false;

	public BluetoothScanner(Context context) {
		this.context = context.getApplicationContext();
	}

	@Override
	public void start(final Listener listener) {
		this.listener = listener;
		finished = false;
		handler = new Handler();
		scannedDevices = new LinkedHashMap<>();

		Exception unavailableReason = checkBluetoothAvailability();
		if (unavailableReason != null) {
			finish(unavailableReason);
			return;
		}

		registerReceiver();


		BluetoothAdapter.getDefaultAdapter().startDiscovery();
	}

	@Override
	public void stop() {
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter != null && adapter.isDiscovering()) {
			adapter.cancelDiscovery();
		}
	}

	@Override
	public boolean isFinished() {
		return finished;
	}

	private void finish(Exception exception) {
		if (receiverRegistered) {
			context.unregisterReceiver(discoveryReceiver);
			receiverRegistered = false;
		}

		handler.post(() -> {
			finished = true;
			if (exception == null) {
				listener.onScanFinished();
			} else {
				listener.onExceptionRaised(new Exception("Exception from BluetoothScanner", exception));
			}
		});
	}

	private void registerReceiver() {
		if (receiverRegistered) {
			Log.w(LOG_TAG, "registerReceiver: Already registered.");
			return;
		}
		// Register a receiver for broadcasts
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		context.registerReceiver(discoveryReceiver, filter);
		receiverRegistered = true;
	}

	private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.d(LOG_TAG, "Bluetooth broadcast action received. action=" + action);
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				onDeviceFound(device);
			} else if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				onDeviceNameChanged(device);
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				finish(null);
			}
		}
	};

	@NonNull
	private String createDescription(BluetoothDevice device) {
		return device.getName() + " (" + device.getAddress() + ")";
	}

	private void onDeviceFound(final BluetoothDevice device) {
		String deviceName = device.getName();
		Log.i(LOG_TAG, "Bluetooth device is found. name=" + deviceName + ", address=" + device.getAddress());
		ScannedDevice scannedDevice = addOrUpdateScannedDevice(device);
		handler.post(() -> listener.onDeviceScanned(scannedDevice));
	}

	private void onDeviceNameChanged(final BluetoothDevice device) {
		String deviceName = device.getName();
		Log.i(LOG_TAG, "Bluetooth device name changed. name=" + deviceName + ", address=" + device.getAddress());
		ScannedDevice scannedDevice = addOrUpdateScannedDevice(device);
		handler.post(() -> listener.onDeviceChanged(scannedDevice));
	}

	private ScannedDevice addOrUpdateScannedDevice(BluetoothDevice device) {
		ScannedDevice scannedDevice = scannedDevices.get(device);
		if (scannedDevice == null) {
			scannedDevice = new BluetoothScannedDevice(device);
			scannedDevices.put(device, scannedDevice);
		}
		scannedDevice.setName(device.getName());
		scannedDevice.setDescription(createDescription(device));
		return scannedDevice;
	}

	static Exception checkBluetoothAvailability() {
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter == null) {
			return new Exception("Bluetooth is not supported by the device!");
		} else if (!adapter.isEnabled()) {
			return new Exception("Bluetooth adapter is not enabled!");
		}
		return null;
	}

}
