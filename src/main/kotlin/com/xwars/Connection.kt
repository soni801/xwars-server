package com.xwars

import io.ktor.websocket.*

class Connection(val session: DefaultWebSocketSession, val player: Int, val game: String)
