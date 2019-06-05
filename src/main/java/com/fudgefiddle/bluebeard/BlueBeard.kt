package com.fudgefiddle.bluebeard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.fudgefiddle.bluebeard.device_template.DeviceTemplate

class BlueBeard : ServiceConnection {

    private lateinit var mContext: Context
    private var mService: BlueBeardService? = null
    private lateinit var mTemplateList: List<DeviceTemplate>

    //region SERVICE CONNECTION OVERRIDE METHODS
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        mService = (service as BlueBeardService.LocalBinder).getService()
        mTemplateList.forEach{ temp ->
            mService?.deviceTemplates?.add(temp)
        }
        onStateChange(true)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        onStateChange(false)
        mService = null
    }
    //endregion

    private fun onStateChange(enable: Boolean){
        mContext.sendBroadcast(Intent().apply{
            action = ACTION_SERVICE_STATE_CHANGED
            putExtra(EXTRA_STATE, enable)
        })
    }

    //region PUBLIC METHODS
    /** Start & Stop Service */
    fun start(context: Context, templateList: List<DeviceTemplate> = listOf()){
        mContext = context
        mTemplateList = templateList
        context.bindService(Intent(context, BlueBeardService::class.java), this, Context.BIND_AUTO_CREATE)
    }
    fun stop(context: Context) = context.unbindService(this)

    fun getService(): BlueBeardService? = mService

    fun requireService(): BlueBeardService = mService!!
    //endregion
}