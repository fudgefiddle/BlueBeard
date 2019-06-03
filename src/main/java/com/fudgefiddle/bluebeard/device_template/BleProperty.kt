package com.fudgefiddle.bluebeard.device_template

import java.util.*

sealed class BleProperty {
    abstract val uuid: UUID
    abstract val name: String

    data class Descriptor(override val uuid: UUID, override val name: String = "") : BleProperty()

    data class Characteristic(override val uuid: UUID, override val name: String = "", val descriptors: Set<Descriptor> = setOf()) : BleProperty(){
        fun getDescriptorFromName(name: String): Descriptor?{
            return descriptors.find{ desc -> desc.name == name }
        }

        fun getDescriptorFromUUID(uuid: UUID): Descriptor?{
            return descriptors.find{ desc -> desc.uuid == uuid }
        }
    }


    data class Service(override val uuid: UUID, override val name: String = "", val characteristics: Set<Characteristic> = setOf()) : BleProperty(){
        fun getCharacteristicFromName(name: String): Characteristic?{
            return characteristics.find{ char -> char.name == name }
        }
        fun getCharacteristicFromUUID(uuid: UUID): Characteristic?{
            return characteristics.find{ char -> char.uuid == uuid }
        }
    }
}
