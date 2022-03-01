package com.xwars.plugins

import com.xwars.Connection
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration
import io.ktor.server.application.*
import io.ktor.server.routing.*
import java.util.*
import kotlin.collections.LinkedHashSet

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        val connections = Collections.synchronizedSet<Connection>(LinkedHashSet())
        webSocket("/xwars/game/{game}") {
            val game = call.parameters["game"]!!
            if (connections.any { it.game == game && it.player == 0 } && connections.none { it.game == game && it.player == 1 })
            {
                println("Player 1 connected to game $game")
                val thisConnection = Connection(this, 1, game, false)
                connections += thisConnection
                try {
                    send("You are connected!")
                    for (frame in incoming) {
                        frame as?Frame.Text ?: continue
                        val received = frame.readText()
                        if (thisConnection.turn)
                        {
                            thisConnection.turn = false
                            connections.filter { it.game == game && it.player == 0 }.forEach {
                                it.session.send(received)
                                it.turn = true
                            }
                        }
                        else send("Not your turn")
                    }
                } catch (e: Exception) {
                    println(e.localizedMessage)
                } finally {
                    println("Player 1 disconnected from game $game")
                    connections -= thisConnection
                }
            }
        }
        webSocket("/xwars/new") {
            var game: String
            do {
                game = createGameCode()
            } while (connections.any { it.game == game })
            println("New game created: $game")
            println("Player 0 connected to game $game")
            val thisConnection = Connection(this, 0, game, true)
            connections += thisConnection
            try {
                send("You are connected!")
                for (frame in incoming) {
                    frame as?Frame.Text ?: continue
                    val received = frame.readText()
                    if (thisConnection.turn)
                    {
                        thisConnection.turn = false
                        connections.filter { it.game == game && it.player == 1 }.forEach {
                            it.session.send(received)
                            it.turn = true
                        }
                    }
                    else send("Not your turn")
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                println("Player 0 disconnected from game $game")
                connections -= thisConnection
            }
        }
    }
}

fun createGameCode(): String {
    val chars = 'A'..'Z'
    return (1..4)
        .map { chars.random() }
        .joinToString("")
}