package xyz.axxonte

import io.ktor.websocket.*
import java.util.concurrent.atomic.AtomicInteger

class Connection(val session: DefaultWebSocketSession) {
    companion object {
        val aInt = AtomicInteger(0)
    }
    val connectionName = "user${aInt.getAndIncrement()}"
    var username : String = ""
    var token: String = ""
    var password: String = ""
    var connected: Boolean = false

}

enum class DeviceType {
    COMPUTER, MOBILE, DEFAULT
}