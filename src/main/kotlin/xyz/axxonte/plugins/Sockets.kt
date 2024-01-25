package xyz.axxonte.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import xyz.axxonte.Connection
import java.time.Duration
import java.util.*

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())
        webSocket("/axx-tools") { // websocketSession

            val thisConnection = Connection(this) //Stock la connexion actuelle
            thisConnection.session.send("identify")

            try {
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue

                    // Identifier si la connexion viens d'un téléphone ou d'un ordinateur
                    // Le comportement change en fonction de l'emeteur
                    if (frame.readText() == "mobile") {
                        thisConnection.name = "mobile"
                        connections += thisConnection  // Ajoute la connection actuelle a toutes les connexions
                    } else if (frame.readText() == "computer") {
                        thisConnection.name = "computer"
                        connections += thisConnection  // Ajoute la connection actuelle a toutes les connexions
                    }
                    
                    connections.forEach {
                        it.session.send("Text to send") // Change the text
                    }
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                println("Removing $thisConnection")
                connections -= thisConnection
            }

        }
    }
}
