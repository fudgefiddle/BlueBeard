package com.fudgefiddle.bluebeard

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.annotation.RequiresApi
import timber.log.Timber
import java.util.*

/**
 * BleScanner
 *
 * The sealed class' companion method 'getInstance' will return the appropriate scanner type
 * depending on API level.
 *
 * NOTE: Versions >= Android 7.0 / API: 24 / VERSION_CODE: N have a security measure that will lock the
 * bluetooth adapter if a scan is started and stopped 5 times within 30 seconds. It is recommended to
 * have at least 6 seconds between each start an stop
 *
 * @property mAdapter: BluetoothAdapter?
 * @property mIsScanning: Boolean - Whether or not scan is in progress
 * @property mIsScanningLiveData: MutableLiveData<Boolean> - LiveData of whether or not scan is in progress
 * @property mCustomScanFilter: (BluetoothDevice) -> Boolean - A custom set function that filters BluetoothDevice objects
 */
sealed class BleScanner {
    protected val mAdapter: BluetoothAdapter? = getDefaultAdapter()

    protected abstract fun startScan()
    protected abstract fun stopScan()

    protected var mIsScanning: Boolean = false
    protected var mCustomScanFilter: CustomScanFilter = object : CustomScanFilter{
        override fun filter(device: BluetoothDevice): Boolean { return true }
    }

    fun isScanning(): Boolean = mIsScanning

    /**
     * Sets a custom filter for devices when received
     * @param filter - object that implements CustomScanFilter interface
     */
    fun setScanFilter(filter: CustomScanFilter) {
        mCustomScanFilter = filter
    }

    /**
     * CustomScanFilter
     * Interface to filter le device objects received during scan
     */
    interface CustomScanFilter {
        fun filter(device: BluetoothDevice): Boolean
    }

    /**
     * CustomScanCallback
     * Callbacks to return scan results
     */
    interface CustomScanCallback : LeScanCallback{
        @RequiresApi(21)
        fun onScanResult(callbackType: Int, result: ScanResult)

        @RequiresApi(21)
        fun onScanFailed(errorCode: Int)
    }

    companion object {
        /**
         * @param context - Needed to send broadcasts
         * @param callback - Callback to handle scan results. Can be null if LiveData preferred.
         * @return instance of BleScanner dependent upon api level
         */
        fun getInstance(context: Context? = null, callback: CustomScanCallback? = null): BleScanner {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> { BleScanner24(context, callback) }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> BleScanner21(context, callback)
                else -> BleScanner18(context, callback)
            }
        }

