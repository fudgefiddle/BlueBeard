package com.fudgefiddle.bluebeard.device_template

import com.fudgefiddle.bluebeard.device_template.BleProperty.Characteristic
import com.fudgefiddle.bluebeard.device_template.BleProperty.Service
import java.util.*

class ServiceBuilder(private val uuid: UUID) {
    private var name: String = ""

    private val characteristics = mutableSetOf<Characteristic>()

    fun addCharacteristic(characteristic: Characteristic) = this.also { characteristics.add(characteristic) }

    fun setName(name: String) = this.also{ this.name = name }

    fun build(): Service = Service(uuid, name, characteristics)
}