package com.fudgefiddle.bluebeard

import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.*
import android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.fudgefiddle.bluebeard.callbacks.SimpleGattCallback
import com.fudgefiddle.bluebeard.device.BleDevice
import com.fudgefiddle.bluebeard.device_template.BleProperties.*
import com.fudgefiddle.bluebeard.operation.Operation
import java.util.*


class BlueBeardService : Service() {

    //region PROPERTIES
    /** BlueBeardService Context */
    private val mContext: Context = this

    private var mAutoReconnect: Boolean = false

    /** Callbacks */
    private var mReadCB: SimpleGattCallback? = null
    private var mWriteCB: SimpleGattCallback? = null
    private var mNotifyCB: SimpleGattCallback? = null
    private var mConnectionCB: SimpleGattCallback? = null
    private var mDiscoveryCB: SimpleGattCallback? = null
    private var mDisconnectionCB: SimpleGattCallback? = null
    private var mWriteDescriptorCB: SimpleGattCallback? = null

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
            mLastOperation.device.attempts = 0
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
                    mLastOperation.device.attempts++
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
            mLastOperation.device.attempts = 0
        }

        fun getLastOperationDevice(): BleDevice{
            return mLastOperation.device
        }

        private fun handleOperation() {
            // Get operation values
            mLastOperation.let{ operation ->

                val delay = operation.delay
                val timeout = operation.timeout

                operation.device.apply {
                    when {
                        // Exceeded attempt limit
                        (attemptLimit != 0 && attempts >= attemptLimit) -> {
                            // Reset attempts else infinite loop
                            mLastOperation.device.attempts = 0
                            // Remove all pending operations for this device
                            removeAllWhere { operation -> operation.device.address == address }
                            // Disconnect from the device
                            if (connected)
                                add(Operation.Disconnect(this))
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
                                        add(Operation.Connect(this))
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
                                        add(Operation.Discover(this))
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

                                is Operation.Notify -> delay(delay) { notify(operation.uuid, operation.enable) }

                                is Operation.Disconnect -> delay(delay) { disconnect() }

                                else -> onOperationCompleteSuccess()
                            }
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
                                mConnectionCB?.onSuccess(device.btDevice)
                            }
                            onFailure { 
                                device.connected = false
                                mConnectionCB?.onFailure(device.btDevice)
                            }

                            shouldComplete(isSameDeviceAsLastOperation(device))
                        }
                        STATE_DISCONNECTED -> {
                            device.apply{
                                connected = false
                                discovered = false
                                closeGatt()
                            }
                            onSuccess { mDisconnectionCB?.onSuccess(device.btDevice) }

                            onFailure {
                                if(mAutoReconnect){
                                    mOperationDeque.add(Operation.Connect(device))
                                }
                                mDisconnectionCB?.onFailure(device.btDevice)
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
                        mDiscoveryCB?.onSuccess(device.btDevice)
                    }
                    onFailure { 
                        device.discovered = false
                        mDiscoveryCB?.onFailure(device.btDevice)
                    }

                    shouldComplete(isSameDeviceAsLastOperation(device))
                }.execute()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?,
                                          characteristic: BluetoothGattCharacteristic?,
                                          status: Int) {
            mBleDeviceList.get(gatt)?.let { device ->
                CustomGattCallback(status).apply {
                    onSuccess {
                        val retVal: Any? = getCharacteristicValue(device, characteristic)
                        mReadCB?.onSuccess(device.btDevice, characteristic?.uuid, retVal)
                    }
                    onFailure { mReadCB?.onFailure(device.btDevice, characteristic?.uuid) }

                    shouldComplete(isSameDeviceAsLastOperation(device))

                }.execute()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?,
                                           characteristic: BluetoothGattCharacteristic?,
                                           status: Int) {
            mBleDeviceList.get(gatt)?.let { device ->
                CustomGattCallback(status).apply {
                    onSuccess { mWriteCB?.onSuccess(device.btDevice, characteristic?.uuid) }
                    onFailure { mWriteCB?.onFailure(device.btDevice, characteristic?.uuid) }
                    shouldComplete(isSameDeviceAsLastOperation(device))
                }.execute()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            mBleDeviceList.get(gatt)?.let { device ->
                CustomGattCallback(GATT_SUCCESS).apply {
                    onSuccess {
                        val retVal: Any? = getCharacteristicValue(device, characteristic)
                        mNotifyCB?.onSuccess(device.btDevice, characteristic?.uuid, retVal)
                    }
                    shouldComplete(false)
                }.execute()
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            mBleDeviceList.get(gatt)?.let{ device ->
                CustomGattCallback(status).apply{
                    onSuccess{
                        mWriteDescriptorCB?.onSuccess(device.btDevice, descriptor?.uuid)
                    }
                    onFailure {
                        mWriteDescriptorCB?.onFailure(device.btDevice, descriptor?.uuid)
                    }

                    shouldComplete(isSameDeviceAsLastOperation(device))
                }.execute()
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
    //endregion

    //region Callback Setters
    fun setReadCallback(simpleGattCallback: SimpleGattCallback){
        mReadCB = simpleGattCallback
    }
    fun setWriteCallback(simpleGattCallback: SimpleGattCallback){
        mWriteCB = simpleGattCallback
    }
    fun setNotificationCallback(simpleGattCallback: SimpleGattCallback){
        mNotifyCB = simpleGattCallback
    }
    fun setConnectionCallback(simpleGattCallback: SimpleGattCallback){
        mConnectionCB = simpleGattCallback
    }
    fun setDiscoveryCallback(simpleGattCallback: SimpleGattCallback){
        mDiscoveryCB = simpleGattCallback
    }
    fun setDisconnectionCallback(simpleGattCallback: SimpleGattCallback){
        mDisconnectionCB = simpleGattCallback
    }
    fun setDescriptorWriteCallback(simpleGattCallback: SimpleGattCallback){
        mWriteDescriptorCB = simpleGattCallback
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
    
    //region Connect Methods
    fun connect(address: String, timeout: Long = 0L, delay: Long = 0L): Boolean{
        addDevice(address)
        return mBleDeviceList.get(address)?.let{ device ->
            mOperationDeque.add(Operation.Connect(device, timeout, delay))
        } ?: false
    }
    fun connect(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return connect(device.address, timeout, delay)
    }
    fun connect(address: String, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setConnectionCallback(callback)
        return connect(address, timeout, delay)
    }
    fun connect(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setConnectionCallback(callback)
        return connect(device.address, timeout, delay)
    }
    //endregion
    
    //region Disconnect Methods
    fun disconnect(address: String, timeout: Long = 0L, delay: Long = 0L): Boolean{
       return  mBleDeviceList.get(address)?.let{ device ->
            mOperationDeque.add(Operation.Disconnect(device, timeout, delay))
        } ?: false
    }
    fun disconnect(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return disconnect(device.address, timeout, delay)
    }
    fun disconnect(address: String, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setDisconnectionCallback(callback)
        return disconnect(address, timeout, delay)
    }
    fun disconnect(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setDisconnectionCallback(callback)
        return disconnect(device.address, timeout, delay)
    }
    //endregion
    
    //region Discover Methods
    fun discover(address: String, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mBleDeviceList.get(address)?.let { device ->
            mOperationDeque.add(Operation.Discover(device, timeout, delay))
        } ?: false
    }
    fun discover(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return discover(device.address, timeout, delay)
    }
    fun discover(address: String, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setDiscoveryCallback(callback)
        return discover(address, timeout, delay)
    }
    fun discover(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setDiscoveryCallback(callback)
        return discover(device.address, timeout, delay)
    }
    //endregion
    
    //region Read Methods
    fun read(address: String, uuid: String, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mBleDeviceList.get(address)?.let{ device ->
            mOperationDeque.add(Operation.Read(device, uuid, timeout, delay))
        } ?: false
    }
    fun read(address: String, uuid: UUID, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return read(address, uuid.toString(), timeout, delay) 
    }
    fun read(device: BluetoothDevice, uuid: String, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return read(device.address, uuid, timeout, delay) 
    }
    fun read(device: BluetoothDevice, uuid: UUID, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return read(device.address, uuid.toString(), timeout, delay) 
    }
    fun read(address: String, uuid: String, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setReadCallback(callback)
        return read(address, uuid, timeout, delay) 
    }
    fun read(address: String, uuid: UUID, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setReadCallback(callback)
        return read(address, uuid.toString(), timeout, delay) 
    }
    fun read(device: BluetoothDevice, uuid: String, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setReadCallback(callback)
        return read(device.address, uuid, timeout, delay) 
    }
    fun read(device: BluetoothDevice, uuid: UUID, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setReadCallback(callback)
        return read(device.address, uuid.toString(), timeout, delay) 
    }
    //endregion

    //region Write Methods
    fun write(address: String, uuid: String, value: Any, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mBleDeviceList.get(address)?.let{ device ->
            val writeValue: ByteArray = getWriteValue(device, uuid, value)
            mOperationDeque.add(Operation.Write(device, uuid, writeValue, timeout, delay))
        } ?: false
    }
    fun write(address: String, uuid: UUID, value: Any, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return write(address, uuid.toString(), value, timeout, delay) 
    }
    fun write(device: BluetoothDevice, uuid: String, value: Any, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return write(device.address, uuid, value, timeout, delay) 
    }
    fun write(device: BluetoothDevice, uuid: UUID, value: Any, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return write(device.address, uuid.toString(), value, timeout, delay) 
    }
    fun write(address: String, uuid: String, value: Any, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setWriteCallback(callback)
        return write(address, uuid, value, timeout, delay) 
    }
    fun write(address: String, uuid: UUID, value: Any, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setWriteCallback(callback)
        return write(address, uuid.toString(), value, timeout, delay) 
    }
    fun write(device: BluetoothDevice, uuid: String, value: Any, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setWriteCallback(callback)
        return write(device.address, uuid, value, timeout, delay) 
    }
    fun write(device: BluetoothDevice, uuid: UUID, value: Any, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setWriteCallback(callback)
        return write(device.address, uuid.toString(), value, timeout, delay) 
    }
    //endregion

    //region Set Notification Methods
    fun setNotification(address: String, uuid: String, enable: Boolean, timeout: Long = 0L, delay: Long = 0L): Boolean{
        mBleDeviceList.get(address)?.let{ device ->
            return mOperationDeque.add(Operation.WriteDescriptor(device, uuid, NOTIFICATION_DESCRIPTOR_UUID, ENABLE_NOTIFICATION_VALUE, timeout, delay)) &&
                    mOperationDeque.add(Operation.Notify(device, uuid, enable, timeout, delay))
        }
        return false
    }
    fun setNotification(address: String, uuid: UUID, enable: Boolean, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return setNotification(address, uuid.toString(), enable, timeout, delay) 
    }
    fun setNotification(device: BluetoothDevice, uuid: String, enable: Boolean, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return setNotification(device.address, uuid, enable, timeout, delay) 
    }
    fun setNotification(device: BluetoothDevice, uuid: UUID, enable: Boolean, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return setNotification(device.address, uuid.toString(), enable, timeout, delay) 
    }
    fun setNotification(address: String, uuid: String, enable: Boolean, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setNotificationCallback(callback)
        return setNotification(address, uuid, enable, timeout, delay) 
    }
    fun setNotification(address: String, uuid: UUID, enable: Boolean, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setNotificationCallback(callback)
        return setNotification(address, uuid.toString(), enable, timeout, delay) 
    }
    fun setNotification(device: BluetoothDevice, uuid: String, enable: Boolean, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setNotificationCallback(callback)
        return setNotification(device.address, uuid, enable, timeout, delay) 
    }
    fun setNotification(device: BluetoothDevice, uuid: UUID, enable: Boolean, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setNotificationCallback(callback)
        return setNotification(device.address, uuid.toString(), enable, timeout, delay) 
    }
    //endregion

    //region Write Descriptor Methods
    fun writeDescriptor(address: String, characteristicUuid: String, descriptorUuid: String, value: ByteArray, timeout: Long = 0L, delay: Long = 0L): Boolean{
        mBleDeviceList.get(address)?.let{ device ->
            return mOperationDeque.add(Operation.WriteDescriptor(device, characteristicUuid, descriptorUuid, value, timeout, delay))
        }
        return false
    }

    // TODO()

    //endregion

    fun addDeviceTemplate(template: DeviceTemplate): Boolean{
        return mDeviceTemplateList.add(template)
    }

    //endregion

    //region PRIVATE METHODS
    private fun getDeviceFromAddress(address: String): BluetoothDevice?{
        return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
    }

    private fun getWriteValue(device: BleDevice, uuid: String, value: Any): ByteArray{
        return if(value !is ByteArray) {
            mDeviceTemplateList.get(device.name)
                    ?.getCharacteristicFromUUID(uuid)
                    ?.writeConversion?.invoke(value)
                    ?: byteArrayOf()
        }
        else {
            value
        }
    }

    private fun getCharacteristicValue(device: BleDevice, characteristic: BluetoothGattCharacteristic?): Any?{
        return characteristic?.let { char ->
            mDeviceTemplateList.get(device)?.getCharacteristicFromUUID(char.uuid)?.readConversion?.invoke(char.value)
                    ?: char.value
        }
    }

    private fun isSameDeviceAsLastOperation(device: BleDevice): Boolean{
        return mOperationDeque.getLastOperationDevice().address == device.address
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