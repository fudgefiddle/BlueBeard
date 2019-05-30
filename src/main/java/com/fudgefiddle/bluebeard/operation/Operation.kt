package com.fudgefiddle.bluebeard.operation


sealed class Operation(val address: String, val timeout: Long, val delay: Long) {

    class Connect(address: String, timeout: Long = 0L, delay: Long = 0L) : Operation(address, timeout, delay)
    class Discover(address: String, timeout: Long = 0L, delay: Long = 0L) : Operation(address, timeout, delay)
    class Disconnect(address: String, timeout: Long = 0L, delay: Long = 0L) : Operation(address, timeout, delay)

    class Read(address: String, val uuid: String, timeout: Long = 0L, delay: Long = 0L) : Operation(address, timeout, delay)
    class Write(address: String, val uuid: String, val value: ByteArray, timeout: Long = 0L, delay: Long = 0L) : Operation(address, timeout, delay)
    class WriteDescriptor(address: String, val characteristicUuid: String, val descriptorUuid: String, val value: ByteArray, timeout: Long = 0L, delay: Long = 0L) : Operation(address, timeout, delay)
    class Notification(address: String, val uuid: String, val enable: Boolean, timeout: Long = 0L, delay: Long = 0L) : Operation(address, timeout, delay)
}





