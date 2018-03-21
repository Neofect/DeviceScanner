package com.neofect.devicescanner.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import com.neofect.devicescanner.DeviceScanner.Listener;
import com.neofect.devicescanner.DeviceScanner.Scanner;
import com.neofect.devicescanner.ScannedDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * @author neo.kim@neofect.com
 * @date Nov 16, 2016
 */
@SuppressLint("MissingPermission")
public class BluetoothScanner implements Scanner {

	private static final String LOG_TAG = "BluetoothScanner";

	public static class BluetoothScannedDevice extends ScannedDevice {
		private int rssi;

		public BluetoothScannedDevice(String identifier, String name, String description, BluetoothDevice device) {
			this(identifier, name, description, device, -1);
		}
		public BluetoothScannedDevice(String identifier, String name, String description, BluetoothDevice device, int rssi) {
			super(identifier, name, description, device);
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
	private List<BluetoothDevice> scannedDevices;
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
		scannedDevices = new ArrayList<>();

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
				onDeviceDiscovered(device);
			} else if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
				BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				onDeviceDiscovered(bluetoothDevice);
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				finish(null);
			}
		}
	};

	private void onDeviceDiscovered(final BluetoothDevice device) {
		String deviceName = device.getName();
		Log.i(LOG_TAG, "Bluetooth device is discovered. name=" + deviceName + ", address=" + device.getAddress());

		// Check duplicates
		if (scannedDevices.contains(device)) {
			return;
		}
		scannedDevices.add(device);

		handler.post(() -> {
			String description = deviceName + " (" + device.getAddress() + ")";
			final ScannedDevice scannedDevice = new BluetoothScannedDevice(device.getAddress(), deviceName, description, device);
			listener.onDeviceScanned(scannedDevice);
		});
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
