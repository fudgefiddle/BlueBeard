package com.fudgefiddle.bluebeard.device_template

import java.util.*

sealed class BleProperties {

    class Characteristic (val name: String, val uuid: UUID) : BleProperties()

    class Service (val name: String,
                   val uuid: UUID,
                   val characteristics: List<Characteristic>) : BleProperties() {
        constructor(name: String, uuid: String) : this(name, UUID.fromString(uuid), listOf())
        constructor(name: String, uuid: UUID) : this(name, uuid, listOf())

        fun getCharacteristicFromName(name: String): Characteristic? = characteristics.find { char -> char.name == name }
        fun getCharacteristicFromUUID(uuid: String): Characteristic? = characteristics.find { char -> char.uuid == UUID.fromString(uuid) }
        fun getCharacteristicFromUUID(uuid: UUID): Characteristic? = getCharacteristicFromUUID(uuid.toString())
        fun getAllCharacteristics(): List<Characteristic> = characteristics
    }

    class DeviceTemplate(val name: String, private val services: ArrayList<Service>) : BleProperties() {
        fun getServiceFromName(name: String): Service? = services.find { svc -> svc.name == name }
        fun getServiceFromUUID(uuid: String): Service? = services.find { svc -> svc.uuid == UUID.fromString(uuid) }
        fun getServiceFromUUID(uuid: UUID): Service? = services.find { svc -> svc.uuid == uuid }
        fun getAllServices(): List<Service> = services

        fun getCharacteristicFromName(name: String): Characteristic? {
            for (service in services) {
                val char = service.getCharacteristicFromName(name)
                if (char != null)
                    return char
            }
            return null
        }
        fun getCharacteristicFromUUID(uuid: String): Characteristic? {
            for (service in services) {
                val char = service.getCharacteristicFromUUID(uuid)
                if (char != null)
                    return char
            }
            return null
        }
        fun getCharacteristicFromUUID(uuid: UUID): Characteristic? {
            for (service in services) {
                val char = service.getCharacteristicFromUUID(uuid)
                if (char != null)
                    return char
            }
            return null
        }
        fun getAllCharacteristics(): List<Characteristic> {
            val result: ArrayList<Characteristic> = arrayListOf()
            for (service in services)
                result.addAll(service.getAllCharacteristics())
            return result
        }
    }
}