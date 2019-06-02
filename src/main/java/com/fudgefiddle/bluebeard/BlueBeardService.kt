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
import android.util.Log
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
    private val mOperationTimeoutHandler = Handler()
    private val mOperationTimeout = Runnable {
        executeNextOperation()
    }
    private lateinit var mLastOperation: Operation
    private var mAttemptLimit = 0
    private var mAutoReconnect: Boolean = false

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

                shouldContinue(btDevice.address, status)
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

                shouldContinue(btDevice.address, status)
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

                    shouldContinue(btDevice.address, status)
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

                    shouldContinue(btDevice.address, status)
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

                    shouldContinue(btDevice.address, -1)
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

                    shouldContinue(btDevice.address, status)
                }
            }
        }
    }

    private fun startOperationTimeout(timeout: Long){
        if(timeout != 0L)
            mOperationTimeoutHandler.postDelayed(mOperationTimeout, timeout)
    }

    private fun stopOperationTimeout(){
        mOperationTimeoutHandler.removeCallbacks(mOperationTimeout)
    }

    private fun executeNextOperation() {
        Log.d(TAG, "Checking for next operation in queue...")
        if (mOperations.isNotEmpty()) {
            mLastOperation = mOperations.pop().also { operation ->
                Log.d(TAG, "Found operation: ${operation::class.simpleName}")
                val delay = operation.delay
                val timeout = operation.timeout

                Log.d(TAG, "Searching for device: ${operation.address}")
                // Look for the device in our set
                getBleDeviceFromAddress(operation.address)?.apply {
                    Log.d(TAG, "Found device")
                    // Increase devices attempts
                    attempts++

                    when {
                        // Attempt limit is being used && Exceeded attempt limit
                        (mAttemptLimit != 0 && attempts >= mAttemptLimit) -> {
                            Log.d(TAG, "Device has exceeded attempt limit")
                            // Reset attempts else infinite loop
                            attempts = 0
                            Log.d(TAG, "Removing pending operations for device")
                            // Remove all pending operations for this address
                            mOperations.removeAllWhere { op -> op.address == address }
                            Log.d(TAG, "Adding disconnect operation for device")
                            // Disconnect from the address
                            mOperations.add(Operation.Disconnect(address))
                            // Move on
                            executeNextOperation()
                        }
                        // NOT exceeded attempt limit; NOT connected
                        !connected -> {
                            Log.d(TAG, "Device is not connected")
                            when (operation) {
                                is Operation.Connect -> {
                                    Log.d(TAG, "Attempting connection in ${operation.delay}ms")
                                    delay(delay) {
                                        startOperationTimeout(timeout)
                                        connect(mContext, mGattCallback)
                                    }
                                }

                                is Operation.Disconnect -> {
                                    Log.d(TAG, "Attempted to disconnect but device already disconnected")
                                    onOperationCompleteSuccess(address)
                                }

                                else -> {
                                    Log.d(TAG, "Invalid operation while device is not connected")
                                    // Make sure we have no other connect operations for this to work properly
                                    mOperations.removeAllWhere { op -> op.address == address && op is Operation.Connect }

                                    Log.d(TAG, "Placing any pending operations for device behind new connect operation...")
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
                            Log.d(TAG, "Device is connected but not discovered")
                            when (operation) {
                                is Operation.Connect -> {
                                    Log.d(TAG, "Attempted to connect but device already connected")
                                    onOperationCompleteSuccess(address)
                                }

                                is Operation.Disconnect -> {
                                    Log.d(TAG, "Attempting to disconnect from device in ${operation.delay}ms")
                                    delay(delay) { startOperationTimeout(timeout); disconnect() }
                                }

                                is Operation.Discover -> {
                                    Log.d(TAG, "Attempting to discover device in ${operation.delay}ms")
                                    delay(delay) { startOperationTimeout(timeout); discoverServices() }
                                }

                                else -> {
                                    Log.d(TAG, "Invalid operation while device is not discovered")
                                    // Make sure we have no other discover operations for this to work properly
                                    mOperations.removeAllWhere { op -> op.address == address && op is Operation.Discover }

                                    Log.d(TAG, "Placing any pending operations for device behind new discover operation...")
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
                            Log.d(TAG, "Device is connected and discovered")
                            when (operation) {
                                is Operation.Read -> {
                                    Log.d(TAG, "Attempting to read device in ${operation.delay}ms")
                                    delay(delay) { startOperationTimeout(timeout); read(operation.uuid) }
                                }

                                is Operation.Write -> {
                                    Log.d(TAG, "Attempting to write device characteristic ${getCharacteristicFromUUID(UUID.fromString(operation.uuid)).name} in ${operation.delay}ms")
                                    delay(delay) { startOperationTimeout(timeout); write(operation.uuid, operation.value) }
                                }

                                is Operation.WriteDescriptor -> {
                                    Log.d(TAG, "Attempting to write device descriptor ${getDescriptorFromUUID(UUID.fromString(operation.descriptorUuid)).name} in ${operation.delay}ms")
                                    delay(delay) { startOperationTimeout(timeout); writeDescriptor(operation.characteristicUuid, operation.descriptorUuid, operation.value) }
                                }

                                is Operation.Notification -> {
                                    Log.d(TAG, "Attempting to enable notifications for characteristic ${getCharacteristicFromUUID(UUID.fromString(operation.uuid)).name} in ${operation.delay}ms")
                                    delay(delay) { startOperationTimeout(timeout); enableNotifications(operation.uuid, operation.enable) }
                                }

                                is Operation.Disconnect -> {
                                    Log.d(TAG, "Attempting to disconnect from device in ${operation.delay}ms")
                                    delay(delay) { startOperationTimeout(timeout); disconnect() }
                                }

                                else -> onOperationCompleteSuccess(address)
                            }
                        }
                    }
                    // BleDevice was null / couldn't find device
                } ?: run {
                    Log.w(TAG, "Device could not be find. Removing all pending operations for device")
                    // Get rid of any operations with this device
                    mOperations.removeAllWhere { op -> op.address == operation.address }
                    executeNextOperation()
                }
            }
        }
        else {
            Log.d(TAG, "Operation queue empty")
        }
    }

    private fun shouldContinue(address: String, status: Int) {
        // If the callback is of the same address of the last operation we can generally assume
        // it was the last operation, therefore we should be safe to start the next one.
        // Probably need a better way to check this though.
        if (mLastOperation.address == address){
            if(status == GATT_SUCCESS)
                onOperationCompleteSuccess(address)
            else
                executeNextOperation()
        }
    }

    private fun onOperationCompleteSuccess(address: String) {
        Log.d(TAG, "Operation SUCCESS")
        stopOperationTimeout()
        getBleDeviceFromAddress(address)?.attempts = 0
        executeNextOperation()
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

    //region BINDING
    private val mBinder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? {
        return mBinder
    }

    inner class LocalBinder : Binder() {
        fun getService(): BlueBeardService = this@BlueBeardService
    }
    //endregion

    private fun getBluetoothDeviceFromAddress(address: String): BluetoothDevice? {
        return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
    }

    private fun getBleDeviceFromAddress(address: String?): BleDevice? {
        return mBleDevices.find { it.address == address }
    }

    private fun getCharacteristicFromUUID(uuid: UUID): Characteristic{
        mDeviceTemplates.forEach { temp ->
            temp.services.forEach{ svc ->
                return svc.characteristics.find{ it.uuid == uuid } ?: Characteristic(uuid)
            }
        }
        return Characteristic(uuid)
    }

    private fun getDescriptorFromUUID(uuid: UUID): Descriptor{
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
        getBluetoothDeviceFromAddress(address)?.let { device ->
            return mBleDevices.add(BleDevice(device))
        }
        return false
    }

    fun addDeviceTemplate(template: DeviceTemplate): Boolean {
        return mDeviceTemplates.add(template)
    }


    private companion object {
        const val TAG: String = "BlueBeardService"
    }
}