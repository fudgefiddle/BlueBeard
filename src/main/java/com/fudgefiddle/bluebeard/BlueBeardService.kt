package com.fudgefiddle.bluebeard

import android.app.Service
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.fudgefiddle.bluebeard.device.BleDevice
import com.fudgefiddle.bluebeard.device_template.BleProperties.DeviceTemplate
import com.fudgefiddle.bluebeard.operation.Operation
import java.util.*


class BlueBeardService : Service() {

    //region PROPERTIES
    /** BlueBeardService Context */
    private val mContext: Context = this

    private var mAutoReconnect: Boolean = false

    private val onReadSuccess: MutableLiveData<Operation.Write> = MutableLiveData()
    private val onReadFailure: MutableLiveData<Operation.Write> = MutableLiveData()

    private val onWriteSuccess: MutableLiveData<Operation.Write> = MutableLiveData()
    private val onWriteFailure: MutableLiveData<Operation.Write> = MutableLiveData()

    private val onConnectSuccess: MutableLiveData<Operation.Connect> = MutableLiveData()
    private val onConnectFailure: MutableLiveData<Operation.Connect> = MutableLiveData()

    private val onDiscoverSuccess: MutableLiveData<Operation.Discover> = MutableLiveData()
    private val onDiscoverFailure: MutableLiveData<Operation.Discover> = MutableLiveData()

    private val onDisconnectSuccess: MutableLiveData<Operation.Disconnect> = MutableLiveData()
    private val onDisconnectFailure: MutableLiveData<Operation.Disconnect> = MutableLiveData()

    private val onNotification: MutableLiveData<Operation.Write> = MutableLiveData()

    private val onDescriptorWriteSuccess: MutableLiveData<Operation.WriteDescriptor> = MutableLiveData()
    private val onDescriptorWriteFailure: MutableLiveData<Operation.WriteDescriptor> = MutableLiveData()

    private val mDeviceTemplateList = object : ArrayList<DeviceTemplate>() {
        override fun add(element: DeviceTemplate): Boolean = if(!contains(element)) super.add(element) else false
        override fun contains(element: DeviceTemplate): Boolean  = get(element) != null
        private fun get(element: DeviceTemplate): DeviceTemplate? = find { dT -> dT.name == element.name }

        fun get(name: String): DeviceTemplate? = find { dT -> dT.name == name }
        fun get(device: BluetoothDevice): DeviceTemplate? = find{ dT -> dT.name == device.name }
        fun get(device: BleDevice): DeviceTemplate? = find{ dT -> dT.name == device.name }
    }
    private val mBleDeviceList = object : ArrayList<BleDevice>() {

        fun add(address: String): Boolean{
            getDeviceFromAddress(address)?.let{ device ->
                return add(BleDevice(device))
            }
            return false
        }

        override fun add(element: BleDevice): Boolean = if(!contains(element)) super.add(element) else false

        override fun contains(element: BleDevice): Boolean = get(element) != null

        fun get(address: String): BleDevice? = find{ device -> device.address == address }

        fun get(element: BleDevice?): BleDevice? = find { device -> device.address == element?.address }

        fun get(element: BluetoothDevice?): BleDevice? = find { device -> device.address == element?.address }

        fun get(gatt: BluetoothGatt?): BleDevice? = get(gatt?.device)
    }
    private val mOperationDeque = object : ArrayDeque<Operation>() {

        private lateinit var mLastOperation: Operation
        private var mCurrentlyOperating = false

        private val mTimeoutHandler: Handler = Handler(Looper.getMainLooper())
        private val mTimeoutRunnable: Runnable = Runnable { executeNextOperation() }

        var attemptLimit = 0 // If zero then will not be used
        var faultCorrection = false

        override fun add(element: Operation): Boolean {
            var result = false
            when(element){
                !is Operation.Connect,
                !is Operation.Discover,
                !is Operation.Disconnect -> {
                    result = super.add(element)
                    onStart()
                }
            }
            return result
        }

        fun onOperationCompleteSuccess() {
            getLastOperationDevice()?.attempts = 0
            executeNextOperation()
        }

        fun addLastOperation() {
            add(mLastOperation)
        }

        private fun onStart() {
            if (!mCurrentlyOperating) {
                mCurrentlyOperating = true
                executeNextOperation()
            }
        }

        fun executeNextOperation() {
            try {
                mLastOperation = remove()
                if(attemptLimit != 0)
                    getLastOperationDevice()?.attempts?.plus(1)
                handleOperation()
            } catch (e: NoSuchElementException) {
                mCurrentlyOperating = false
            }
        }

        private fun removeAllWhere(func: (Operation) -> Boolean) {
            forEach { operation ->
                if (func(operation))
                    remove(operation)
            }
        }

        private fun delay(delay: Long, func: () -> Boolean) {

            Handler(Looper.getMainLooper()).postDelayed({
                if(!func())
                    executeNextOperation()
            }, delay)
        }

        private fun startTimeout(time: Long) {
            mTimeoutHandler.postDelayed(mTimeoutRunnable, time)
        }

        fun removeTimeout(){
            mTimeoutHandler.removeCallbacks(mTimeoutRunnable)
        }

        fun resetLastDeviceAttempts(){
            getLastOperationDevice()?.attempts = 0
        }

        fun getLastOperationDevice(): BleDevice? {
            return mBleDeviceList.get(mLastOperation.address)
        }

        private fun handleOperation() {
            // Get operation values
            val operation = mLastOperation

            val delay = operation.delay
            val timeout = operation.timeout

            getLastOperationDevice()?.apply {
                when {
                    // Exceeded attempt limit
                    (attemptLimit != 0 && attempts >= attemptLimit) -> {
                        // Reset attempts else infinite loop
                        attempts = 0
                        // Remove all pending operations for this address
                        removeAllWhere { operation -> operation.address == address }
                        // Disconnect from the address
                        if (connected)
                            add(Operation.Disconnect(address))
                        // Move on
                        executeNextOperation()
                    }
                    // NOT exceeded attempt limit; NOT connected
                    !connected -> {
                        when (operation) {
                            is Operation.Connect -> delay(delay){ connect(mContext, mGattCallback) }

                            is Operation.Disconnect -> onOperationCompleteSuccess()

                            else -> {
                                if(faultCorrection) {
                                    add(Operation.Connect(address))
                                    addLastOperation()
                                }
                                onOperationCompleteSuccess()
                            }
                        }
                    }
                    // NOT exceeded attempt limit; IS connected; NOT discovered
                    !discovered -> {
                        when (operation) {
                            is Operation.Connect -> onOperationCompleteSuccess()

                            is Operation.Disconnect -> delay(delay) { disconnect() }

                            is Operation.Discover -> delay(delay) { discoverServices() }

                            else -> {
                                if(faultCorrection) {
                                    add(Operation.Discover(address))
                                    addLastOperation()
                                }
                                onOperationCompleteSuccess()
                            }
                        }
                    }
                    // NOT exceeded attempt limit; IS connected; IS discovered
                    else -> {
                        when (operation) {
                            is Operation.Read -> delay(delay) { read(operation.uuid) }

                            is Operation.Write -> delay(delay) { write(operation.uuid, operation.value) }

                            is Operation.WriteDescriptor -> delay(delay) { writeDescriptor(operation.characteristicUuid, operation.descriptorUuid, operation.value) }

                            is Operation.Notification -> delay(delay) { notify(operation.uuid, operation.enable) }

                            is Operation.Disconnect -> delay(delay) { disconnect() }

                            else -> onOperationCompleteSuccess()
                        }
                    }
                }

                if(timeout != 0L)
                    startTimeout(timeout)
            }
        }
    }

