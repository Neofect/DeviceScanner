package com.neofect.devicescanner;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * @author neo.kim@neofect.com
 * @date Nov 15, 2016
 */
public class DeviceScanner {

	private static final String LOG_TAG = DeviceScanner.class.getSimpleName();

	public interface Listener {
		void onDeviceScanned(ScannedDevice device);
		void onExceptionRaised(Exception exception);
		void onScanFinished();
	}

	public interface Scanner {
		void scan(Listener listener);
		void stopScan();
		boolean isFinished();
	}

	/**
	 * Do not reuse this builder.
	 */
	public static class DeviceScannerBuilder {
		private Context context;
		private Listener listener;
		private List<Scanner> scanners;

		public DeviceScannerBuilder(Context context, Listener listener) {
			this.context = context;
			this.listener = listener;
			scanners = new ArrayList<>();
		}

		public DeviceScannerBuilder addBluetoothType() {
			scanners.add(new BluetoothScanner(context));
			return this;
		}

		public DeviceScannerBuilder addUsbType(List<Pair<Integer, Integer>> supportedProducts) {
			scanners.add(new UsbScanner(context, supportedProducts));
			return this;
		}

		public DeviceScanner build() {
			return new DeviceScanner(this);
		}

	}

	private DeviceScannerBuilder builder;
	private boolean scanning = false;

	private DeviceScanner(DeviceScannerBuilder builder) {
		this.builder = builder;
	}

	public boolean isScanning() {
		return scanning;
	}

	public boolean scan() {
		if (scanning) {
			Log.w(LOG_TAG, "scan() Scanning is in progress. Need to call stopScan() first and wait for onScanFinished() event.");
			return false;
		}
		if (builder.listener == null) {
			Log.e(LOG_TAG, "Listener is not set!");
			return false;
		}
		scanning = true;
		startScanners();
		return true;
	}

	public void stopScan() {
		for (Scanner scanner : builder.scanners) {
			scanner.stopScan();
		}
	}

	private void startScanners() {
		for (Scanner scanner : builder.scanners) {
			scanner.scan(new Listener() {
				public void onDeviceScanned(ScannedDevice device) {
					builder.listener.onDeviceScanned(device);
				}

				public void onScanFinished() {
					for (Scanner finishedScanner : builder.scanners) {
						if (!finishedScanner.isFinished()) {
							return;
						}
					}
					scanning = false;
					builder.listener.onScanFinished();
				}

				@Override
				public void onExceptionRaised(Exception exception) {
					builder.listener.onExceptionRaised(exception);
				}
			});
		}
	}

}
