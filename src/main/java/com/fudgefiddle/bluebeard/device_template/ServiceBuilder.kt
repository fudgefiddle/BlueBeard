package com.fudgefiddle.bluebeard.device_template

import com.fudgefiddle.bluebeard.device_template.BleProperties.Service
import com.fudgefiddle.bluebeard.device_template.BleProperties.Characteristic
import java.util.*

class ServiceBuilder internal constructor(private val name: String, private val uuid: UUID, private val device: DeviceTemplateBuilder?) {
    internal constructor(name: String, uuid: String, device: DeviceTemplateBuilder?) : this(name, UUID.fromString(uuid), device)

    constructor(name: String, uuid: UUID) : this(name, uuid, null)
    constructor(name: String, uuid: String) : this(name, uuid, null)

    private val characteristics: ArrayList<Characteristic> = arrayListOf()

    fun addCharacteristic(characteristic: Characteristic): ServiceBuilder {
        checkForDuplicateCharacteristic(characteristic)
        characteristics.add(characteristic)
        return this
    }
    fun addCharacteristic(name: String, uuid: UUID): CharacteristicBuilder {
        val characteristic = Characteristic(name, uuid)
        checkForDuplicateCharacteristic(characteristic)
        return CharacteristicBuilder(name, uuid, this)
    }
    fun addCharacteristic(name: String, uuid: String): CharacteristicBuilder {
        val characteristic = Characteristic(name, uuid)
        checkForDuplicateCharacteristic(characteristic)
        return CharacteristicBuilder(name, uuid, this)
    }

    fun build(): Service {
        return Service(name, uuid, characteristics)
    }

    fun addToDevice(): DeviceTemplateBuilder {
        return device!!.addService(build())
    }

    private fun checkForDuplicateCharacteristic(characteristic: Characteristic) {
        characteristics.forEach { char ->
            if (char.name == characteristic.name || char.uuid == characteristic.uuid)
                throw DuplicatePropertyException(characteristic)
        }
    }
}