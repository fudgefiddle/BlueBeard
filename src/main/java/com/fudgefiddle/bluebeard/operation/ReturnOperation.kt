package com.fudgefiddle.bluebeard.operation

import android.os.Parcel
import android.os.Parcelable
import com.fudgefiddle.bluebeard.BLANK_UUID
import com.fudgefiddle.bluebeard.device_template.BleProperty.*
import java.util.*

class ReturnOperation(val address: String,
                      val characteristic: Characteristic = Characteristic(UUID.fromString(BLANK_UUID), ""),
                      val descriptor: Descriptor = Descriptor(UUID.fromString(BLANK_UUID), ""),
                      val value: ByteArray = byteArrayOf()) : Parcelable {

    override fun writeToParcel(pOut: Parcel, flags: Int) = with(pOut) {
        writeString(address)
        writeString(characteristic.name)
        writeString(characteristic.uuid.toString())
        writeString(descriptor.name)
        writeString(descriptor.uuid.toString())
        writeByteArray(value)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ReturnOperation> {
        override fun createFromParcel(source: Parcel): ReturnOperation = with(source) {
            return ReturnOperation(
                    readString()!!,
                    Characteristic(UUID.fromString(readString()!!),readString()!!),
                    Descriptor(UUID.fromString(readString()!!),readString()!!),
                    byteArrayOf().apply(::readByteArray)
            )
        }

        override fun newArray(size: Int): Array<ReturnOperation> {
            return arrayOf()
        }
    }
}