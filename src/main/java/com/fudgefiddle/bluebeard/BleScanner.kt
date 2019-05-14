package com.example.bluebeard

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
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
sealed class BleScanner
{
    //region PROPERTIES
    protected abstract val mAdapter: BluetoothAdapter?

    protected abstract fun abStartScan(): Boolean
    protected abstract fun abStopScan(): Boolean

    protected var mScanning: Boolean = false
    protected var mCustomScanFilter: (BluetoothDevice) -> Boolean = { true }
    //endregion

    //region PUBLIC METHODS
    fun isScanning(): Boolean = mScanning

    fun startScan(): Boolean {
        if (!mScanning)
            mScanning = abStartScan()
        return isScanning()
    }

    fun stopScan(): Boolean {
        if (mScanning)
            mScanning = abStopScan()
        return !isScanning()
    }

    fun setScanFilter(filter: (BluetoothDevice) -> Boolean){
        mCustomScanFilter = filter
    }
    //endregion

    //region CALLBACK INTERFACE
    interface ScanEvent {
        fun onScanResult(result: BluetoothDevice)

        @RequiresApi(21)
        fun onScanResult(result: ScanResult)

        @RequiresApi(21)
        fun onScanError()
    }
    //endregion

    //region COMPANION OBJ
    companion object {
        fun getScanner(context: Context, scanEvent: ScanEvent): BleScanner {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> BleScanner21(scanEvent)
                else                                                  -> BleScanner18(context, scanEvent)
            }
        }
    }
    //endregion

    //region API 21+ SCANNER
    @RequiresApi(21)
    private class BleScanner21(scanEvent: ScanEvent) : BleScanner()
    {
        override val mAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        private val mScanSettings: ScanSettings = ScanSettings.Builder().build()
        private val mScanFilter: List<ScanFilter> = emptyList()
        private val mBleScanCallback: ScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { r ->
                    if(mCustomScanFilter(r.device))
                        scanEvent.onScanResult(r)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                mScanning = false
                scanEvent.onScanError()
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
    //endregion

    //region API 18+ SCANNER
    @Suppress("DEPRECATION")
    private class BleScanner18(context: Context, scanEvent: ScanEvent) : BleScanner()
    {
        override val mAdapter: BluetoothAdapter? =
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        private val mBleScanCallback: BluetoothAdapter.LeScanCallback =
                BluetoothAdapter.LeScanCallback { device, _, _ ->
                    device?.let { d ->
                        if(mCustomScanFilter(d))
                            scanEvent.onScanResult(d)
                    }
                }

        override fun abStartScan(): Boolean {
            return mAdapter?.startLeScan(mBleScanCallback) != null
        }

        override fun abStopScan(): Boolean {
            return mAdapter?.stopLeScan(mBleScanCallback) != null
        }
    }
    //endregion
}