    /** Gatt Callbacks */
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            mBleDeviceList.get(gatt)?.let { device ->
                CustomGattCallback(status).apply {
                    when (newState) {
                        STATE_CONNECTED -> {
                            onSuccess { 
                                device.connected = true
                                onConnectSuccess.value = Operation.Connect(device.address)
                            }
                            onFailure { 
                                device.connected = false
                                onConnectFailure.value = Operation.Connect(device.address)
                            }

                            shouldComplete(isSameDeviceAsLastOperation(device))
                        }
                        STATE_DISCONNECTED -> {
                            device.apply{
                                connected = false
                                discovered = false
                                closeGatt()
                            }
                            onSuccess { onDisconnectSuccess.value = Operation.Disconnect(device.address) }

                            onFailure {
                                if(mAutoReconnect){
                                    mOperationDeque.add(Operation.Connect(device.address))
                                }
                                onDisconnectFailure.value = Operation.Disconnect(device.address)
                            }

                            shouldComplete(isSameDeviceAsLastOperation(device))
                        }
                    }
                }.execute()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            mBleDeviceList.get(gatt)?.let { device ->
                CustomGattCallback(status).apply {
                    onSuccess {
                        device.discovered = true
                        onDiscoverSuccess.value = Operation.Discover(device.address)
                    }
                    onFailure { 
                        device.discovered = false
                        onDiscoverFailure.value = Operation.Discover(device.address)
                    }

                    shouldComplete(isSameDeviceAsLastOperation(device))
                }.execute()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?,
                                          characteristic: BluetoothGattCharacteristic?,
                                          status: Int) {
            mBleDeviceList.get(gatt)?.let { device ->
                characteristic?.let { char ->

                    CustomGattCallback(status).apply {
                        onSuccess { onReadSuccess.value = Operation.Write(device.address, char.uuid.toString(), char.value) }

                        onFailure { onReadFailure.value = Operation.Write(device.address, char.uuid.toString(), char.value) }

                        shouldComplete(isSameDeviceAsLastOperation(device))

                    }.execute()
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?,
                                           characteristic: BluetoothGattCharacteristic?,
                                           status: Int) {
            mBleDeviceList.get(gatt)?.let { device ->
                characteristic?.let { char ->
                    CustomGattCallback(status).apply {
                        onSuccess { onWriteSuccess.value = Operation.Write(device.address, char.uuid.toString(), char.value) }
                        onFailure { onWriteFailure.value = Operation.Write(device.address, char.uuid.toString(), char.value) }
                        shouldComplete(isSameDeviceAsLastOperation(device))
                    }.execute()
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            mBleDeviceList.get(gatt)?.let { device ->
                characteristic?.let { char ->
                    CustomGattCallback(GATT_SUCCESS).apply {
                        onSuccess {
                            onNotification.value = Operation.Write(device.address, char.uuid.toString(), char.value)
                        }
                        shouldComplete(false)
                    }.execute()
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            mBleDeviceList.get(gatt)?.let{ device ->
                descriptor?.let { desc ->
                    CustomGattCallback(status).apply {
                        onSuccess {
                            onDescriptorWriteSuccess.value = Operation.WriteDescriptor(device.address, desc.characteristic.uuid.toString(), desc.uuid.toString(), desc.value)
                        }
                        onFailure {
                            onDescriptorWriteFailure.value = Operation.WriteDescriptor(device.address, desc.characteristic.uuid.toString(), desc.uuid.toString(), desc.value)
                        }

                        shouldComplete(isSameDeviceAsLastOperation(device))
                    }.execute()
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

    //region LIFECYCLE EVENTS
    override fun onDestroy() {
        super.onDestroy()
        mOperationDeque.clear()
        mBleDeviceList.clear()
    }
    //endregion

    //region PUBLIC METHODS
    //region Service Settings Setters
    fun setAttemptLimit(limit: Int){
        if(limit >= 0) 
            mOperationDeque.attemptLimit = limit
    }
    fun enableFaultCorrection(enable: Boolean){
        mOperationDeque.faultCorrection = enable
    }
    fun enableAutoReconnect(enable: Boolean){
        mAutoReconnect = enable
    }

    fun getReadSuccessLiveData(): LiveData<Operation.Write> = onReadSuccess
    fun getReadFailureLiveData(): LiveData<Operation.Write> = onReadFailure

    fun getWriteSuccessLiveData(): LiveData<Operation.Write> = onWriteSuccess
    fun getWriteFailureLiveData(): LiveData<Operation.Write> = onWriteFailure

    fun getConnectSuccessLiveData(): LiveData<Operation.Connect> = onConnectSuccess
    fun getConnectFailureLiveData(): LiveData<Operation.Connect> = onConnectFailure

    fun getDisconnectSuccessLiveData(): LiveData<Operation.Disconnect> = onDisconnectSuccess
    fun getDisconnectFailureLiveData(): LiveData<Operation.Disconnect> = onDisconnectFailure

    fun getDiscoverSuccessLiveData(): LiveData<Operation.Discover> = onDiscoverSuccess
    fun getDiscoverFailureLiveData(): LiveData<Operation.Discover> = onDiscoverFailure

    fun getDescriptorWriteSuccessLiveData(): LiveData<Operation.WriteDescriptor> = onDescriptorWriteSuccess
    fun getDescriptorWriteFailureLiveData(): LiveData<Operation.WriteDescriptor> = onDescriptorWriteFailure

    fun getNotificationLiveData(): LiveData<Operation.Write> = onNotification

    fun send(operation: Operation): Boolean{
        return mOperationDeque.add(operation)
    }

    fun addDeviceTemplate(template: DeviceTemplate): Boolean{
        return mDeviceTemplateList.add(template)
    }
    //endregion

    //region Add Device Methods
    fun addDevice(address: String): Boolean{
        return mBleDeviceList.add(address)
    }
    fun addDevice(device: BluetoothDevice): Boolean{
        return addDevice(device.address)
    }
    //endregion

    //region PRIVATE METHODS
    private fun getDeviceFromAddress(address: String): BluetoothDevice?{
        return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
    }

    private fun isSameDeviceAsLastOperation(device: BleDevice): Boolean{
        return mOperationDeque.getLastOperationDevice()?.address == device.address
    }
    //endregion

    //region PRIVATE INNER CLASS / COMPANION
    private inner class CustomGattCallback(private val status: Int) {
        // Gatt Success Function
        private var gattSuccess: () -> Unit = { }
        // Gatt Failure Function
        private var gattFailure: () -> Unit = { mOperationDeque.addLastOperation() }
        // Complete flag
        private var complete: Boolean = true

        fun onSuccess(doThis: () -> Unit): CustomGattCallback {
            gattSuccess = doThis
            return this
        }

        fun onFailure(doThis: () -> Unit): CustomGattCallback {
            gattFailure = doThis
            return this
        }

        fun shouldComplete(enable: Boolean): CustomGattCallback {
            complete = enable
            return this
        }

        fun execute() {
            if (status == GATT_SUCCESS) {
                mOperationDeque.removeTimeout()
                mOperationDeque.resetLastDeviceAttempts()
                gattSuccess.invoke()
            } else {
                gattFailure.invoke()
            }
            if (complete)
                mOperationDeque.executeNextOperation()
        }
    }

    private companion object{
        private const val NOTIFICATION_DESCRIPTOR_UUID: String = "00002902-0000-1000-8000-00805f9b34fb"
    }
    //endregion
}