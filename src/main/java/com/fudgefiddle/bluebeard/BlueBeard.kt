package com.example.bluebeard

import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.example.bluebeard.callbacks.SimpleGattCallback
import com.example.bluebeard.device_template.DeviceTemplate
import java.util.*

class BlueBeard : ServiceConnection {

    private var mStateCallback: StateCallback? = null
    private var mService: BleService? = null

    //region SERVICE CONNECTION OVERRIDE METHODS
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        mService = (service as BleService.LocalBinder).getService()
        mStateCallback?.initialized()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        mService = null
        mStateCallback?.uninitialized()
    }
    //endregion

    //region PUBLIC METHODS
    /** Start & Stop Service */
    fun start(context: Context, stateCallback: StateCallback){
        mStateCallback = stateCallback
        context.bindService(Intent(context, BleService::class.java), this, Context.BIND_AUTO_CREATE)
    }
    fun stop(context: Context) = context.unbindService(this)

    /** Set Callbacks */
    fun setReadCallback(simpleGattCallback: SimpleGattCallback) = mService?.setReadCallback(simpleGattCallback)
    fun setWriteCallback(simpleGattCallback: SimpleGattCallback) = mService?.setWriteCallback(simpleGattCallback)
    fun setNotificationCallback(simpleGattCallback: SimpleGattCallback) = mService?.setNotificationCallback(simpleGattCallback)
    fun setConnectionCallback(simpleGattCallback: SimpleGattCallback) = mService?.setConnectionCallback(simpleGattCallback)
    fun setDiscoveryCallback(simpleGattCallback: SimpleGattCallback) = mService?.setDiscoveryCallback(simpleGattCallback)
    fun setDisconnectionCallback(simpleGattCallback: SimpleGattCallback) = mService?.setDisconnectionCallback(simpleGattCallback)

    /** Operation Parameters */
    fun setAttemptLimit(limit: Int) = mService?.setAttemptLimit(limit)
    fun enableFaultCorrection(enable: Boolean) = mService?.enableFaultCorrection(enable)
    
    fun addDeviceTemplate(template: DeviceTemplate): Boolean{
        return mService?.addDeviceTemplate(template) ?: false
    }

    /** Add Device Methods */
    fun addDevice(address: String): Boolean{
        return mService?.addDevice(address) ?: false
    }
    fun addDevice(device: BluetoothDevice): Boolean{
        return mService?.addDevice(device.address) ?: false
    }
    /** Connect Methods */
    fun connect(address: String, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.connect(address, timeout, delay) ?: false
    }
    fun connect(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.connect(device.address, timeout, delay) ?: false
    }
    fun connect(address: String, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setConnectionCallback(callback)
        return mService?.connect(address, timeout, delay) ?: false
    }
    fun connect(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setConnectionCallback(callback)
        return mService?.connect(device.address, timeout, delay) ?: false
    }
    
    /** Disconnect Methods */
    fun disconnect(address: String, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.disconnect(address, timeout, delay) ?: false
    }
    fun disconnect(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.disconnect(device.address, timeout, delay) ?: false
    }
    fun disconnect(address: String, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setDisconnectionCallback(callback)
        return mService?.disconnect(address, timeout, delay) ?: false
    }
    fun disconnect(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setDisconnectionCallback(callback)
        return mService?.disconnect(device.address, timeout, delay) ?: false
    }

    /** Discover Methods */
    fun discover(address: String, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.discover(address, timeout, delay) ?: false
    }
    fun discover(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.discover(device.address, timeout, delay) ?: false
    }
    fun discover(address: String, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setDiscoveryCallback(callback)
        return mService?.discover(address, timeout, delay) ?: false
    }
    fun discover(device: BluetoothDevice, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setDiscoveryCallback(callback)
        return mService?.discover(device.address, timeout, delay) ?: false
    }

    /** Read Methods */
    fun read(address: String, uuid: String, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.read(address, uuid, timeout, delay) ?: false
    }
    fun read(address: String, uuid: UUID, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.read(address, uuid.toString(), timeout, delay) ?: false
    }
    fun read(device: BluetoothDevice, uuid: String, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.read(device.address, uuid, timeout, delay) ?: false
    }
    fun read(device: BluetoothDevice, uuid: UUID, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.read(device.address, uuid.toString(), timeout, delay) ?: false
    }
    fun read(address: String, uuid: String, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setReadCallback(callback)
        return mService?.read(address, uuid, timeout, delay) ?: false
    }
    fun read(address: String, uuid: UUID, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setReadCallback(callback)
        return mService?.read(address, uuid.toString(), timeout, delay) ?: false
    }
    fun read(device: BluetoothDevice, uuid: String, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setReadCallback(callback)
        return mService?.read(device.address, uuid, timeout, delay) ?: false
    }
    fun read(device: BluetoothDevice, uuid: UUID, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setReadCallback(callback)
        return mService?.read(device.address, uuid.toString(), timeout, delay) ?: false
    }
    
    /** Write Methods */
    fun write(address: String, uuid: String, value: Any, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.write(address, uuid, value, timeout, delay) ?: false
    }
    fun write(address: String, uuid: UUID, value: Any, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.write(address, uuid.toString(), value, timeout, delay) ?: false
    }
    fun write(device: BluetoothDevice, uuid: String, value: Any, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.write(device.address, uuid, value, timeout, delay) ?: false
    }
    fun write(device: BluetoothDevice, uuid: UUID, value: Any, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.write(device.address, uuid.toString(), value, timeout, delay) ?: false
    }
    fun write(address: String, uuid: String, value: Any, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setWriteCallback(callback)
        return mService?.write(address, uuid, value, timeout, delay) ?: false
    }
    fun write(address: String, uuid: UUID, value: Any, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setWriteCallback(callback)
        return mService?.write(address, uuid.toString(), value, timeout, delay) ?: false
    }
    fun write(device: BluetoothDevice, uuid: String, value: Any, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setWriteCallback(callback)
        return mService?.write(device.address, uuid, value, timeout, delay) ?: false
    }
    fun write(device: BluetoothDevice, uuid: UUID, value: Any, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setWriteCallback(callback)
        return mService?.write(device.address, uuid.toString(), value, timeout, delay) ?: false
    }

    /** setNotification Methods */
    fun setNotification(address: String, uuid: String, enable: Boolean, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.setNotification(address, uuid, enable, timeout, delay) ?: false
    }
    fun setNotification(address: String, uuid: UUID, enable: Boolean, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.setNotification(address, uuid.toString(), enable, timeout, delay) ?: false
    }
    fun setNotification(device: BluetoothDevice, uuid: String, enable: Boolean, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.setNotification(device.address, uuid, enable, timeout, delay) ?: false
    }
    fun setNotification(device: BluetoothDevice, uuid: UUID, enable: Boolean, timeout: Long = 0L, delay: Long = 0L): Boolean{
        return mService?.setNotification(device.address, uuid.toString(), enable, timeout, delay) ?: false
    }
    fun setNotification(address: String, uuid: String, enable: Boolean, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setNotificationCallback(callback)
        return mService?.setNotification(address, uuid, enable, timeout, delay) ?: false
    }
    fun setNotification(address: String, uuid: UUID, enable: Boolean, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setNotificationCallback(callback)
        return mService?.setNotification(address, uuid.toString(), enable, timeout, delay) ?: false
    }
    fun setNotification(device: BluetoothDevice, uuid: String, enable: Boolean, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setNotificationCallback(callback)
        return mService?.setNotification(device.address, uuid, enable, timeout, delay) ?: false
    }
    fun setNotification(device: BluetoothDevice, uuid: UUID, enable: Boolean, timeout: Long = 0L, delay: Long = 0L, callback: SimpleGattCallback): Boolean{
        setNotificationCallback(callback)
        return mService?.setNotification(device.address, uuid.toString(), enable, timeout, delay) ?: false
    }
    //endregion

    interface StateCallback {
        fun initialized()
        fun uninitialized()
    }
}