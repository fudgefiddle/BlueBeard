package com.fudgefiddle.bluebeard

import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import com.fudgefiddle.bluebeard.device.BleDevice
import com.fudgefiddle.bluebeard.device_template.BleProperty
import com.fudgefiddle.bluebeard.device_template.DeviceTemplate
import com.fudgefiddle.bluebeard.operation.Operation
import com.fudgefiddle.bluebeard.operation.ReturnOperation
import java.util.*

class BlueBeardService : Service() {
    //region BINDING
    private val mBinder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? {
        return mBinder
    }

    inner class LocalBinder : Binder() {
        fun getService(): BlueBeardService = this@BlueBeardService
    }
    //endregion

    val deviceTemplates = DeviceTemplateSet()
    val bleDevices = BleDeviceList()
    val operationQueue = OperationQueue()

    private val mContext: Context = this
    private val mTimeout = Timeout()
    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            // Update the BleDevice object
            val device = bleDevices.get(gatt.device).also { it.onConnectionStateChange(status, newState) }
            // Build intent
            val intent = buildIntent(ACTION_CONNECTION_STATE_CHANGE, ReturnOperation(gatt.device), status, newState)
            // Send intent
            mContext.sendBroadcast(intent)

            // Check if this callback matches the last operation
            operationQueue.getLastOperation().let {
                if ((it is Operation.Connect || it is Operation.Disconnect) &&
                        (it.device.address == device.address)) {
                    // Operation complete
                    operationQueue.onOperationComplete()
                }
            }

            // If disconnected, close gatt connection
            if (newState == BluetoothProfile.STATE_DISCONNECTED)
                device.closeGatt()

            // If status is a failure, add a redo operation
            if (status != GATT_SUCCESS)
                operationQueue.queueOperation(Operation.Connect(gatt.device))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // Update the BleDevice object
            val device = bleDevices.get(gatt.device).also { it.onServicesDiscovered(status) }
            // Build intent
            val intent = buildIntent(ACTION_DISCOVER, ReturnOperation(gatt.device), status)
            // Send intent
            mContext.sendBroadcast(intent)
            // Check if this callback matches the last operation
            operationQueue.getLastOperation().let {
                if (it is Operation.Discover && it.device.address == device.address) {
                    // Operation complete
                    operationQueue.onOperationComplete()
                }
            }

            // If status is a failure, add a redo operation
            if (status != GATT_SUCCESS)
                operationQueue.queueOperation(Operation.Discover(gatt.device))

        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // Get Characteristic object
            val char = deviceTemplates.getCharacteristic(gatt.device.name, characteristic.uuid)
            // Build intent
            val intent = buildIntent(ACTION_READ, ReturnOperation(gatt.device, char, value = characteristic.value), status)
            // Send intent
            mContext.sendBroadcast(intent)
            // Check if this callback matches the last operation
            operationQueue.getLastOperation().let {
                if (it is Operation.Read &&
                        it.device.address == gatt.device.address &&
                        it.characteristic.uuid == characteristic.uuid) {
                    // Operation complete
                    operationQueue.onOperationComplete()
                }
            }

