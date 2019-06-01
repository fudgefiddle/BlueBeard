package com.fudgefiddle.bluebeard

import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.fudgefiddle.bluebeard.device.BleDevice
import com.fudgefiddle.bluebeard.device_template.BleProperty.Characteristic
import com.fudgefiddle.bluebeard.device_template.BleProperty.Descriptor
import com.fudgefiddle.bluebeard.device_template.DeviceTemplate
import com.fudgefiddle.bluebeard.operation.Operation
import com.fudgefiddle.bluebeard.operation.ReturnOperation
import java.util.*


class BlueBeardService : Service() {

    //region PROPERTIES
    /** BlueBeardService Context */
    private val mContext: Context = this

    private val mDeviceTemplates = mutableSetOf<DeviceTemplate>()
    private val mBleDevices = mutableSetOf<BleDevice>()
    private val mOperations = object : ArrayDeque<Operation>() {
        override fun add(element: Operation?): Boolean {
            if (contains(element) && (element is Operation.Connect || element is Operation.Discover || element is Operation.Disconnect))
                return false

            return super.add(element)
        }

        fun removeAllWhere(func: (Operation) -> Boolean) {
            forEach { operation ->
                if (func(operation))
                    remove(operation)
            }
        }
    }

    private lateinit var mLastOperation: Operation

    private fun executeNextOperation() {
        if (mOperations.isNotEmpty()) {
            mLastOperation = mOperations.pop().also { operation ->
                val delay = operation.delay
                val timeout = operation.timeout

                // Look for the device in our set
                getBleDeviceFromAddress(operation.address)?.apply {
                    attempts++

                    when {
                        // Exceeded attempt limit
                        (mAttemptLimit != 0 && attempts >= mAttemptLimit) -> {
                            // Reset attempts else infinite loop
                            attempts = 0
                            // Remove all pending operations for this address
                            mOperations.removeAllWhere { op -> op.address == address }
                            // Disconnect from the address
                            mOperations.add(Operation.Disconnect(address))
                            // Move on
                            executeNextOperation()
                        }
                        // NOT exceeded attempt limit; NOT connected
                        !connected -> {
                            when (operation) {
                                is Operation.Connect -> delay(delay) { connect(mContext, mGattCallback) }

                                is Operation.Disconnect -> onOperationCompleteSuccess(address)

                                else -> {
                                    // Make sure we have no other connect operations for this to work properly
                                    mOperations.removeAllWhere { op -> op.address == address && op is Operation.Connect }

                                    // Get any other future operations, remove them, put a connection at the front, put the rest behind
                                    mOperations.filter { op -> op.address == address && op !is Operation.Connect }.also { list ->
                                        mOperations.removeAll(list)
                                        mOperations.add(Operation.Connect(address))
                                        mOperations.addAll(list)
                                    }

                                    onOperationCompleteSuccess(address)
                                }
                            }
                        }
                        // NOT exceeded attempt limit; IS connected; NOT discovered
                        !discovered -> {
                            when (operation) {
                                is Operation.Connect -> onOperationCompleteSuccess(address)

                                is Operation.Disconnect -> delay(delay) { disconnect() }

                                is Operation.Discover -> delay(delay) { discoverServices() }

                                else -> {
                                    // Make sure we have no other discover operations for this to work properly
                                    mOperations.removeAllWhere { op -> op.address == address && op is Operation.Discover }

                                    // Get any other future operations, remove them, put a discover at the front, put the rest behind
                                    mOperations.filter { op -> op.address == address && op !is Operation.Discover }.also { list ->
                                        mOperations.removeAll(list)
                                        mOperations.add(Operation.Discover(address))
                                        mOperations.addAll(list)
                                    }

                                    onOperationCompleteSuccess(address)
                                }
                            }
                        }
                        // NOT exceeded attempt limit; IS connected; IS discovered
                        else -> {
                            when (operation) {
                                is Operation.Read -> delay(delay) { read(operation.uuid) }

                                is Operation.Write -> delay(delay) { write(operation.uuid, operation.value) }

                                is Operation.WriteDescriptor -> delay(delay) { writeDescriptor(operation.characteristicUuid, operation.descriptorUuid, operation.value) }

                                is Operation.Notification -> delay(delay) { enableNotifications(operation.uuid, operation.enable) }

                                is Operation.Disconnect -> delay(delay) { disconnect() }

                                else -> onOperationCompleteSuccess(address)
                            }
                        }
                    }
                }

            }
        }
    }

    fun onOperationCompleteSuccess(address: String) {
        getBleDeviceFromAddress(address)?.attempts = 0
        executeNextOperation()
    }

    private fun getBleDeviceFromAddress(address: String?): BleDevice? {
        return mBleDevices.find { it.address == address }
    }

