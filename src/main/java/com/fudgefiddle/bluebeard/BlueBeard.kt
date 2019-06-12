package com.fudgefiddle.bluebeard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.fudgefiddle.bluebeard.device_template.DeviceTemplate

class BlueBeard : ServiceConnection {

    private var mService: BlueBeardService? = null
    private var mCallback: StateCallback? = null

    //region SERVICE CONNECTION OVERRIDE METHODS
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        mService = (service as BlueBeardService.LocalBinder).getService()
        mCallback?.onInitialize()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        mService = null
        mCallback?.onUninitialize()
    }
    //endregion



    //region PUBLIC METHODS
    /** Start & Stop Service */
    fun start(context: Context, callback: StateCallback?){
        mCallback = callback
        context.bindService(Intent(context, BlueBeardService::class.java), this, Context.BIND_AUTO_CREATE)
    }
    fun stop(context: Context) = context.unbindService(this)

    fun getService(): BlueBeardService? = mService

    fun requireService(): BlueBeardService = mService!!
    //endregion

    interface StateCallback {
        fun onInitialize()
        fun onUninitialize()
    }
}