            // If status is a failure, add a redo operation
            if (status != GATT_SUCCESS)
                operationQueue.queueOperation(Operation.Read(gatt.device, char))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // Get Characteristic object
            val char = deviceTemplates.getCharacteristic(gatt.device.name, characteristic.uuid)
            // Build intent
            val intent = buildIntent(ACTION_WRITE,
                    ReturnOperation(gatt.device, char, value = characteristic.value),
                    status)
            // Send intent
            mContext.sendBroadcast(intent)
            // Check if this callback matches the last operation
            operationQueue.getLastOperation().let {
                if (it is Operation.Write &&
                        it.device.address == gatt.device.address &&
                        it.characteristic.uuid == characteristic.uuid) {
                    // Operation complete
                    operationQueue.onOperationComplete()

                    // If status is a failure, add a redo operation
                    // Last operation must match so we can write the correct value
                    if (status != GATT_SUCCESS)
                        operationQueue.queueOperation(Operation.Write(gatt.device, char, it.value))
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // Get Descriptor object
            val desc = deviceTemplates.getDescriptor(gatt.device.name, descriptor.uuid)
            // Build intent
            val intent = buildIntent(ACTION_WRITE_DESCRIPTOR,
                    ReturnOperation(gatt.device, descriptor = desc, value = descriptor.value),
                    status)
            // Send intent
            mContext.sendBroadcast(intent)
            // Check if this callback matches the last operation
            operationQueue.getLastOperation().let {
                if (it is Operation.WriteDescriptor &&
                        it.device.address == gatt.device.address &&
                        it.descriptor.uuid == descriptor.uuid) {
                    // Operation complete
                    operationQueue.onOperationComplete()

                    // If status is a failure, add a redo operation
                    // Last operation must match so we can write the correct value
                    if (status != GATT_SUCCESS)
                        operationQueue.queueOperation(Operation.WriteDescriptor(gatt.device, desc, it.value))
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // Get Characteristic object
            val char = deviceTemplates.getCharacteristic(gatt.device.name, characteristic.uuid)
            // Build intent
            val intent = buildIntent(ACTION_NOTIFICATION, ReturnOperation(gatt.device, char, value = characteristic.value))
            // Send intent
            mContext.sendBroadcast(intent)
        }

        private fun buildIntent(action: String, operation: ReturnOperation, status: Int = GATT_SUCCESS, state: Int = 0): Intent {
            return Intent().apply {
                this.action = action
                this.putExtra(EXTRA_OPERATION, operation)
                this.putExtra(EXTRA_STATUS, status)
                this.putExtra(EXTRA_STATE, state)
            }
        }
    }

    inner class DeviceTemplateSet {

        private val mSet = mutableSetOf<DeviceTemplate>()

        fun add(template: DeviceTemplate): Boolean {
            return mSet.add(template)
        }

        fun getTemplateFromName(name: String): DeviceTemplate? {
            return mSet.find { temp -> temp.name == name }
        }

        fun getService(deviceName: String, uuid: UUID): BleProperty.Service {
            return getTemplateFromName(deviceName)?.getServiceFromUUID(uuid)
                    ?: BleProperty.Service(uuid)
        }

        fun getCharacteristic(deviceName: String, uuid: UUID): BleProperty.Characteristic {
            return getTemplateFromName(deviceName)?.getCharacteristicFromUUID(uuid)
                    ?: BleProperty.Characteristic(uuid)
        }

        fun getDescriptor(deviceName: String, uuid: UUID): BleProperty.Descriptor {
            return getTemplateFromName(deviceName)?.getDescriptorFromUUID(uuid)
                    ?: BleProperty.Descriptor(uuid)
        }
    }

    inner class BleDeviceList {
        private val mList = mutableListOf<BleDevice>()

        internal fun get(address: String): BleDevice {
            return mList.find { bleDevice -> bleDevice.address == address }
                    ?: throw NoSuchElementException()
        }

        internal fun get(device: BluetoothDevice): BleDevice {
            return get(device.address)
        }

        fun add(device: BluetoothDevice): Boolean {
            if (!contains(device))
                return mList.add(BleDevice(device))

            return false
        }

        fun add(address: String): Boolean {
            return add(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address))
        }

        fun contains(device: BluetoothDevice): Boolean {
            return !mList.none { d -> d.address == device.address }
        }
    }

    inner class OperationQueue {
        private val mQueue = ArrayDeque<Operation>()
        private var mIsCurrentlyOperating = false
        private lateinit var mLastOperation: Operation

        private fun setCurrentlyOperating(enable: Boolean) {
            mIsCurrentlyOperating = enable
            val intent = Intent().apply{
                action = ACTION_OPERATION_QUEUE_STATE_CHANGE
                putExtra(EXTRA_STATE, mIsCurrentlyOperating)
            }
            mContext.sendBroadcast(intent)
        }

        fun queueOperation(operation: Operation) {
            mQueue.add(operation)
            tryStartOperationQueue()
        }

        fun queueOperationFirst(operation: Operation) {
            mQueue.addFirst(operation)
            tryStartOperationQueue()
        }

        fun getLastOperation(): Operation = mLastOperation

        private fun tryStartOperationQueue() {
            if (!mIsCurrentlyOperating)
                onOperationQueueStart()
        }

        internal fun onOperationQueueStart() {
            if (mQueue.isNotEmpty()) {
                if (!mIsCurrentlyOperating) {
                    setCurrentlyOperating(true)
                }
                mLastOperation = mQueue.remove().also { currentOperation ->
                    currentOperation.device.let {
                        if (!bleDevices.contains(it))
                            bleDevices.add(it)
                    }

                    val delay = currentOperation.delay
                    val timeout = currentOperation.timeout

                    bleDevices.get(currentOperation.device).apply {
                        execute(delay, timeout) {
                            when {
                                !isConnected() -> {
                                    when (currentOperation) {
                                        is Operation.Disconnect -> false

                                        is Operation.Connect -> Handler().postDelayed({ connect(mContext, mGattCallback) }, delay)

                                        else -> {
                                            mQueue.add(currentOperation)
                                            mQueue.addFirst(Operation.Connect(address))
                                            false
                                        }
                                    }
                                }

                                !isDiscovered() -> {
                                    when (currentOperation) {
                                        is Operation.Connect -> false

                                        is Operation.Disconnect,
                                        is Operation.Discover -> executeGattOperation(currentOperation)

                                        else -> {
                                            mQueue.add(currentOperation)
                                            mQueue.addFirst(Operation.Discover(address))
                                            false
                                        }
                                    }
                                }

                                else -> {
                                    when (currentOperation) {
                                        is Operation.Connect,
                                        is Operation.Discover -> false

                                        else -> executeGattOperation(currentOperation)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                setCurrentlyOperating(false)
            }
        }

        internal fun onOperationComplete(){
            mTimeout.stop()
            onOperationQueueStart()
        }

        private fun execute(delay: Long, timeout: Long, func: () -> Boolean) {
            Handler().postDelayed({
                if (func()) {
                    mTimeout.start(timeout)
                } else {
                    onOperationQueueStart()
                }
            }, delay)
        }
    }

    private inner class Timeout {
        private val mTimeoutHandler = Handler()
        private val mTimeoutRunnable = Runnable { operationQueue.onOperationQueueStart() }

        fun start(millis: Long) {
            if (millis != 0L)
                mTimeoutHandler.postDelayed(mTimeoutRunnable, millis)
        }

        fun stop() {
            mTimeoutHandler.removeCallbacks(mTimeoutRunnable)
        }
    }
}