        const val STATE_STARTING: Int = 0
        const val STATE_STARTED: Int = 1
        const val STATE_STOPPED: Int = 2
    }

    @RequiresApi(24)
    class BleScanner24(val context: Context?, callback: CustomScanCallback?) : BleScanner() {
        private val mScanSettings: ScanSettings = ScanSettings.Builder().build()
        private val mScanFilter: List<ScanFilter> = emptyList()
        private val mBleScanCallback: ScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult?) {
                r?.let { result ->
                    if (mCustomScanFilter.filter(result.device))
                        Handler(Looper.getMainLooper()).post {
                            callback?.onScanResult(callbackType, result)
                            context?.sendBroadcast(Intent().apply{
                                action = ACTION_SCAN_RESULT
                                putExtra(EXTRA_SCAN_RESULT_21, result)
                            })
                        }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.d("onScanFailed: %s", ScanErrors.toString(errorCode))
                Handler(Looper.getMainLooper()).post {
                    callback?.onScanFailed(errorCode)
                    context?.sendBroadcast(Intent().apply{
                        action = ACTION_SCAN_ERROR
                        putExtra(EXTRA_SCAN_ERROR_CODE, errorCode)
                    })
                    context?.sendBroadcast(Intent().apply{
                        action = ACTION_SCAN_STATE_CHANGED
                        putExtra(EXTRA_STATE, STATE_STOPPED)
                    })
                }
            }
        }

        private var mLastStartedScanTime: Long = 0
        private val mScanHandler = Handler()
        private val mScanRunnable = Runnable {
            context?.sendBroadcast(Intent().apply{
                action = ACTION_SCAN_STATE_CHANGED
                putExtra(EXTRA_STATE, STATE_STARTED)
            })
            mLastStartedScanTime = Calendar.getInstance().timeInMillis
            mIsScanning = true
            mAdapter?.bluetoothLeScanner
                    ?.startScan(
                            mScanFilter,
                            mScanSettings,
                            mBleScanCallback)
        }

        override fun startScan() {
            val timeNeededUntilNextScan = getTimeUntilNextPossibleScan()

            if(timeNeededUntilNextScan > 0){
                context?.sendBroadcast(Intent().apply{
                    action = ACTION_SCAN_STATE_CHANGED
                    putExtra(EXTRA_STATE, STATE_STARTING)
                })
                mScanHandler.postDelayed(mScanRunnable, timeNeededUntilNextScan)
            }else{
                mScanRunnable.run()
            }
        }

        override fun stopScan() {
            mIsScanning = false
            context?.sendBroadcast(Intent().apply{
                action = ACTION_SCAN_STATE_CHANGED
                putExtra(EXTRA_STATE, STATE_STOPPED)
            })
            mAdapter?.bluetoothLeScanner?.stopScan(mBleScanCallback)
        }

        private fun getTimeUntilNextPossibleScan(): Long {
            val timeSinceLastScan: Long = Calendar.getInstance().timeInMillis - mLastStartedScanTime
            // If we are under the six second limit then wait for the amount of time needed
            return if(timeSinceLastScan < 6000) 6000 - timeSinceLastScan else 0
        }
    }

    @RequiresApi(21)
    class BleScanner21(val context: Context?, callback: CustomScanCallback?) : BleScanner() {
        private val mScanSettings: ScanSettings = ScanSettings.Builder().build()
        private val mScanFilter: List<ScanFilter> = emptyList()
        private val mBleScanCallback: ScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult?) {
                r?.let { result ->
                    if (mCustomScanFilter.filter(result.device))
                        Handler(Looper.getMainLooper()).post {
                            callback?.onScanResult(callbackType, result)
                            context?.sendBroadcast(Intent().apply{
                                action = ACTION_SCAN_RESULT
                                putExtra(EXTRA_SCAN_RESULT_21, result)
                            })
                        }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.d("onScanFailed: %s", ScanErrors.toString(errorCode))
                Handler(Looper.getMainLooper()).post {
                    callback?.onScanFailed(errorCode)
                    context?.sendBroadcast(Intent().apply{
                        action = ACTION_SCAN_ERROR
                        putExtra(EXTRA_SCAN_ERROR_CODE, errorCode)
                    })
                }
            }
        }

        override fun startScan() {
            context?.sendBroadcast(Intent().apply{
                action = ACTION_SCAN_STATE_CHANGED
                putExtra(EXTRA_STATE, STATE_STARTED)
            })
            mIsScanning = true
            mAdapter?.bluetoothLeScanner
                    ?.startScan(
                            mScanFilter,
                            mScanSettings,
                            mBleScanCallback)
        }

        override fun stopScan() {
            mIsScanning = false
            context?.sendBroadcast(Intent().apply{
                action = ACTION_SCAN_STATE_CHANGED
                putExtra(EXTRA_STATE, STATE_STOPPED)
            })
            mAdapter?.bluetoothLeScanner?.stopScan(mBleScanCallback) }
    }

    @Suppress("DEPRECATION")
    class BleScanner18(val context: Context?, callback: CustomScanCallback?) : BleScanner() {
        private val mBleScanCallback: LeScanCallback =
                LeScanCallback { device, rssi, scanRecord ->
                    device?.let { d ->
                        if (mCustomScanFilter.filter(d))
                            Handler(Looper.getMainLooper()).post {
                                callback?.onLeScan(device, rssi, scanRecord)
                                context?.sendBroadcast(Intent().apply{
                                    action = ACTION_SCAN_RESULT
                                    putExtra(EXTRA_SCAN_RESULT_18, device)
                                    putExtra(EXTRA_SCAN_RSSI, rssi)
                                    putExtra(EXTRA_SCAN_SCAN_RECORD, scanRecord)
                                })
                            }
                    }
                }

        override fun startScan() {
            context?.sendBroadcast(Intent().apply{
                action = ACTION_SCAN_STATE_CHANGED
                putExtra(EXTRA_STATE, STATE_STARTED)
            })
            mAdapter?.startLeScan(mBleScanCallback)
        }

        override fun stopScan() {
            context?.sendBroadcast(Intent().apply{
                action = ACTION_SCAN_STATE_CHANGED
                putExtra(EXTRA_STATE, STATE_STOPPED)
            })
            mAdapter?.stopLeScan(mBleScanCallback)
        }
    }
}

