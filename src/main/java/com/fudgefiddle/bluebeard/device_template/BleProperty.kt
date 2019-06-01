package com.fudgefiddle.bluebeard.device_template

import java.util.*

sealed class BleProperty {
    abstract val uuid: UUID
    abstract val name: String

    data class Descriptor(override val uuid: UUID, override val name: String = "") : BleProperty()
    data class Characteristic(override val uuid: UUID, override val name: String = "", val descriptors: Set<Descriptor> = setOf()) : BleProperty()
    data class Service(override val uuid: UUID, override val name: String = "", val characteristics: Set<Characteristic> = setOf()) : BleProperty()
}
