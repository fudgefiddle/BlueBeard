package com.fudgefiddle.bluebeard.viewmodels

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fudgefiddle.bluebeard.*
import com.fudgefiddle.bluebeard.operation.ReturnOperation

class OperationViewModel(app: Application) : AndroidViewModel(app) {

    private val isInitialized = MutableLiveData<Boolean>()

    private val isOperating = MutableLiveData<Boolean>()

    private val onReadSuccess = MutableLiveData<ReturnOperation>()
    private val onReadFailure = MutableLiveData<ReturnOperation>()

    private val onWriteSuccess = MutableLiveData<ReturnOperation>()
    private val onWriteFailure = MutableLiveData<ReturnOperation>()

    private val onConnectSuccess = MutableLiveData<ReturnOperation>()
    private val onConnectFailure = MutableLiveData<ReturnOperation>()

    private val onDiscoverSuccess = MutableLiveData<ReturnOperation>()
    private val onDiscoverFailure = MutableLiveData<ReturnOperation>()

    private val onDisconnectSuccess = MutableLiveData<ReturnOperation>()
    private val onDisconnectFailure = MutableLiveData<ReturnOperation>()

    private val onDescriptorWriteSuccess = MutableLiveData<ReturnOperation>()
    private val onDescriptorWriteFailure = MutableLiveData<ReturnOperation>()

    private val onNotification = MutableLiveData<ReturnOperation>()

    val isInitializedLiveData: LiveData<Boolean> = isInitialized

    val isOperatingLiveData: LiveData<Boolean> = isOperating

    val readSuccessLiveData: LiveData<ReturnOperation> = onReadSuccess
    val getReadFailureLiveData: LiveData<ReturnOperation> = onReadFailure

    val writeSuccessLiveData: LiveData<ReturnOperation> = onWriteSuccess
    val writeFailureLiveData: LiveData<ReturnOperation> = onWriteFailure

    val connectSuccessLiveData: LiveData<ReturnOperation> = onConnectSuccess
    val connectFailureLiveData: LiveData<ReturnOperation> = onConnectFailure

    val disconnectSuccessLiveData: LiveData<ReturnOperation> = onDisconnectSuccess
    val disconnectFailureLiveData: LiveData<ReturnOperation> = onDisconnectFailure

    val discoverSuccessLiveData: LiveData<ReturnOperation> = onDiscoverSuccess
    val discoverFailureLiveData: LiveData<ReturnOperation> = onDiscoverFailure

    val descriptorWriteSuccessLiveData: LiveData<ReturnOperation> = onDescriptorWriteSuccess
    val descriptorWriteFailureLiveData: LiveData<ReturnOperation> = onDescriptorWriteFailure

    val notificationLiveData: LiveData<ReturnOperation> = onNotification

    private val mOperationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) = with(intent) {
            val operation: ReturnOperation = getParcelableExtra(EXTRA_OPERATION)
            when (action) {


                ACTION_SERVICE_STATE_CHANGED -> isInitialized.value = getBooleanExtra(EXTRA_STATE, false)

                ACTION_OPERATION_QUEUE_STATE_CHANGE -> isOperating.value = getBooleanExtra(EXTRA_STATE, false)

                ACTION_CONNECTION_STATE_CHANGE -> {
                    when(getIntExtra(EXTRA_STATE, -1)){
                        BluetoothProfile.STATE_CONNECTED -> {
                            if (getIntExtra(EXTRA_STATUS, -1) == GATT_SUCCESS)
                                onConnectSuccess.value = operation
                            else
                                onConnectFailure.value = operation
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (getIntExtra(EXTRA_STATUS, -1) == GATT_SUCCESS)
                                onDisconnectSuccess.value = operation
                            else
                                onDisconnectFailure.value = operation
                        }
                    }
                }
                ACTION_DISCOVER -> {
                    if (getIntExtra(EXTRA_STATUS, -1) == GATT_SUCCESS)
                        onDiscoverSuccess.value = operation
                    else
                        onDiscoverFailure.value = operation
                }
                ACTION_READ -> {
                    if (getIntExtra(EXTRA_STATUS, -1) == GATT_SUCCESS)
                        onReadSuccess.value = operation
                    else
                        onReadFailure.value = operation

                }
                ACTION_WRITE -> {
                    if (getIntExtra(EXTRA_STATUS, -1) == GATT_SUCCESS)
                        onWriteSuccess.value = operation
                    else
                        onWriteFailure.value = operation
                }
                ACTION_NOTIFICATION -> {
                    onNotification.value = operation
                }
                ACTION_WRITE_DESCRIPTOR -> {
                    if (getIntExtra(EXTRA_STATUS, -1) == GATT_SUCCESS)
                        onDescriptorWriteSuccess.value = operation
                    else
                        onDescriptorWriteFailure.value = operation
                }
            }
        }

    }

    init {
        getApplication<Application>().registerReceiver(mOperationReceiver, INTENT_FILTER_ALL)
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(mOperationReceiver)
    }
}