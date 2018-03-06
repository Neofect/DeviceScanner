package com.neofect.devicescanner.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;

/**
 * Created by Neo on 2018/03/02.
 */

class CommonLogic {

	@SuppressLint("MissingPermission")
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
