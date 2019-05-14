package com.example.bluebeard.device_template

import java.util.*

class DeviceTemplateBuilder(private val name: String) {
    private val services: ArrayList<Service> = arrayListOf()

    fun addService(service: Service): DeviceTemplateBuilder {
        doCheckForDuplicateService(service)

        doCheckForDuplciateCharacteristicsInServices(service)

        services.add(service)
        return this
    }
    fun addService(name: String, uuid: String): ServiceBuilder {
        val service = Service(name, uuid)
        doCheckForDuplicateService(service)
        doCheckForDuplciateCharacteristicsInServices(service)
        return ServiceBuilder(name, uuid, this)
    }
    fun addService(name: String, uuid: UUID): ServiceBuilder {
        val service = Service(name, uuid)
        doCheckForDuplicateService(service)
        doCheckForDuplciateCharacteristicsInServices(service)
        return ServiceBuilder(name, uuid, this)
    }

    fun build(): DeviceTemplate = DeviceTemplate(name, services)

    private fun doCheckForDuplicateService(service: Service) {
        if (services.find { it.name == service.name || it.uuid == service.uuid } != null)
            throw DuplicatePropertyException(service)
    }
    private fun doCheckForDuplciateCharacteristicsInServices(service: Service) {
        for (char in service.getAllCharacteristics()) {
            for (svc in services) {
                if (svc.getAllCharacteristics().find { it.name == char.name || it.uuid == char.uuid } != null)
                    throw DuplicatePropertyException(service)
            }
        }
    }
}