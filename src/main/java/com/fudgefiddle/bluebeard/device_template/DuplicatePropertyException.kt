package com.fudgefiddle.bluebeard.device_template

import com.fudgefiddle.bluebeard.device_template.BleProperties.*

class DuplicatePropertyException(duplicate: BleProperties) : Exception() {
    override val message: String = when (duplicate) {
            is Characteristic -> "Service already contains an instance of type Characteristic with name: ${duplicate.name} or UUID: ${duplicate.uuid}"
            is Service -> "DeviceTemplate already contains an instance of type Service with name: ${duplicate.name} or UUID: ${duplicate.uuid}"
            is DeviceTemplate -> "DeviceTemplateList already contains an instance of type DeviceTemplate with name: ${duplicate.name}"
        }
}