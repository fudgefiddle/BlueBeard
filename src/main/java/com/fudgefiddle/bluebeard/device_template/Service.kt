package com.example.bluebeard.device_template

import java.util.*

class Service internal constructor(val name: String, val uuid: UUID, private val characteristics: List<Characteristic>) {
    constructor(name: String, uuid: String) : this(name, UUID.fromString(uuid), listOf())
    constructor(name: String, uuid: UUID) : this(name, uuid, listOf())

    fun getCharacteristicFromName(name: String): Characteristic? = characteristics.find { char -> char.name == name }
    fun getCharacteristicFromUUID(uuid: String): Characteristic? = characteristics.find { char -> char.uuid == UUID.fromString(uuid) }
    fun getCharacteristicFromUUID(uuid: UUID): Characteristic? = getCharacteristicFromUUID(uuid.toString())
    fun getAllCharacteristics(): List<Characteristic> = characteristics
}