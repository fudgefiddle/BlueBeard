package com.fudgefiddle.bluebeard.operation

import android.bluetooth.BluetoothDevice
import java.util.*

class OperationQueue : ArrayDeque<Operation>() {
    override fun addFirst(element: Operation) {
        if (contains(element) && (element is Operation.Connect || element is Operation.Discover || element is Operation.Disconnect))
            return

        return super.addFirst(element)
    }

    override fun add(element: Operation?): Boolean {
        if (contains(element) && (element is Operation.Connect || element is Operation.Discover || element is Operation.Disconnect))
            return false

        return super.add(element)
    }

    override fun contains(element: Operation): Boolean {
        return !none{ op -> op.device.address == element.device.address }
    }

    fun removeAllWhere(func: (Operation) -> Boolean) {
        forEach { operation ->
            if (func(operation))
                remove(operation)
        }
    }

    fun removeAllForDevice(address: String){
        removeAllWhere{ op -> op.device.address == address }
    }

    fun removeAllForDevice(device: BluetoothDevice){
        removeAllWhere{ op -> op.device.address == device.address }
    }
}