package com.fudgefiddle.bluebeard.device

import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.content.Context
import com.fudgefiddle.bluebeard.device_template.BleProperty
import com.fudgefiddle.bluebeard.operation.Operation
import java.util.*

internal class BleDevice(private val btDevice: BluetoothDevice) {

    val name = btDevice.name
    val address = btDevice.address

    private var mConnected = false
    private var mDiscovered = false

    private lateinit var mGatt: BluetoothGatt

    fun isConnected(): Boolean = mConnected

    fun isDiscovered(): Boolean = mDiscovered

    fun onConnectionStateChange(status: Int, newState: Int){
        when(newState){
            BluetoothGatt.STATE_CONNECTED -> {
                mConnected = status == GATT_SUCCESS
            }
            BluetoothGatt.STATE_DISCONNECTED -> {
                mConnected = false
                mGatt.close()
            }
        }

        if(!mConnected)
            mDiscovered = false
    }

    fun onServicesDiscovered(status: Int){
        mDiscovered = status == GATT_SUCCESS
    }

    fun executeGattOperation(operation: Operation): Boolean = when(operation){
        is Operation.Disconnect -> disconnect()

        is Operation.Discover -> discoverServices()

        is Operation.Read -> read(operation.characteristic)

        is Operation.Write -> write(operation.characteristic, operation.value)

        is Operation.WriteDescriptor -> writeDescriptor(operation.descriptor, operation.value)

        is Operation.Notification -> setCharacteristicNotification(operation.characteristic, operation.enable)

        else -> false
    }

    fun connect(context: Context, callback: BluetoothGattCallback): Boolean {
        if(!mConnected)
            mGatt = btDevice.connectGatt(context, false, callback)

        return !mConnected
    }

    fun disconnect(): Boolean {
        if(mConnected)
            mGatt.disconnect()

        return mConnected
    }

    fun closeGatt() {
        mGatt.close()
    }

    fun discoverServices(): Boolean {
        return mGatt.discoverServices()
    }

    fun read(characteristic: BleProperty.Characteristic): Boolean{
        return mGatt.readCharacteristic(getCharacteristicFromUUID(characteristic.uuid))
    }

    fun write(characteristic: BleProperty.Characteristic, bytes: ByteArray): Boolean{
        return mGatt.writeCharacteristic(getCharacteristicFromUUID(characteristic.uuid).apply{ value = bytes})
    }

    fun writeDescriptor(descriptor: BleProperty.Descriptor, bytes: ByteArray): Boolean{
        return mGatt.writeDescriptor(getDescriptorFromUUID(descriptor.uuid).apply{ value = bytes })
    }

    fun setCharacteristicNotification(characteristic: BleProperty.Characteristic, enable: Boolean): Boolean{
        return mGatt.setCharacteristicNotification(getCharacteristicFromUUID(characteristic.uuid), enable)
    }

    private fun getCharacteristicFromUUID(uuid: UUID): BluetoothGattCharacteristic{
        mGatt.services.forEach{ svc ->
            svc.getCharacteristic(uuid)?.let{
                return it
            }
        }
        throw NoSuchElementException()
    }

    private fun getDescriptorFromUUID(uuid: UUID): BluetoothGattDescriptor{
        mGatt.services.forEach{ svc ->
            svc.characteristics.forEach{ char ->
                char.getDescriptor(uuid)?.let{
                    return it
                }
            }
        }
        throw NoSuchElementException()
    }
}