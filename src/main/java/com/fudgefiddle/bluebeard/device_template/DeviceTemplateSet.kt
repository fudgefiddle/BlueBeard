package com.fudgefiddle.bluebeard.device_template


class DeviceTemplateSet{

    private val mSet = mutableSetOf<DeviceTemplate>()

    fun add(template: DeviceTemplate): Boolean{
        return mSet.add(template)
    }

    fun getTemplateFromName(name: String): DeviceTemplate?{
        return mSet.find{ temp -> temp.name == name}
    }
}