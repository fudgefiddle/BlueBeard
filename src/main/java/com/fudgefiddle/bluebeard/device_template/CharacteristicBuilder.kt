package com.fudgefiddle.bluebeard.device_template

import java.util.*

class CharacteristicBuilder(private val uuid: UUID) {
    private var name: String = ""

    private val descriptors = mutableSetOf<BleProperty.Descriptor>()

    fun addDescriptor(descriptor: BleProperty.Descriptor) = this.also { descriptors.add(descriptor) }

    fun setName(name: String) = this.also{ this.name = name }

    fun build() = BleProperty.Characteristic(uuid, name, descriptors)
}