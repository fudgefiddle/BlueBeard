package com.fudgefiddle.bluebeard.device_template


class DeviceTemplateBuilder(private val name: String) {
    private val services = mutableSetOf<BleProperty.Service>()

    fun addService(service: BleProperty.Service) = this.also { services.add(service) }

    fun build(): DeviceTemplate = DeviceTemplate(name, services)
}