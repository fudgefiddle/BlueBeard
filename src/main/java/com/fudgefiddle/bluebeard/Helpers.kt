package com.fudgefiddle.bluebeard

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

internal fun getBlueoothDeviceFromAddress(address: String): BluetoothDevice {
    return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
}