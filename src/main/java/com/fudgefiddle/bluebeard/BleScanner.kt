package com.fudgefiddle.bluebeard

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.annotation.RequiresApi

/**
 * BleScanner
 *
 * The sealed class' companion method 'getScanner' will return the appropriate scanner type
 * depending on API level.
 *
 * NOTE: Versions >= Android 7.0 / API: 24 / VERSION_CODE: N have a security measure that will lock the
 * bluetooth adapter if a scan is started and stopped 5 times within 30 seconds. It is recommended to
 * have at least 6 seconds between each start an stop
 *
 */

/**
 * PRIVATE
 * @property mAdapter: BluetoothAdapter?
 * @property mIsScanning: Boolean - Whether or not scan is in progress
 * @property mIsScanningLiveData: MutableLiveData<Boolean> - LiveData of whether or not scan is in progress
 * @property mCustomScanFilter: (BluetoothDevice) -> Boolean - A custom set function that filters BluetoothDevice objects
 * @property mScanResultLiveData: MutableLiveData<BluetoothDevice> - LiveData of devices returned from scan
 *
 * PUBLIC
 * @property isScanning: Boolean - Immutable version of mIsScanning
 * @property isScanningLiveData: Boolean - Immutable version mIsScanningLiveData
 * @property scanResultLiveData: LiveData<BluetoothDevice> - Immutable version of mScanResultLiveData
 */
sealed class BleScanner {
    protected abstract val mAdapter: BluetoothAdapter?

    protected abstract fun abStartScan(): Boolean
    protected abstract fun abStopScan(): Boolean

    protected var mIsScanning: () -> Boolean = { mIsScanningLiveData.value ?: false }
    protected val mIsScanningLiveData: MutableLiveData<Boolean> = MutableLiveData()
    protected var mCustomScanFilter: CustomScanFilter = object : CustomScanFilter{
        override fun filter(device: BluetoothDevice): Boolean {
            return true
        }
    }
    protected val mScanResultLiveData: MutableLiveData<BluetoothDevice> = MutableLiveData()

    val isScanning: () -> Boolean = { mIsScanning() }
    val isScanningLiveData: LiveData<Boolean> = mIsScanningLiveData
    val scanResultLiveData: LiveData<BluetoothDevice> = mScanResultLiveData

    /**
     * @return if mAdapter was not null when executing
     */
    fun startScan(): Boolean {
        if (!isScanning())
            mIsScanningLiveData.value = abStartScan()
        return isScanning()
    }

    /**
     * @return if mAdapter was not null when executing
     */
    fun stopScan(): Boolean {
        if (isScanning())
            mIsScanningLiveData.value = !abStopScan()
        return !isScanning()
    }

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
         * @param context - Needed to initiate api 18 scanner
         * @param callback - Callback to handle scan results. Can be null if LiveData preferred.
         * @return instance of BleScanner dependent upon api level
         */
        fun getScanner(context: Context, callback: CustomScanCallback? = null): BleScanner {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> BleScanner21(callback)
                else -> BleScanner18(context, callback)
            }
        }
    }

    @RequiresApi(21)
    class BleScanner21(callback: CustomScanCallback?) : BleScanner() {
        override val mAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        private val mScanSettings: ScanSettings = ScanSettings.Builder().build()
        private val mScanFilter: List<ScanFilter> = emptyList()
        private val mBleScanCallback: ScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult?) {
                r?.let { result ->
                    if (mCustomScanFilter.filter(result.device))
                        Handler(Looper.getMainLooper()).post {
                            callback?.onScanResult(callbackType, result)
                            mScanResultLiveData.value = result.device
                        }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                mIsScanningLiveData.value = false
                Handler(Looper.getMainLooper()).post {
                    callback?.onScanFailed(errorCode)
                }
            }
        }

        override fun abStartScan(): Boolean {
            return mAdapter?.bluetoothLeScanner?.startScan(
                    mScanFilter,
                    mScanSettings,
                    mBleScanCallback
            ) != null
        }

        override fun abStopScan(): Boolean {
            return mAdapter?.bluetoothLeScanner?.stopScan(mBleScanCallback) != null
        }

    }

    @Suppress("DEPRECATION")
    class BleScanner18(context: Context, callback: CustomScanCallback?) : BleScanner() {
        override val mAdapter: BluetoothAdapter? =
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        private val mBleScanCallback: LeScanCallback =
                LeScanCallback { device, rssi, scanRecord ->
                    device?.let { d ->
                        if (mCustomScanFilter.filter(d))
                            Handler(Looper.getMainLooper()).post {
                                callback?.onLeScan(device, rssi, scanRecord)
                                mScanResultLiveData.value = d
                            }
                    }
                }

        override fun abStartScan(): Boolean {
            return mAdapter?.startLeScan(mBleScanCallback) != null
        }

        override fun abStopScan(): Boolean {
            return mAdapter?.stopLeScan(mBleScanCallback) != null
        }
    }
}

