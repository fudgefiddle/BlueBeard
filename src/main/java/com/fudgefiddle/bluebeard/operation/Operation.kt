package com.fudgefiddle.bluebeard.operation

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Parcel
import android.os.Parcelable
import com.fudgefiddle.bluebeard.getBlueoothDeviceFromAddress
import java.util.*


sealed class Operation(val device: BluetoothDevice, internal val timeout: Long, internal val delay: Long) {

    class Connect(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L) : Operation(device, timeout, delay){
        constructor(address: String, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), timeout, delay)
    }

    class Discover(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L) : Operation(device, timeout, delay){
        constructor(address: String, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), timeout, delay)
    }

    class Disconnect(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L) : Operation(device, timeout, delay){
        constructor(address: String, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), timeout, delay)
    }

    class Read(device: BluetoothDevice, val uuid: UUID, timeout: Long = 0L, delay: Long = 0L) :
            Operation(device, timeout, delay){
        constructor(address: String, uuid: UUID, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), uuid, timeout, delay)
    }

    class Write(device: BluetoothDevice, val uuid: UUID, val value: ByteArray, timeout: Long = 0L, delay: Long = 0L) :
            Operation(device, timeout, delay){
        constructor(address: String, uuid: UUID, value: ByteArray, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), uuid, value, timeout, delay)
    }

    class WriteDescriptor(device: BluetoothDevice, val characteristicUuid: UUID, val descriptorUuid: UUID, val value: ByteArray, timeout: Long = 0L, delay: Long = 0L) :
            Operation(device, timeout, delay){
        constructor(address: String, characteristicUuid: UUID, descriptorUuid: UUID, value: ByteArray, timeout: Long = 0L, delay: Long = 0L) :
                this(getBlueoothDeviceFromAddress(address), characteristicUuid, descriptorUuid, value, timeout, delay)
    }

    class Notification(device: BluetoothDevice, val uuid: UUID, val enable: Boolean, timeout: Long = 0L, delay: Long = 0L) :
            Operation(device, timeout, delay){
        constructor(address: String, uuid: UUID, enable: Boolean, timeout: Long = 0L, delay: Long = 0L) :
                this(getBlueoothDeviceFromAddress(address), uuid, enable, timeout, delay)
    }
    
}





