package com.example.bluebeard.device_template

import java.util.*

class DeviceTemplate(val name: String, private val services: ArrayList<Service>) {
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