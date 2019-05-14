package com.example.bluebeard.callbacks

import android.bluetooth.BluetoothDevice
import java.util.*

interface SimpleGattCallback{
    fun onSuccess(device: BluetoothDevice, uuid: UUID? = null, result: Any? = null)
    fun onFailure(device: BluetoothDevice, uuid: UUID? = null)
}

