package com.neofect.devicescanner.bluetooth;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.neofect.devicescanner.DeviceScanner;
import com.neofect.devicescanner.DeviceScanner.Scanner;
import com.neofect.devicescanner.ScannedDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Neo on 2018/03/02.
 */

public class BluetoothLeScanner implements Scanner {

	private static final String LOG_TAG = "BluetoothLeScanner";

	private static final int SCAN_DURATION = 3000;

	private Context context;
	private Handler handler;
	private DeviceScanner.Listener listener;
	private List<BluetoothDevice> scannedDevices = new ArrayList<>();
	private boolean scanning = false;
	private boolean finished = false;

	private android.bluetooth.le.BluetoothLeScanner bleScanner;

	private BluetoothAdapter.LeScanCallback scanCallbackForOld = (device, rssi, scanRecord) -> onDeviceScanned(device, rssi);

	public BluetoothLeScanner(Context context) {
		this.context = context.getApplicationContext();
	}

	@Override
	public void start(DeviceScanner.Listener listener) {
		if (scanning) {
			Log.e(LOG_TAG, "start: Scanning is in progress!");
			return;
		}
		this.listener = listener;
		if (handler == null) {
			handler = new Handler();
		}
		finished = false;
		Exception unavailableReason = CommonLogic.checkBluetoothAvailability();
		if (unavailableReason != null) {
			finish(unavailableReason);
			return;
		} else if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			finish(new Exception("Bluetooth LE is not supported by the device!"));
			return;
		}

		startBleScan();
	}

	@SuppressLint("MissingPermission")
	@Override
	public void stop() {
		if (finished) {
			Log.w(LOG_TAG, "stop: Already finished.");
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			bleScanner.stopScan(scanCallback);
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
			adapter.stopLeScan(scanCallbackForOld);
		} else {
			finish(new Exception("Bluetooth LE is not supported for Android version " + Build.VERSION.SDK_INT));
			return;
		}
		finish(null);
	}

	@Override
	public boolean isFinished() {
		return finished;
	}

	private void finish(Exception exception) {
		scanning = false;
		handler.post(() -> {
			finished = true;
			if (exception == null) {
				listener.onScanFinished();
			} else {
				listener.onExceptionRaised(new Exception("Exception from BluetoothLeScanner", exception));
			}
		});
	}

	@SuppressLint("MissingPermission")
	private void startBleScan() {
		scannedDevices.clear();
		scanning = true;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			if (bleScanner == null) {
				bleScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
			}
			ScanSettings settings = new ScanSettings.Builder()
					.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
					.build();
			bleScanner.startScan(new ArrayList<>(), settings, scanCallback);
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
			adapter.startLeScan(scanCallbackForOld);
		} else {
			finish(new Exception("Bluetooth LE is not supported for Android version " + Build.VERSION.SDK_INT));
		}

		// Schedule to stop scanning
		handler.postDelayed(() -> stop(), SCAN_DURATION);
	}

	private void onDeviceScanned(final BluetoothDevice device, int rssi) {
		String deviceName = getDeviceName(device);
		if (deviceName == null) {
			Log.w(LOG_TAG, "The name of scanned BLE device is not set. Skip it. deviceAddress=" + device.getAddress());
			return;
		}
		Log.i(LOG_TAG, "Bluetooth LE device is scanned. name=" + deviceName + ", address=" + device.getAddress() + ", rssi=" + rssi);

		// Check duplicates
		if (scannedDevices.contains(device)) {
			return;
		}
		scannedDevices.add(device);

		handler.post(() -> {
			String description = deviceName + " (" + device.getAddress() + ")";
			final ScannedDevice scannedDevice = new BluetoothScanner.BluetoothScannedDevice(device.getAddress(), deviceName, description, device, true, rssi);
			listener.onDeviceScanned(scannedDevice);
		});
	}

	@SuppressLint("MissingPermission")
	private String getDeviceName(BluetoothDevice device) {
		return device.getName();
	}

	private ScanCallback scanCallback = new ScanCallback() {
		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			onDeviceScanned(result.getDevice(), result.getRssi());
		}

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		@Override
		public void onBatchScanResults(List<ScanResult> results) {
			for (ScanResult result : results) {
				onDeviceScanned(result.getDevice(), result.getRssi());
			}
		}

		@Override
		public void onScanFailed(int errorCode) {
			Log.e(LOG_TAG, "Bluetooth LE scan failed! errorCode=" + errorCode);
			String message;
			switch (errorCode) {
				case SCAN_FAILED_ALREADY_STARTED: message = "SCAN_FAILED_ALREADY_STARTED"; break;
				case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED: message = "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"; break;
				case SCAN_FAILED_FEATURE_UNSUPPORTED: message = "SCAN_FAILED_FEATURE_UNSUPPORTED"; break;
				case SCAN_FAILED_INTERNAL_ERROR: message = "SCAN_FAILED_INTERNAL_ERROR"; break;
				default: message = "UNKNOWN"; break;
			}
			finish(new Exception(message));
		}
	};

}
