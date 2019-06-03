package com.fudgefiddle.bluebeard.viewmodels

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.bluetooth.BluetoothAdapter.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class BluetoothAdapterViewModel(app: Application) : AndroidViewModel(app) {

    private val mBluetoothAdapterLiveData: MutableLiveData<Boolean> = MutableLiveData()

    val bluetoothAdapterLiveData: LiveData<Boolean> = mBluetoothAdapterLiveData

    private val mBluetoothAdapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) = with(intent) {
            when (getIntExtra(EXTRA_STATE, ERROR)) {
                /** We only want to catch if the adapter is in the state of turning off or on
                 *  because otherwise we will be reporting twice when the second call comes from
                 *  the completed state */
                STATE_TURNING_OFF -> mBluetoothAdapterLiveData.value = false

                STATE_TURNING_ON -> mBluetoothAdapterLiveData.value = true

                else -> { /* Do nothing... */ }
            }
        }
    }

    init {
        getApplication<Application>().registerReceiver(mBluetoothAdapterReceiver, IntentFilter(ACTION_STATE_CHANGED))
        mBluetoothAdapterLiveData.value = getDefaultAdapter()?.isEnabled ?: false
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(mBluetoothAdapterReceiver)
    }
}