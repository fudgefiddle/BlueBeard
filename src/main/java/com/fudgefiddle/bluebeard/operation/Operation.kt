package com.example.bluebeard.operation

import android.bluetooth.BluetoothGattDescriptor
import com.example.bluebeard.device.BleDevice
import com.example.bluebeard.device_template.Characteristic

internal sealed class Operation(val device: BleDevice, val timeout: Long, val delay: Long ){

    class Connect(device: BleDevice, timeout: Long = 0L, delay: Long = 0L): Operation(device, timeout, delay)
    class Discover(device: BleDevice, timeout: Long = 0L, delay: Long = 0L): Operation(device, timeout, delay)
    class Disconnect(device: BleDevice, timeout: Long = 0L, delay: Long = 0L): Operation(device, timeout, delay)

    class Read(device: BleDevice, val uuid: String, timeout: Long, delay: Long): Operation(device, timeout, delay)
    class Write(device: BleDevice, val uuid: String, val value: ByteArray, timeout: Long, delay: Long): Operation(device, timeout, delay)
    class WriteDescriptor(device: BleDevice, val characteristicUuid: String, val descriptorUuid: String, val value: ByteArray, timeout: Long, delay: Long): Operation(device, timeout, delay)
    class Notify(device: BleDevice, val uuid: String, val enable: Boolean, timeout: Long, delay: Long): Operation(device, timeout, delay)
}





