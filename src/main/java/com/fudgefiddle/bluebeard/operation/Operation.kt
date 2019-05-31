package com.fudgefiddle.bluebeard.operation

import android.os.Parcel
import android.os.Parcelable


sealed class Operation(val address: String, internal val timeout: Long, internal val delay: Long) : Parcelable {


    class Connect(address: String, timeout: Long = 0L, delay: Long = 0L) : Operation(address, timeout, delay) {
        private constructor(pIn: Parcel) : this(pIn.readString()!!, pIn.readLong(), pIn.readLong())

        override fun writeToParcel(pOut: Parcel, flags: Int) = with(pOut) {
                writeString(address)
                writeLong(timeout)
                writeLong(delay)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Operation> {
            override fun createFromParcel(source: Parcel): Operation {
                return Connect(source)
            }

            override fun newArray(size: Int): Array<Operation> {
                return arrayOf()
            }
        }
    }
    class Discover(address: String, timeout: Long = 0L, delay: Long = 0L) : Operation(address, timeout, delay) {
        private constructor(pIn: Parcel) : this(pIn.readString()!!, pIn.readLong(), pIn.readLong())

        override fun writeToParcel(pOut: Parcel, flags: Int) = with(pOut) {
            writeString(address)
            writeLong(timeout)
            writeLong(delay)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Operation> {
            override fun createFromParcel(source: Parcel): Operation {
                return Discover(source)
            }

            override fun newArray(size: Int): Array<Operation> {
                return arrayOf()
            }

        }
    }
    class Disconnect(address: String, timeout: Long = 0L, delay: Long = 0L) : Operation(address, timeout, delay) {
        private constructor(pIn: Parcel) : this(pIn.readString()!!, pIn.readLong(), pIn.readLong())

        override fun writeToParcel(pOut: Parcel?, flags: Int) {
            pOut?.apply {
                writeString(address)
                writeLong(timeout)
                writeLong(delay)
            }
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Operation> {
            override fun createFromParcel(source: Parcel): Operation {
                return Disconnect(source)
            }

            override fun newArray(size: Int): Array<Operation> {
                return arrayOf()
            }

        }
    }
    class Read(address: String, val uuid: String, timeout: Long = 0L, delay: Long = 0L) :
        Operation(address, timeout, delay) {
        private constructor(
            pIn: Parcel
        ) : this(
            pIn.readString()!!,
            pIn.readString()!!,
            pIn.readLong(),
            pIn.readLong()
        )

        override fun writeToParcel(pOut: Parcel, flags: Int) = with(pOut) {
            writeString(address)
            writeString(uuid)
            writeLong(timeout)
            writeLong(delay)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Operation> {
            override fun createFromParcel(source: Parcel): Operation {
                return Read(source)
            }

            override fun newArray(size: Int): Array<Operation> {
                return arrayOf()
            }

        }
    }

    class Write(address: String, val uuid: String, val value: ByteArray, timeout: Long = 0L, delay: Long = 0L) :
        Operation(address, timeout, delay) {
        private constructor(pIn: Parcel, byteArray: ByteArray) : this(
            pIn.readString()!!,
            pIn.readString()!!,
            byteArray.apply(pIn::readByteArray),
            pIn.readLong(),
            pIn.readLong()
        )

        override fun writeToParcel(pOut: Parcel, flags: Int) = with(pOut) {
            writeString(address)
            writeString(uuid)
            writeByteArray(value)
            writeLong(timeout)
            writeLong(delay)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Operation> {
            override fun createFromParcel(source: Parcel): Operation {
                return Write(source, byteArrayOf())
            }

            override fun newArray(size: Int): Array<Operation> {
                return arrayOf()
            }

        }
    }
    class WriteDescriptor(
        address: String,
        val characteristicUuid: String,
        val descriptorUuid: String,
        val value: ByteArray,
        timeout: Long = 0L,
        delay: Long = 0L
    ) : Operation(address, timeout, delay) {
        private constructor(pIn: Parcel, byteArray: ByteArray) : this(
            pIn.readString()!!,
            pIn.readString()!!,
            pIn.readString()!!,
            byteArray.apply(pIn::readByteArray),
            pIn.readLong(),
            pIn.readLong()
        )

        override fun writeToParcel(pOut: Parcel?, flags: Int) {
            pOut?.apply {
                writeString(address)
                writeString(characteristicUuid)
                writeString(descriptorUuid)
                writeByteArray(value)
                writeLong(timeout)
                writeLong(delay)
            }
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Operation> {
            override fun createFromParcel(source: Parcel): Operation {
                return WriteDescriptor(source, byteArrayOf())
            }

            override fun newArray(size: Int): Array<Operation> {
                return arrayOf()
            }

        }
    }
    class Notification(address: String, val uuid: String, val enable: Boolean, timeout: Long = 0L, delay: Long = 0L) :
        Operation(address, timeout, delay){
        private constructor(pIn: Parcel, byteArray: ByteArray) : this(
            pIn.readString()!!,
            pIn.readString()!!,
            (pIn.readInt() == 1),
            pIn.readLong(),
            pIn.readLong()
        )

        override fun writeToParcel(pOut: Parcel, flags: Int) = with(pOut) {
            writeString(address)
            writeString(uuid)
            writeInt(if(enable) 1 else 0)
            writeLong(timeout)
            writeLong(delay)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Operation> {
            override fun createFromParcel(source: Parcel): Operation {
                return Notification(source, byteArrayOf())
            }

            override fun newArray(size: Int): Array<Operation> {
                return arrayOf()
            }

        }
    }

    companion object{

    }
}





