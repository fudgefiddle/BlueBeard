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
import com.fudgefiddle.bluebeard.device.BleDeviceSet
import com.fudgefiddle.bluebeard.device_template.BleProperty.Characteristic
import com.fudgefiddle.bluebeard.device_template.BleProperty.Descriptor
import com.fudgefiddle.bluebeard.device_template.DeviceTemplateSet
import com.fudgefiddle.bluebeard.operation.Operation
import com.fudgefiddle.bluebeard.operation.OperationQueue
import com.fudgefiddle.bluebeard.operation.ReturnOperation
import timber.log.Timber
import java.util.*


class BlueBeardService : Service() {

    /** BlueBeardService Context */
    private val mContext: Context = this

    val deviceTemplates = DeviceTemplateSet()
    val bleDevices = BleDeviceSet()

    private val mOperationQueue = OperationQueue()
    private var mIsOperating = false
    private val mOperationTimeoutHandler = Handler()
    private val mOperationTimeout = Runnable {
        executeNextOperation()
    }
    private lateinit var mLastOperation: Operation

    var attemptLimit = 0
    var autoReconnect: Boolean = false

    /** Gatt Callbacks */
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            gatt?.device?.let { btDevice ->
                val device = bleDevices.get(btDevice.address)
                val intent = Intent().also {
                    it.putExtra(EXTRA_STATUS, status)
                    it.putExtra(EXTRA_OPERATION, ReturnOperation(btDevice))
                }
                when (newState) {
                    STATE_CONNECTED -> {
                        device.connected = status == GATT_SUCCESS
                        intent.action = ACTION_CONNECT
                    }
                    STATE_DISCONNECTED -> {
                        device.connected = false
                        device.discovered = false
                        if (status != GATT_SUCCESS && autoReconnect) {
                            mOperationQueue.add(Operation.Connect(btDevice))
                        } else {
                            device.closeGatt()
                        }
                        intent.action = ACTION_DISCONNECT
                    }
                }

                mContext.sendBroadcast(intent)

                shouldContinue(btDevice.address, status)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            gatt?.device?.let { btDevice ->
                bleDevices.get(btDevice).discovered = status == GATT_SUCCESS

                Intent().apply {
                    action = ACTION_DISCOVER
                    putExtra(EXTRA_STATUS, status)
                    putExtra(EXTRA_OPERATION, ReturnOperation(btDevice))
                }.also(mContext::sendBroadcast)

                shouldContinue(btDevice.address, status)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?,
                                          characteristic: BluetoothGattCharacteristic?,
                                          status: Int) {
            gatt?.device?.let { btDevice ->
                characteristic?.let { char ->
                    val myCharacteristic = getCharacteristicFromUUID(btDevice.name, char.uuid)

                    Intent().apply {
                        action = ACTION_READ
                        putExtra(EXTRA_STATUS, status)
                        putExtra(EXTRA_OPERATION, ReturnOperation(btDevice, myCharacteristic, value = char.value))
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
                    val myCharacteristic = getCharacteristicFromUUID(btDevice.name, char.uuid)

                    Intent().apply {
                        action = ACTION_READ
                        putExtra(EXTRA_STATUS, status)
                        putExtra(EXTRA_OPERATION, ReturnOperation(btDevice, myCharacteristic, value = char.value))
                    }.also(mContext::sendBroadcast)

                    shouldContinue(btDevice.address, status)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            gatt?.device?.let { btDevice ->
                characteristic?.let { char ->
                    val myCharacteristic = getCharacteristicFromUUID(btDevice.name, char.uuid)

                    Intent().apply {
                        action = ACTION_NOTIFICATION
                        putExtra(EXTRA_STATUS, -1)
                        putExtra(EXTRA_OPERATION, ReturnOperation(btDevice, myCharacteristic, value = char.value))
                    }.also(mContext::sendBroadcast)

                    shouldContinue(btDevice.address, GATT_SUCCESS)
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            gatt?.device?.let { btDevice ->
                descriptor?.let { desc ->
                    val myCharacteristic = getCharacteristicFromUUID(btDevice.name, desc.characteristic.uuid)
                    val myDescriptor = getDescriptorFromUUID(btDevice.name, desc.uuid)
                    Intent().apply {
                        action = ACTION_WRITE_DESCRIPTOR
                        putExtra(EXTRA_STATUS, status)
                        putExtra(EXTRA_OPERATION, ReturnOperation(btDevice, myCharacteristic, myDescriptor, desc.value))
                    }.also(mContext::sendBroadcast)

                    shouldContinue(btDevice.address, status)
                }
            }
        }
    }

    private fun startOperationTimeout(timeout: Long) {
        if (timeout != 0L)
            mOperationTimeoutHandler.postDelayed(mOperationTimeout, timeout)
    }

    private fun stopOperationTimeout() {
        mOperationTimeoutHandler.removeCallbacks(mOperationTimeout)
    }

    private fun executeNextOperation() {
        Timber.d("Checking for next operation in queue...")
        if (mOperationQueue.isNotEmpty()) {
            setIsOperating(true)
            mLastOperation = mOperationQueue.pop().also { operation ->
                Timber.d("Found operation: ${operation::class.simpleName}")
                val delay = operation.delay
                val timeout = operation.timeout
                val bleDevice = bleDevices.get(operation.device)


                // Increase devices attempts
                bleDevice.attempts++

                when {
                    // Attempt limit is being used && Exceeded attempt limit
                    (attemptLimit != 0 && bleDevice.attempts >= attemptLimit) -> {
                        Timber.d("Device has exceeded attempt limit")
                        // Reset attempts else infinite loop
                        bleDevice.attempts = 0
                        // Remove all pending operations for this address
                        Timber.d("Removing pending operations for device")
                        mOperationQueue.removeAllForDevice(bleDevice.address)
                        // Disconnect from the address
                        Timber.d("Adding disconnect operation for device")
                        mOperationQueue.add(Operation.Disconnect(bleDevice.address))
                        // Move on
                        executeNextOperation()
                    }
                    // NOT exceeded attempt limit; NOT connected
                    !bleDevice.connected -> {
                        Timber.d("Device is not connected")
                        when (operation) {
                            is Operation.Connect -> {
                                Timber.d("Attempting connection in ${operation.delay}ms")
                                startOperationTimeout(timeout)
                                delay(delay) {
                                    bleDevice.connect(mContext, mGattCallback)
                                }
                            }

                            is Operation.Disconnect -> {
                                Timber.d("Attempted to disconnect but device already disconnected")
                                onOperationCompleteSuccess(bleDevice.address)
                            }

                            else -> {
                                Timber.d("Invalid operation while device is not connected")
                                // Make sure we have no other connect operations for this to work properly
                                mOperationQueue.removeAllWhere { op -> op.device.address == bleDevice.address && op is Operation.Connect }

                                Timber.d("Placing any pending operations for device behind new connect operation...")
                                // Get any other future operations, remove them, put a connection at the front, put the rest behind
                                mOperationQueue.filter { op -> op.device.address == bleDevice.address && op !is Operation.Connect }.also { list ->
                                    mOperationQueue.removeAll(list)
                                    mOperationQueue.add(Operation.Connect(bleDevice.address))
                                    mOperationQueue.addAll(list)
                                }

                                onOperationCompleteSuccess(bleDevice.address)
                            }
                        }
                    }
                    // NOT exceeded attempt limit; IS connected; NOT discovered
                    !bleDevice.discovered -> {
                        Timber.d("Device is connected but not discovered")
                        when (operation) {
                            is Operation.Connect -> {
                                Timber.d("Attempted to connect but device already connected")
                                onOperationCompleteSuccess(bleDevice.address)
                            }

                            is Operation.Disconnect -> {
                                Timber.d("Attempting to disconnect from device in ${operation.delay}ms")
                                delay(delay) { startOperationTimeout(timeout); bleDevice.disconnect() }
                            }

                            is Operation.Discover -> {
                                Timber.d("Attempting to discover device in ${operation.delay}ms")
                                delay(delay) { startOperationTimeout(timeout); bleDevice.discoverServices() }
                            }

                            else -> {
                                Timber.d("Invalid operation while device is not discovered")
                                // Make sure we have no other discover operations for this to work properly
                                mOperationQueue.removeAllWhere { op -> op.device.address == bleDevice.address && op is Operation.Discover }

                                Timber.d("Placing any pending operations for device behind new discover operation...")
                                // Get any other future operations, remove them, put a discover at the front, put the rest behind
                                mOperationQueue.filter { op -> op.device.address == bleDevice.address && op !is Operation.Discover }.also { list ->
                                    mOperationQueue.removeAll(list)
                                    mOperationQueue.add(Operation.Discover(bleDevice.address))
                                    mOperationQueue.addAll(list)
                                }

                                onOperationCompleteSuccess(bleDevice.address)
                            }
                        }
                    }
                    // NOT exceeded attempt limit; IS connected; IS discovered
                    else -> {
                        Timber.d("Device is connected and discovered")
                        when (operation) {
                            is Operation.Read -> {
                                Timber.d("Attempting to read device in ${operation.delay}ms")
                                delay(delay) { startOperationTimeout(timeout); bleDevice.read(operation.uuid) }
                            }

                            is Operation.Write -> {
                                Timber.d("Attempting to write device characteristic ${getCharacteristicFromUUID(bleDevice.name, operation.uuid).name} in ${operation.delay}ms")
                                delay(delay) { startOperationTimeout(timeout); bleDevice.write(operation.uuid, operation.value) }
                            }

                            is Operation.WriteDescriptor -> {
                                Timber.d("Attempting to write device descriptor ${getDescriptorFromUUID(bleDevice.name, operation.descriptorUuid).name} in ${operation.delay}ms")
                                delay(delay) { startOperationTimeout(timeout); bleDevice.writeDescriptor(operation.characteristicUuid, operation.descriptorUuid, operation.value) }
                            }

                            is Operation.Notification -> {
                                Timber.d("Attempting to enable notifications for characteristic ${getCharacteristicFromUUID(bleDevice.name, operation.uuid).name} in ${operation.delay}ms")
                                delay(delay) { startOperationTimeout(timeout); bleDevice.enableNotifications(operation.uuid, operation.enable) }
                            }

                            is Operation.Disconnect -> {
                                Timber.d("Attempting to disconnect from device in ${operation.delay}ms")
                                delay(delay) { startOperationTimeout(timeout); bleDevice.disconnect() }
                            }

                            else -> onOperationCompleteSuccess(bleDevice.address)
                        }
                    }
                }
            }
        } else {
            Timber.d("Operation queue empty")
            setIsOperating(false)
        }
    }

    private fun shouldContinue(address: String, status: Int) {
        // If the callback is of the same address of the last operation we can generally assume
        // it was the last operation, therefore we should be safe to start the next one.
        // Probably need a better way to check this though.
        if (mLastOperation.device.address == address) {
            if (status == GATT_SUCCESS)
                onOperationCompleteSuccess(address)
            else{
                Timber.d("Operation FAILURE")
                executeNextOperation()
            }
        }
    }

    private fun onOperationCompleteSuccess(address: String) {
        Timber.d("Operation SUCCESS")
        stopOperationTimeout()
        bleDevices.get(address).attempts = 0
        executeNextOperation()
    }

    private fun setIsOperating(enable: Boolean) {
        if(mIsOperating != enable){
            mIsOperating = enable
            val intentAction = if (enable) ACTION_OPERATION_QUEUE_START else ACTION_OPERATION_QUEUE_END
            mContext.sendBroadcast(Intent().apply { action = intentAction })
        }
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

    private fun getCharacteristicFromUUID(name: String, uuid: UUID): Characteristic {
        deviceTemplates.getTemplateFromName(name)?.getCharacteristicFromUUID(uuid)?.let {
            return it
        }
        return Characteristic(uuid)
    }

    private fun getDescriptorFromUUID(name: String, uuid: UUID): Descriptor {
        deviceTemplates.getTemplateFromName(name)?.getDescriptorFromUUID(uuid)?.let {
            return it
        }
        return Descriptor(uuid)
    }

    fun isOperating(): Boolean = mIsOperating

    fun queueOperation(operation: Operation) {
        mOperationQueue.add(operation)
        startOperating()
    }

    fun queueOperationFirst(operation: Operation) {
        mOperationQueue.addFirst(operation)
        startOperating()
    }

    fun startOperating(){
        if(!mIsOperating)
            executeNextOperation()
    }

    fun isDeviceConnected(address: String): Boolean {
        return bleDevices.get(address).connected
    }

    fun isDeviceDiscovered(address: String): Boolean {
        return bleDevices.get(address).discovered
    }

}