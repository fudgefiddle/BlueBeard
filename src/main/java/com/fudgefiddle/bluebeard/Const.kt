package com.fudgefiddle.bluebeard

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.content.IntentFilter
import android.os.Build
import android.support.annotation.RequiresApi

const val ACTION_SERVICE_STATE_CHANGED: String = "com.fudgefiddle.bluebeard.SERVICE_STATE_CHANGED"
const val ACTION_OPERATION_QUEUE_STATE_CHANGE: String = "com.fudgefiddle.bluebeard.OPERATION_QUEUE_STATE_CHANGE"
const val ACTION_CONNECTION_STATE_CHANGE: String = "com.fudgefiddle.bluebeard.CONNECTION_STATE_CHANGE"
const val ACTION_READ: String = "com.fudgefiddle.bluebeard.READ"
const val ACTION_WRITE: String = "com.fudgefiddle.bluebeard.WRITE"
const val ACTION_NOTIFICATION: String = "com.fudgefiddle.bluebeard.NOTIFICATION"
const val ACTION_DISCOVER: String = "com.fudgefiddle.bluebeard.DISCOVER"
const val ACTION_WRITE_DESCRIPTOR: String = "com.fudgefiddle.bluebeard.WRITE_DESCRIPTOR"

const val EXTRA_OPERATION: String = "com.fudgefiddle.bluebeard.EXTRA_OPERATION"
const val EXTRA_STATUS: String = "com.fudgefiddle.bluebeard.EXTRA_STATUS"
const val EXTRA_STATE: String = "com.fudgefiddle.bluebeard.EXTRA_STATE"

const val NOTIFICATION_DESCRIPTOR_UUID: String = "00002902-0000-1000-8000-00805f9b34fb"
const val BLANK_UUID: String = "00000000-0000-1000-8000-00805f9b34fb"

val INTENT_FILTER_ALL = IntentFilter().apply{
    addAction(ACTION_SERVICE_STATE_CHANGED)
    addAction(ACTION_OPERATION_QUEUE_STATE_CHANGE)
    addAction(ACTION_CONNECTION_STATE_CHANGE)
    addAction(ACTION_READ)
    addAction(ACTION_WRITE)
    addAction(ACTION_NOTIFICATION)
    addAction(ACTION_DISCOVER)
    addAction(ACTION_WRITE_DESCRIPTOR)
}

enum class GattStatuses(val id: Int){
    SUCCESS(BluetoothGatt.GATT_SUCCESS),
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    CONNECTION_CONGESTED(BluetoothGatt.GATT_CONNECTION_CONGESTED),
    INSUFFICIENT_AUTHENTICATION(BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION),
    INSUFFICIENT_ENCRYPTION(BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION),
    INVALID_ATTRIBUTE_LENGTH(BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH),
    READ_NOT_PERMITTED(BluetoothGatt.GATT_READ_NOT_PERMITTED),
    WRITE_NOT_PERMITTED(BluetoothGatt.GATT_WRITE_NOT_PERMITTED),
    REQUEST_NOT_SUPPORTED(BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED),
    FAILURE(BluetoothGatt.GATT_FAILURE);

    companion object {
        fun toString(status: Int): String {
            return values().associateBy(GattStatuses::id)[status]?.name ?: "Unknown Gatt Status Code: $status"
        }
    }
}

enum class GattStates(val id: Int){
    CONNECTED(BluetoothProfile.STATE_CONNECTED),
    DISCONNECTED(BluetoothProfile.STATE_DISCONNECTED);

    companion object {
        fun toString(state: Int): String {
            return values().associateBy(GattStates::id)[state]?.name ?: "Unknown Gatt State: $state"
        }
    }
}

@RequiresApi(21)
enum class ScanErrors(val id: Int){
    ALREADY_STARTED(ScanCallback.SCAN_FAILED_ALREADY_STARTED),
    APP_REGISTRATION_FAILED(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED),
    FEATURE_UNSUPPORTED(ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED),
    INTERNAL_ERROR(ScanCallback.SCAN_FAILED_INTERNAL_ERROR);

    companion object {
        fun toString(errorCode: Int): String {
            return values().associateBy(ScanErrors::id)[errorCode]?.name ?: "Unknown Scan Error Code: $errorCode"
        }
    }
}


fun getBlueoothDeviceFromAddress(address: String): BluetoothDevice {
    return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
}