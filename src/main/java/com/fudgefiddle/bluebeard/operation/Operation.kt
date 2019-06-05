package com.fudgefiddle.bluebeard.operation

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Parcel
import android.os.Parcelable
import com.fudgefiddle.bluebeard.device_template.BleProperty
import com.fudgefiddle.bluebeard.device_template.BleProperty.Characteristic
import com.fudgefiddle.bluebeard.device_template.BleProperty.Descriptor
import com.fudgefiddle.bluebeard.getBlueoothDeviceFromAddress
import java.util.*


sealed class Operation() {
    abstract val device: BluetoothDevice
    internal abstract val timeout: Long
    internal abstract val delay: Long

    data class Connect(override val device: BluetoothDevice, override val timeout: Long = 0L, override val delay: Long = 0L) : Operation() {
        constructor(address: String, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), timeout, delay)
    }

    data class Discover(override val device: BluetoothDevice, override val timeout: Long = 0L, override val delay: Long = 0L) : Operation() {
        constructor(address: String, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), timeout, delay)
    }

    data class Disconnect(override val device: BluetoothDevice, override val timeout: Long = 0L, override val delay: Long = 0L) : Operation() {
        constructor(address: String, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), timeout, delay)
    }

    data class Read(override val device: BluetoothDevice, val characteristic: Characteristic, override val timeout: Long = 0L, override val delay: Long = 0L) : Operation() {
        constructor(device: BluetoothDevice, uuid: UUID, timeout: Long = 0L, delay: Long = 0L) : this(device, Characteristic(uuid), timeout, delay)
        constructor(address: String, characteristic: Characteristic, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), characteristic, timeout, delay)
        constructor(address: String, uuid: UUID, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), uuid, timeout, delay)
    }

    data class Write(override val device: BluetoothDevice, val characteristic: Characteristic, val value: ByteArray, override val timeout: Long = 0L, override val delay: Long = 0L) : Operation() {
        constructor(device: BluetoothDevice, uuid: UUID, value: ByteArray, timeout: Long = 0L, delay: Long = 0L) : this(device, Characteristic(uuid), value, timeout, delay)
        constructor(address: String, characteristic: Characteristic, value: ByteArray, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), characteristic, value, timeout, delay)
        constructor(address: String, uuid: UUID, value: ByteArray, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), uuid, value, timeout, delay)
    }

    data class WriteDescriptor(override val device: BluetoothDevice, val descriptor: Descriptor, val value: ByteArray, override val timeout: Long = 0L, override val delay: Long = 0L) : Operation() {
        constructor(device: BluetoothDevice, descriptorUuid: UUID, value: ByteArray, timeout: Long = 0L, delay: Long = 0L) : this(device, Descriptor(descriptorUuid), value, timeout, delay)
        constructor(address: String, descriptorUuid: UUID, value: ByteArray, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), Descriptor(descriptorUuid), value, timeout, delay)
        constructor(address: String, descriptor: Descriptor, value: ByteArray, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), descriptor, value, timeout, delay)
    }

    data class Notification(override val device: BluetoothDevice, val characteristic: Characteristic, val enable: Boolean, override val timeout: Long = 0L, override val delay: Long = 0L) : Operation() {
        constructor(device: BluetoothDevice, uuid: UUID, enable: Boolean, timeout: Long = 0L, delay: Long = 0L) : this(device, Characteristic(uuid), enable, timeout, delay)
        constructor(address: String, uuid: UUID, enable: Boolean, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), Characteristic(uuid), enable, timeout, delay)
        constructor(address: String, characteristic: Characteristic, enable: Boolean, timeout: Long = 0L, delay: Long = 0L) : this(getBlueoothDeviceFromAddress(address), characteristic, enable, timeout, delay)
    }

}





