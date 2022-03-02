package com.xwars.plugins

import com.google.gson.Gson
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
                    send(Gson().toJson(mapOf<Any, Any>("type" to "response", "success" to true, "game" to game)))
                    connections.filter { it.game == game && it.player == 0 }.forEach {
                        it.session.send(Gson().toJson(mapOf("type" to "remote", "action" to "join")))
                    }
                    for (frame in incoming) {
                        frame as?Frame.Text ?: continue
                        val received = Gson().fromJson(frame.readText(), Map::class.java)
                        var `return`: Map<Any, Any>
                        if (connections.any { it.game == game && it.player == 0 })
                        {
                            if (received["action"] == "turn")
                            {
                                if (thisConnection.turn)
                                {
                                    thisConnection.turn = false
                                    connections.filter { it.game == game && it.player == 0 }.forEach {
                                        it.turn = true
                                    }
                                }
                                else
                                {
                                    `return` = mapOf("type" to "response", "success" to false, "reason" to "Not your turn")
                                    send(Gson().toJson(`return`))
                                    continue
                                }
                            }

                            `return` = mapOf("type" to "response", "success" to true)
                            val send = mapOf("type" to "remote") + received

                            send(Gson().toJson(`return`))
                            connections.filter { it.game == game && it.player == 0 }.forEach {
                                it.session.send(Gson().toJson(send))
                            }
                        }
                        else
                        {
                            `return` = mapOf("type" to "response", "success" to false, "reason" to "No other player")
                            send(Gson().toJson(`return`))
                        }
                    }
                } catch (e: Exception) {
                    println(e.localizedMessage)
                } finally {
                    connections.filter { it.game == game && it.player == 0 }.forEach {
                        it.session.send(Gson().toJson(mapOf("type" to "remote", "action" to "leave")))
                    }
                    println("Player 1 disconnected from game $game")
                    connections -= thisConnection
                }
            }
            else send(Gson().toJson(mapOf<Any, Any>("type" to "response", "success" to false, "reason" to "Invalid game code")))
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
                send(Gson().toJson(mapOf<Any, Any>("type" to "response", "success" to true, "game" to game)))
                for (frame in incoming) {
                    frame as?Frame.Text ?: continue
                    val received = Gson().fromJson(frame.readText(), Map::class.java)
                    var `return`: Map<Any, Any>
                    if (connections.any { it.game == game && it.player == 1 })
                    {
                        if (received["action"] == "turn")
                        {
                            if (thisConnection.turn)
                            {
                                thisConnection.turn = false
                                connections.filter { it.game == game && it.player == 1 }.forEach {
                                    it.turn = true
                                }
                            }
                            else
                            {
                                `return` = mapOf("type" to "response", "success" to false, "reason" to "Not your turn")
                                send(Gson().toJson(`return`))
                                continue
                            }
                        }

                        `return` = mapOf("type" to "response", "success" to true)
                        val send = mapOf("type" to "remote") + received

                        send(Gson().toJson(`return`))
                        connections.filter { it.game == game && it.player == 1 }.forEach {
                            it.session.send(Gson().toJson(send))
                        }
                    }
                    else
                    {
                        `return` = mapOf("type" to "response", "success" to false, "reason" to "No other player")
                        send(Gson().toJson(`return`))
                    }
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                connections.filter { it.game == game && it.player == 1 }.forEach {
                    it.session.send(Gson().toJson(mapOf("type" to "remote", "action" to "leave")))
                }
                println("Player 0 disconnected from game $game")
                println("Game deleted: $game")
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
