package com.neofect.devicescanner.bluetooth;

import android.annotation.SuppressLint;
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
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.neofect.devicescanner.DeviceScanner;
import com.neofect.devicescanner.DeviceScanner.Scanner;
import com.neofect.devicescanner.ScannedDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Neo on 2018/03/02.
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
@SuppressLint("MissingPermission")
public class BluetoothLeScanner implements Scanner {

	private static final String LOG_TAG = "BluetoothLeScanner";

	private static final int SCAN_DURATION = 3000;

	public static class BluetoothLeScannedDevice extends ScannedDevice {
		private ScanResult scanResult;
		public BluetoothLeScannedDevice(String identifier, String name, String description, BluetoothDevice device, ScanResult scanResult) {
			super(identifier, name, description, device);
			this.scanResult = scanResult;
		}
		public BluetoothDevice getBluetoothDevice() {
			return (BluetoothDevice) getDevice();
		}

		public int getRssi() {
			return scanResult.getRssi();
		}
	}

	private Context context;
	private Handler handler;
	private DeviceScanner.Listener listener;
	private List<BluetoothDevice> scannedDevices = new ArrayList<>();
	private boolean scanning = false;
	private boolean finished = false;
	private List<ScanFilter> scanFilters;
	private ScanSettings scanSettings;
	private android.bluetooth.le.BluetoothLeScanner bleScanner;

	public BluetoothLeScanner(Context context, List<ScanFilter> scanFilters, ScanSettings scanSettings) {
		this.context = context.getApplicationContext();
		if (scanFilters != null) {
			this.scanFilters = scanFilters;
		} else {
			this.scanFilters = new ArrayList<>();
		}
		if (scanSettings != null) {
			this.scanSettings = scanSettings;
		} else {
			this.scanSettings = createDefaultScanSettings();
		}
	}

	@Override
	public void start(DeviceScanner.Listener listener) {
		this.listener = listener;
		if (handler == null) {
			handler = new Handler();
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
			finish(new Exception("Bluetooth LE is not supported for Android version " + Build.VERSION.SDK_INT));
			return;
		} else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			finish(new Exception("DeviceScanner does not support BLE for Android version " + Build.VERSION.SDK_INT));
			return;
		} else if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			finish(new Exception("Bluetooth LE is not supported by the device!"));
			return;
		}

		if (scanning) {
			Log.e(LOG_TAG, "start: Scanning is in progress!");
			return;
		}
		finished = false;
		Exception unavailableReason = BluetoothScanner.checkBluetoothAvailability();
		if (unavailableReason != null) {
			finish(unavailableReason);
			return;
		}

		startBleScan();
	}

	@Override
	public void stop() {
		if (finished) {
			Log.w(LOG_TAG, "stop: Already finished.");
			return;
		}
		if (bleScanner != null) {
			bleScanner.stopScan(scanCallback);
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

	private void startBleScan() {
		if (bleScanner == null) {
			bleScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
		}
		scannedDevices.clear();
		scanning = true;

		bleScanner.startScan(scanFilters, scanSettings, scanCallback);

		// Schedule to stop scanning
		handler.postDelayed(this::stop, SCAN_DURATION);
	}

	private static ScanSettings createDefaultScanSettings() {
		return new ScanSettings.Builder()
					.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
					.build();
	}

	private void onDeviceScanned(ScanResult scanResult) {
		BluetoothDevice device = scanResult.getDevice();
		int rssi = scanResult.getRssi();
		String deviceName = device.getName();
		Log.i(LOG_TAG, "Bluetooth LE device is scanned. name=" + deviceName + ", address=" + device.getAddress() + ", rssi=" + rssi);

		// Check duplicates
		if (scannedDevices.contains(device)) {
			return;
		}
		scannedDevices.add(device);

		handler.post(() -> {
			String description = deviceName + " (" + device.getAddress() + ")";
			final ScannedDevice scannedDevice = new BluetoothLeScannedDevice(device.getAddress(), deviceName, description, device, scanResult);
			listener.onDeviceScanned(scannedDevice);
		});
	}

	private ScanCallback scanCallback = new ScanCallback() {
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			onDeviceScanned(result);
		}

		@Override
		public void onBatchScanResults(List<ScanResult> results) {
			for (ScanResult result : results) {
				onDeviceScanned(result);
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
