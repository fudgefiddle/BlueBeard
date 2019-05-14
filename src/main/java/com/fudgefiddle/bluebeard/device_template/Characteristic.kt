package com.example.bluebeard.device_template

import java.util.*


class Characteristic internal constructor(val name: String,
                              val uuid: UUID,
                              internal val readConversion: ((ByteArray) -> Any?)? = null,
                              internal val writeConversion: ((Any) -> ByteArray)? = null) {
    constructor(name: String, uuid: String) : this(name, UUID.fromString(uuid))

}