    private fun delay(delay: Long, func: () -> Boolean) {
        if (delay > 0) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!func())
                    executeNextOperation()
            }, delay)
        } else {
            if (!func())
                executeNextOperation()
        }
    }

    private var mAttemptLimit = 0

    private var mAutoReconnect: Boolean = false

    private fun shouldContinue(address: String) {
        if (mLastOperation.address == address)
            executeNextOperation()
    }

    /** Gatt Callbacks */
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            gatt?.device?.let { btDevice ->
                val device = getBleDeviceFromAddress(btDevice.address)
                val intent = Intent().also {
                    it.putExtra(EXTRA_STATUS, status)
                    it.putExtra(EXTRA_OPERATION, ReturnOperation(btDevice.address))
                }
                when (newState) {
                    STATE_CONNECTED -> {
                        device?.connected = status == GATT_SUCCESS
                        intent.action = ACTION_CONNECT
                    }
                    STATE_DISCONNECTED -> {
                        device?.connected = false
                        device?.discovered = false
                        device?.closeGatt()
                        intent.action = ACTION_DISCONNECT
                    }
                }

                mContext.sendBroadcast(intent)

                shouldContinue(btDevice.address)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            gatt?.device?.let { btDevice ->
                getBleDeviceFromAddress(btDevice.address)?.discovered = status == GATT_SUCCESS

                Intent().apply {
                    action = ACTION_DISCOVER
                    putExtra(EXTRA_STATUS, status)
                    putExtra(EXTRA_OPERATION, ReturnOperation(btDevice.address))
                }.also(mContext::sendBroadcast)

                shouldContinue(btDevice.address)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?,
                                          characteristic: BluetoothGattCharacteristic?,
                                          status: Int) {
            gatt?.device?.let { btDevice ->
                characteristic?.let { char ->
                    val myCharacteristic = getCharacteristicFromUUID(char.uuid)

                    Intent().apply {
                        action = ACTION_READ
                        putExtra(EXTRA_STATUS, status)
                        putExtra(EXTRA_OPERATION, ReturnOperation(btDevice.address, myCharacteristic, value = char.value))
                    }.also(mContext::sendBroadcast)

                    shouldContinue(btDevice.address)
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?,
                                           characteristic: BluetoothGattCharacteristic?,
                                           status: Int) {
            gatt?.device?.let { btDevice ->
                characteristic?.let { char ->
                    val myCharacteristic = getCharacteristicFromUUID(char.uuid)

                    Intent().apply {
                        action = ACTION_READ
                        putExtra(EXTRA_STATUS, status)
                        putExtra(EXTRA_OPERATION, ReturnOperation(btDevice.address, myCharacteristic, value = char.value))
                    }.also(mContext::sendBroadcast)

                    shouldContinue(btDevice.address)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            gatt?.device?.let { btDevice ->
                characteristic?.let { char ->
                    val myCharacteristic = getCharacteristicFromUUID(char.uuid)

                    Intent().apply {
                        action = ACTION_NOTIFICATION
                        putExtra(EXTRA_STATUS, -1)
                        putExtra(EXTRA_OPERATION, ReturnOperation(btDevice.address, myCharacteristic, value = char.value))
                    }.also(mContext::sendBroadcast)

                    shouldContinue(btDevice.address)
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            gatt?.device?.let { btDevice ->
                descriptor?.let { desc ->
                    val myCharacteristic = getCharacteristicFromUUID(desc.characteristic.uuid)
                    val myDescriptor = getDescriptorFromUUID(desc.uuid)
                    Intent().apply {
                        action = ACTION_WRITE_DESCRIPTOR
                        putExtra(EXTRA_STATUS, status)
                        putExtra(EXTRA_OPERATION, ReturnOperation(btDevice.address, myCharacteristic, myDescriptor, desc.value))
                    }.also(mContext::sendBroadcast)

                    shouldContinue(btDevice.address)
                }
            }
        }
    }
//endregion

    //region BINDING
    private val mBinder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? {
        return mBinder
    }

    inner class LocalBinder : Binder() {
        fun getService(): BlueBeardService = this@BlueBeardService
    }

    //endregion

    fun getCharacteristicFromUUID(uuid: UUID): Characteristic{
        mDeviceTemplates.forEach { temp ->
            temp.services.forEach{ svc ->
                return svc.characteristics.find{ it.uuid == uuid } ?: Characteristic(uuid)
            }
        }
        return Characteristic(uuid)
    }

    fun getDescriptorFromUUID(uuid: UUID): Descriptor{
        mDeviceTemplates.forEach { temp ->
            temp.services.forEach { svc ->
                svc.characteristics.forEach { char ->
                    return char.descriptors.find { it.uuid == uuid } ?: Descriptor(uuid)
                }
            }
        }
        return Descriptor(uuid)
    }

    fun setAttemptLimit(limit: Int) {
        if (limit >= 0)
            mAttemptLimit = limit
    }

    fun enableAutoReconnect(enable: Boolean) {
        mAutoReconnect = enable
    }

    fun send(operation: Operation): Boolean {
        return mOperations.add(operation)
    }

    fun isDeviceConnected(address: String): Boolean {
        return getBleDeviceFromAddress(address)?.connected ?: false
    }

    fun isDeviceDiscovered(address: String): Boolean {
        return getBleDeviceFromAddress(address)?.discovered ?: false
    }

    fun addDevice(address: String): Boolean {
        getDeviceFromAddress(address)?.let { device ->
            return mBleDevices.add(BleDevice(device))
        }
        return false
    }

    fun addDeviceTemplate(template: DeviceTemplate): Boolean {
        return mDeviceTemplates.add(template)
    }

    private fun getDeviceFromAddress(address: String): BluetoothDevice? {
        return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
    }
}