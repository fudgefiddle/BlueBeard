package com.fudgefiddle.bluebeard.device_template

import java.util.*

class CharacteristicBuilder
    internal constructor(private val name: String, private val uuid: UUID, private val serviceBuilder: ServiceBuilder?) {
    internal constructor(name: String, uuid: String, serviceBuilder: ServiceBuilder?) : this(name, UUID.fromString(uuid), serviceBuilder)

    // Public Constructors
    constructor(name: String, uuid: UUID) : this(name, uuid, null)
    constructor(name: String, uuid: String) : this(name, uuid, null)

    // Read / Write Conversions
    private var readConversion: (ByteArray) -> Any? = { b -> b}
    private var writeConversion: (Any) -> ByteArray = { byteArrayOf() }

    // Setters
    fun setReadConversion(conversion: (ByteArray) -> Any?): CharacteristicBuilder {
        readConversion = conversion
        return this
    }
    fun setWriteConversion(conversion: (Any) -> ByteArray): CharacteristicBuilder {
        writeConversion = conversion
        return this
    }

    fun build(): BleProperties.Characteristic = BleProperties.Characteristic(name, uuid, readConversion, writeConversion)
    fun addToService(): ServiceBuilder = serviceBuilder!!.addCharacteristic(build())

}