package com.fudgefiddle.bluebeard

import android.content.IntentFilter

const val ACTION_INITIALIZED: String = "com.fudgefiddle.bluebeard.INITIALIZED"
const val ACTION_UNINITIALIZED: String = "com.fudgefiddle.bluebeard.UNINITIALIZED"
const val ACTION_OPERATION_QUEUE_START: String = "com.fudgefiddle.bluebeard.OPERATION_QUEUE_START"
const val ACTION_OPERATION_QUEUE_END: String = "com.fudgefiddle.bluebeard.OPERATION_QUEUE_END"
const val ACTION_READ: String = "com.fudgefiddle.bluebeard.READ"
const val ACTION_WRITE: String = "com.fudgefiddle.bluebeard.WRITE"
const val ACTION_NOTIFICATION: String = "com.fudgefiddle.bluebeard.NOTIFICATION"
const val ACTION_CONNECT: String = "com.fudgefiddle.bluebeard.CONNECT"
const val ACTION_DISCONNECT: String = "com.fudgefiddle.bluebeard.DISCONNECT"
const val ACTION_DISCOVER: String = "com.fudgefiddle.bluebeard.DISCOVER"
const val ACTION_WRITE_DESCRIPTOR: String = "com.fudgefiddle.bluebeard.WRITE_DESCRIPTOR"


const val EXTRA_OPERATION: String = "com.fudgefiddle.bluebeard.EXTRA_OPERATION"
const val EXTRA_STATUS: String = "com.fudgefiddle.bluebeard.EXTRA_STATUS"

const val NOTIFICATION_DESCRIPTOR_UUID: String = "00002902-0000-1000-8000-00805f9b34fb"
const val BLANK_UUID: String = "00000000-0000-1000-8000-00805f9b34fb"

val INTENT_FILTER_ALL = IntentFilter().apply{
    addAction(ACTION_INITIALIZED)
    addAction(ACTION_UNINITIALIZED)
    addAction(ACTION_READ)
    addAction(ACTION_WRITE)
    addAction(ACTION_NOTIFICATION)
    addAction(ACTION_CONNECT)
    addAction(ACTION_DISCONNECT)
    addAction(ACTION_DISCOVER)
    addAction(ACTION_WRITE_DESCRIPTOR)
}
