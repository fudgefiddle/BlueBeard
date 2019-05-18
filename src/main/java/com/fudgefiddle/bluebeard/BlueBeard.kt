package com.fudgefiddle.bluebeard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.fudgefiddle.bluebeard.callbacks.StateCallback

class BlueBeard : ServiceConnection {

    private var mStateCallback: StateCallback? = null
    private var mService: BlueBeardService? = null

    //region SERVICE CONNECTION OVERRIDE METHODS
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        mService = (service as BlueBeardService.LocalBinder).getService()
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
        context.bindService(Intent(context, BlueBeardService::class.java), this, Context.BIND_AUTO_CREATE)
    }
    fun stop(context: Context) = context.unbindService(this)

    fun getService(): BlueBeardService? = mService
    //endregion
}