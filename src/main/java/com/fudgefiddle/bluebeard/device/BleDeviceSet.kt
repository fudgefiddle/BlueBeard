package com.fudgefiddle.bluebeard.device

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

class BleDeviceSet {
    private val mSet = mutableSetOf<BleDevice>()

    internal fun get(address: String): BleDevice{
        return mSet.find{ dev -> dev.address == address } ?: throw NoSuchElementException()
    }

    internal fun get(device: BluetoothDevice): BleDevice{
        return get(device.address)
    }

    fun add(device: BluetoothDevice): Boolean{
        val newDevice = BleDevice(device)
        return mSet.add(newDevice)
    }

    fun add(address: String): Boolean{
        return BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)?.let{
             add(it)
        } ?: false
    }

    fun contains(address: String): Boolean{
        return !mSet.none{ device -> device.address == address }
    }

    fun contains(device: BluetoothDevice): Boolean{
        return contains(device.address)
    }

    internal fun contains(device: BleDevice): Boolean{
        return contains(device.address)
    }
}