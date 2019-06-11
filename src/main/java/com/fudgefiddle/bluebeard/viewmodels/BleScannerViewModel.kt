package com.fudgefiddle.bluebeard.viewmodels

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.fudgefiddle.bluebeard.*

class BleScannerViewModel(app: Application) : AndroidViewModel(app) {

    private val mIsScanningLiveData = MutableLiveData<Boolean>()
    private val mScanErrorCodeLiveData = MutableLiveData<Int>()
    private val mScanResult21LiveData = MutableLiveData<ScanResult>()
    private val mScanResult18LiveData = MutableLiveData<BluetoothDevice>()

    val isScanningLiveData: LiveData<Boolean> = mIsScanningLiveData
    val scanErrorCodeLiveData: LiveData<Int> = mScanErrorCodeLiveData
    val scanResult21LiveData: LiveData<ScanResult> = mScanResult21LiveData
    val scanResult18LiveData: LiveData<BluetoothDevice> = mScanResult18LiveData

    private val mBleScannerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) = with(intent) {
            when (action) {
                ACTION_SCAN_STATE_CHANGED -> {
                    mIsScanningLiveData.value =
                            getIntExtra(EXTRA_STATE, -1) == BleScanner.STATE_STARTED
                }
                ACTION_SCAN_ERROR -> {
                    mScanErrorCodeLiveData.value = getIntExtra(EXTRA_SCAN_ERROR_CODE, -1)
                }
                ACTION_SCAN_RESULT -> {
                    val scanResult21 = getParcelableExtra<ScanResult>(EXTRA_SCAN_RESULT_21)
                    val scanResult18 = getParcelableExtra<BluetoothDevice>(EXTRA_SCAN_RESULT_18)

                    if(scanResult21 != null) mScanResult21LiveData.value = scanResult21
                    else mScanResult18LiveData.value = scanResult18
                }
            }
        }
    }

    init {
        getApplication<Application>().registerReceiver(mBleScannerReceiver, IntentFilter().apply {
            addAction(ACTION_SCAN_STATE_CHANGED)
            addAction(ACTION_SCAN_ERROR)
            addAction(ACTION_SCAN_RESULT)
        })
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(mBleScannerReceiver)
    }
}