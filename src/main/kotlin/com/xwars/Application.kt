package com.xwars

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.xwars.plugins.*

fun main() {
    embeddedServer(Netty, port = 80, host = "0.0.0.0") {
        configureRouting()
        configureSockets()
    }.start(wait = true)
}
