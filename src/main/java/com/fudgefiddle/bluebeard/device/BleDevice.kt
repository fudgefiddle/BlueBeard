package com.fudgefiddle.bluebeard.device

import android.bluetooth.*
import android.content.Context
import java.util.*
import kotlin.String

internal class BleDevice(val btDevice: BluetoothDevice) {
    val name: String = btDevice.name
    val address: String = btDevice.address

    var connected = false
    var discovered = false

    var attempts = 0

    private var gatt: BluetoothGatt? = null

    fun connect(context: Context, callback: BluetoothGattCallback): Boolean {
        if (connected)
            return false

        gatt = btDevice.connectGatt(context, false, callback)
        return true
    }

    fun disconnect(): Boolean {
        if (!connected)
            return false

        gatt?.disconnect()
        return true
    }

    fun closeGatt() {
        gatt?.close()
        gatt = null
    }

    fun discoverServices(): Boolean {
        if (!connected)
            return false

        return gatt?.discoverServices() ?: false
    }

    fun read(uuid: String): Boolean{
        if(connected && discovered) {
            gatt?.apply{
                return findCharacteristic(uuid, ::readCharacteristic)
            }
        }
        return false
    }

    fun write(uuid: String, value: ByteArray): Boolean{
        if(connected && discovered) {
            gatt?.apply{
                setCharacteristicValue(uuid, value)
                return findCharacteristic(uuid, ::writeCharacteristic)
            }
        }
        return false
    }

    fun writeDescriptor(characteristicUuid: String, descriptorUuid: String, value: ByteArray): Boolean{
        if(connected && discovered) {
            gatt?.apply {
                return findCharacteristic(characteristicUuid){ characteristic ->
                    val descriptor: BluetoothGattDescriptor = characteristic.getDescriptor(UUID.fromString(descriptorUuid))
                    descriptor.value = value
                    writeDescriptor(descriptor)
                }
            }
        }
        return false
    }

    fun notify(uuid: String, enable: Boolean): Boolean{
        if(connected && discovered) {
            gatt?.apply{
                return findCharacteristic(uuid){ characteristic ->
                    setCharacteristicNotification(characteristic, enable)
                }
            }
        }
        return false
    }

    private fun findCharacteristic(uuid: String, andDo: (BluetoothGattCharacteristic) -> Boolean): Boolean {
        if (discovered) {
            gatt?.services?.forEach { svc ->
                svc.characteristics.find { char -> char.uuid.toString() == uuid }?.also {
                    return andDo(it)
                }
            }
        }
        return false
    }

    private fun setCharacteristicValue(uuid: String, value: ByteArray): Boolean{
        return findCharacteristic(uuid){ characteristic ->
            characteristic.value = value
            true
        }
    }
}