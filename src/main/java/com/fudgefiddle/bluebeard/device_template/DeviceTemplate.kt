package com.fudgefiddle.bluebeard.device_template

import java.util.*

data class DeviceTemplate(val name: String, val services: Set<BleProperty.Service> = setOf()){

    fun getServiceFromName(name: String): BleProperty.Service?{
        return services.find{ svc -> svc.name == name }
    }

    fun getServiceFromUUID(uuid: UUID): BleProperty.Service?{
        return services.find{ svc -> svc.uuid == uuid }
    }

    fun getCharacteristicFromName(name: String): BleProperty.Characteristic?{
        services.forEach{ svc ->
            svc.getCharacteristicFromName(name)?.let{
                return it
            }
        }
        return null
    }
    fun getCharacteristicFromUUID(uuid: UUID): BleProperty.Characteristic?{
        services.forEach{ svc ->
            svc.getCharacteristicFromUUID(uuid)?.let{
                return it
            }
        }
        return null
    }

    fun getDescriptorFromName(name: String): BleProperty.Descriptor?{
        services.forEach{ svc ->
            svc.characteristics.forEach{char ->
                char.getDescriptorFromName(name)?.let{
                    return it
                }
            }
        }
        return null
    }

    fun getDescriptorFromUUID(uuid: UUID): BleProperty.Descriptor?{
        services.forEach{ svc ->
            svc.characteristics.forEach{char ->
                char.getDescriptorFromUUID(uuid)?.let{
                    return it
                }
            }
        }
        return null
    }
}