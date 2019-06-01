package com.fudgefiddle.bluebeard.device_template

data class DeviceTemplate(val name: String, val services: Set<BleProperty.Service> = setOf())