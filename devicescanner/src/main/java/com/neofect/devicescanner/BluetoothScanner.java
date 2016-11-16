package com.neofect.devicescanner;

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

import java.util.ArrayList;
import java.util.List;

/**
 * @author neo.kim@neofect.com
 * @date Nov 16, 2016
 */
public class BluetoothScanner implements Scanner {

	private static final String LOG_TAG = BluetoothScanner.class.getSimpleName();

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

	public BluetoothScanner(Context context) {
		this.context = context;
	}

	@Override
	public void scan(final Listener listener) {
		this.listener = listener;
		handler = new Handler();
		scannedDevices = new ArrayList<>();

		if (!checkBluetoothAvailability()) {
			finish();
			return;
		}

		registerReceiver();

		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter.isDiscovering()) {
			adapter.cancelDiscovery();
		}
		adapter.startDiscovery();
	}

	private boolean checkBluetoothAvailability() {
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter == null) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					listener.onExceptionRaised(new Exception("Bluetooth is not supported by the device!"));
				}
			});
			return false;
		} else if (!adapter.isEnabled()) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					listener.onExceptionRaised(new Exception("Bluetooth adapter is not enabled!"));
				}
			});
			return false;
		} return true;
	}

	@Override
	public void stopScan() {
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter.isDiscovering()) {
			adapter.cancelDiscovery();
		}
	}

	@Override
	public boolean isFinished() {
		return finished;
	}

	private void finish() {
		context.unregisterReceiver(discoveryReceiver);
		handler.post(new Runnable() {
			@Override
			public void run() {
				finished = true;
				listener.onScanFinished();
			}
		});
	}

	private void registerReceiver() {
		// Register a receiver for broadcasts
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		context.registerReceiver(discoveryReceiver, filter);
	}

	private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.d(LOG_TAG, "Bluetooth broadcast action received. action=" + action);
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device.getName() != null) {
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
		Log.i(LOG_TAG, "Bluetooth device is discovered. name=" + device.getName() + ", address=" + device.getAddress());

		// Check duplicates
		if (scannedDevices.contains(device)) {
			return;
		}
		scannedDevices.add(device);

		handler.post(new Runnable() {
			@Override
			public void run() {
				String description = device.getName() + " (" + device.getAddress() + ")";
				final ScannedDevice scannedDevice = new BluetoothScannedDevice(device.getAddress(), device.getName(), description, device);
				handler.post(new Runnable() {
					@Override
					public void run() {
						listener.onDeviceScanned(scannedDevice);
					}
				});
			}
		});
	}

}
