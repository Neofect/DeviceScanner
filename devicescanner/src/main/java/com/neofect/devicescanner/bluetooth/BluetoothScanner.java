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
public class BluetoothScanner implements Scanner {

	private static final String LOG_TAG = "BluetoothScanner";

	public static class BluetoothScannedDevice extends ScannedDevice {
		public BluetoothScannedDevice(String identifier, String name, String description, BluetoothDevice device) {
			super(identifier, name, description, device);
		}
		public BluetoothDevice getBluetoothDevice() {
			return (BluetoothDevice) getDevice();
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

		if (!checkBluetoothAvailability()) {
			finish();
			return;
		}

		registerReceiver();
		startBluetoothDiscovery();
	}

	@SuppressLint("MissingPermission")
	private void startBluetoothDiscovery() {
		BluetoothAdapter.getDefaultAdapter().startDiscovery();
	}

	@SuppressLint("MissingPermission")
	private boolean checkBluetoothAvailability() {
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter == null) {
			handler.post(() -> listener.onExceptionRaised(new Exception("Bluetooth is not supported by the device!")));
			return false;
		} else if (!adapter.isEnabled()) {
			handler.post(() -> listener.onExceptionRaised(new Exception("Bluetooth adapter is not enabled!")));
			return false;
		}
		return true;
	}

	@SuppressLint("MissingPermission")
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

	private void finish() {
		if (receiverRegistered) {
			context.unregisterReceiver(discoveryReceiver);
			receiverRegistered = false;
		}
		handler.post(() -> {
			finished = true;
			listener.onScanFinished();
		});
	}

	private void registerReceiver() {
		if (receiverRegistered) {
			Log.w(LOG_TAG, "registerReceiver() Already registered.");
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
				if (getDeviceName(device) != null) {
					onDeviceDiscovered(device);
				} else {
					Log.d(LOG_TAG, "The name of bluetooth device is null! device=" + device);
				}
			} else if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
				BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				onDeviceDiscovered(bluetoothDevice);
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				finish();
			}
		}
	};

	private void onDeviceDiscovered(final BluetoothDevice device) {
		String deviceName = getDeviceName(device);
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

	@SuppressLint("MissingPermission")
	private String getDeviceName(BluetoothDevice device) {
		return device.getName();
	}

}
