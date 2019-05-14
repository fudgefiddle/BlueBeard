package com.example.bluebeard.device_template

class DuplicatePropertyException(val duplicate: Any) : Exception() {
    override val message: String?
        get() = when (duplicate) {
            is Characteristic -> {
                "Service already contains an instance of type Characteristic with name: ${duplicate.name} or UUID: ${duplicate.uuid}"
            }
            is Service -> {
                "DeviceTemplate already contains an instance of type Service with name: ${duplicate.name} or UUID: ${duplicate.uuid}"
            }
            is DeviceTemplate -> {
                "TemplateManager already contains an instance of type DeviceTemplate with name: ${duplicate.name}"
            }
            else -> {
                ""
            }
        }
}