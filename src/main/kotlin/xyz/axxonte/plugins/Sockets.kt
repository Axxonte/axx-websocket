package xyz.axxonte.plugins

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import xyz.axxonte.Connection
import xyz.axxonte.database.DatabaseName
import xyz.axxonte.database.ExposedUser
import xyz.axxonte.database.UsersService

fun Application.configureSockets(databaseMap: MutableMap<DatabaseName, Any>, connections: MutableSet<Connection>) {
    install(WebSockets) {
//        pingPeriod = Duration.ofSeconds(15)
//        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = GsonWebsocketContentConverter()
    }
    routing {

        val logger = LoggerFactory.getLogger(this::class.java)
        webSocket("/loginComputer") { // Route pour la connexion d'un ordinateur au serveur
            //val thisConnection = Connection(this) //Connexion actuelle

            val selectorManager = SelectorManager(Dispatchers.IO)
            val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", 9002)

            println("Server listening on ${serverSocket.localAddress}")

            while (true) {
                val socket = serverSocket.accept()
                println("Client connected: ${socket}")
                launch {
                    val receiveChannel = socket.openReadChannel()
                    val sendChannel = socket.openWriteChannel(autoFlush = true)
                    sendChannel.writeStringUtf8("Name : \n")
                    try {
                        while (true) {
                            val name = receiveChannel.readUTF8Line()
                            sendChannel.writeStringUtf8("Hello $name\n")
                        }
                    } catch (e: Throwable) {
                        socket.close()
                    }
                }
            }

            /* try {
                var incomingDup : ReceiveChannel<Frame>? = null // Pour la boucle principale
                var incomingDupUsername : ReceiveChannel<Frame>? = null // Pour la boucle Username
                while (true) {
                    //thisConnection.session.timeoutMillis = 8223372036854775807
                    if (thisConnection.username == "" || thisConnection.token == "") {
                        if (incoming != incomingDup) {
                            for (frame in incoming) {
                                frame as? Frame.Text ?: continue
                                val frameText = frame.readText()

                                incomingDup = incoming  // Met a jour le Dupliqué pour eviter de tourner dans le vide

                                if (thisConnection.username.isNotBlank() && thisConnection.token.isNotBlank()) {  //Si la connexion est complete, ajoute cette derniere a la liste des connexions actives
                                    if (!connections.contains(thisConnection)) {
                                        connections += thisConnection
                                        thisConnection.connected = true
                                        thisConnection.session.send("Connected")
                                        return@webSocket
                                    } else {
                                        thisConnection.connected = true
                                        thisConnection.session.send("Connected")
                                        return@webSocket
                                    }
                                } else if (frameText.contains("*")) {

                                    var token: String
                                    var username: String

                                    frameText.split("*").let {
                                        token = it[0]
                                        username = it[1]
                                    }

                                    val usersService = databaseMap[DatabaseName.USER] as UsersService
                                    val computerService = databaseMap[DatabaseName.COMPUTER] as ComputerService

                                    if (thisConnection.username.isBlank()) {
                                        if (usersService.findUserByNameOrNull(username) == null) {
                                            var tries: Int = 0
                                            thisConnection.session.send("InvalidUsername")

                                            if (incoming != incomingDupUsername){
                                                for (frame in incoming) {
                                                    incomingDupUsername = incoming
                                                    frame as? Frame.Text ?: continue
                                                    val frameText = frame.readText()
                                                    val username = frameText.split("*")[0]
                                                    if (frameText != "null") logger.info("Debugger", "Income : $frameText")
                                                    println(usersService.findUserByNameOrNull(username))
                                                    if (usersService.findUserByNameOrNull(username) != null) {
                                                        thisConnection.username = frameText
                                                        thisConnection.session.send("ValidUsername")
                                                        break
                                                    } else {
                                                        tries += 1
                                                    }
                                                    if (tries == 3) {
                                                        thisConnection.username = ""
                                                        break
                                                    }
                                                }
                                                thisConnection.username = ""
                                            } else {
                                                thisConnection.username = username
                                            }
                                        }
                                    }

                                    if (thisConnection.token.isBlank()) {
                                        if (computerService.read(token) == null) {
                                            thisConnection.session.send("InvalidToken")
                                            thisConnection.token = ""
                                        } else {
                                            thisConnection.token = token
                                        }
                                    }

                                    logger.debug("CRASHTEST : FinalInfo : ${thisConnection.username} : ${thisConnection.token}")
                                } else {
                                    thisConnection.session.send("InvalidInfo")
                                }


                            }
                        }
                    }
                }


            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                connections -= thisConnection
                thisConnection.session.close()
            }*/                                 //OLD WAY

        }

        webSocket("loginUser") { //Route pour la connexion d'un utilisateur au serveur
            val thisConnection = Connection(this)
            for (frame in incoming) {
                frame as? Frame.Text ?: continue
                val frameText = frame.readText()
                if (thisConnection.username.isNotBlank() && thisConnection.password.isNotBlank()) {  //Si la connexion est complete, ajoute cette derniere a la liste des connexions actives
                    if (!connections.contains(thisConnection)) {
                        connections += thisConnection
                        thisConnection.connected = true
                        thisConnection.session.send("Connected")
                        return@webSocket
                    } else {
                        thisConnection.connected = true
                        thisConnection.session.send("Connected")
                        return@webSocket
                    }
                } else if (frameText.contains("*")) {
                    frameText.split("*").let {
                        val username = it[0]
                        val password = it[1]

                        val usersService = databaseMap[DatabaseName.USER] as UsersService

                        if (usersService.findUserByNameOrNull(username) == null) {
                            thisConnection.session.send("InvalidUsername")
                            thisConnection.username = ""
                        } else {
                            thisConnection.username = username
                            if (usersService.findPasswdByUsername(username) == password) {
                                thisConnection.password = password
                            } else {
                                thisConnection.session.send("InvalidPassword")
                                thisConnection.password = ""
                            }
                        }

                    }
                } else {
                    thisConnection.session.send("InvalidInfo")
                }
                thisConnection.session.close()
            }
        }

        webSocket("/listComputers") {
            val computerMList = mutableListOf<Device>()
            connections.forEach {
                computerMList.add(Device(it.connectionName))
            }
            println(computerMList.toList())
            sendSerialized(computerMList.toList())
        }

        webSocket("/listUsers") {
            val usersService = databaseMap[DatabaseName.USER] as UsersService

            val userMList = usersService.readAll().toList()

            sendSerialized(userMList)
        }

        webSocket("/poke") {    //Path uniquement utilisé pour transmettre la cible de la commande
            val thisConnection = Connection(this)
            for (frame in incoming) {
                frame as? Frame.Text ?: continue

                val incomingText: String = frame.readText()         //Split received message to get target and command
                val splitedText = incomingText.split(' ')

                val target = splitedText.get(0)     //Lecture de la cible de la commande
                var targetFound = false

                val commandTab = splitedText.subList(1, splitedText.size)       // Recuperation de la commande
                var command = ""
                commandTab.forEach {
                    command += "$it "
                }

                if (command == "") {                        // Verifier si la commande est vide
                    send("Command cannot be empty")
                } else {
                    connections.forEach {   //Recherche de la cible dans les connexions existantes
                        if (it.connectionName == target) {
                            it.session.send(command)     //Cible trouvée -> Envoi de la commande
                            println("Target Found")
                            targetFound = true
                        }
                    }
                }

                if (!targetFound) {
                    thisConnection.session.send("Target not found :/")      // Si la cible n'existe pas dans la liste des connexions
                } else {
                    targetFound = false
                }
            }
        }

        webSocket("/register") {
            try {
                var registered = false
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val frameText = frame.readText()
                    if (frameText.contains("*")) {
                        val username = frameText.split("*")[0]
                        val password = frameText.split("*")[1]

                        val usersService = databaseMap[DatabaseName.USER] as UsersService

                        if (!registered) {
                            if (usersService.findUserByNameOrNull(username) == null) { // Si le nom d'utilisateur n'existe pas
                                usersService.create(
                                    ExposedUser(
                                        name = username,
                                        password = password,
                                        id = null
                                    )
                                )
                                registered = true
                                this.send("Registered")
                                logger.debug("Debug : ", "Registered : username : $username, password : $password")
                            } else {
                                registered = true
                                this.send("Username already exists")
                            }
                        }
                    } else {
                        this.send("Invalid username/password format")
                    }
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            }
        }

        webSocket("/registerPost") {
            post {
                val received = call.receive<String>()
                println(received)
            }
        }

        webSocket("/loginMobile") {
            val thisConnection = Connection(this) //Connexion actuelle

            try {
                while (true) {

                    if (thisConnection.username == "" || thisConnection.password == "") {
                        for (frame in incoming) {
                            frame as? Frame.Text ?: continue
                            val frameText = frame.readText()
                            if (thisConnection.username.isNotBlank() && thisConnection.password.isNotBlank()) {  //Si la connexion est complete, ajoute cette derniere a la liste des connexions actives
                                if (!connections.contains(thisConnection)) {
                                    connections += thisConnection
                                    thisConnection.connected = true
                                    thisConnection.session.send("Connected")
                                    return@webSocket
                                }
                            } else if (frameText.contains("*")) {

                                var username: String
                                var password: String

                                frameText.split("*").let {
                                    username = it[0]
                                    password = it[1]
                                }

                                val usersService = databaseMap[DatabaseName.USER] as UsersService

                                if (thisConnection.username.isBlank()) {
                                    if (usersService.findUserByNameOrNull(username) == null) {
                                        thisConnection.session.send("InvalidUsername")
                                        thisConnection.username = ""
                                    } else {
                                        thisConnection.username = username
                                    }
                                }

                                if (thisConnection.password.isBlank()) {
                                    if (usersService.findPasswdByUsername(username) != password) {
                                        thisConnection.session.send("InvalidPassword")
                                        thisConnection.password = ""
                                    } else {
                                        thisConnection.password = password
                                    }
                                }

                                logger.debug("CRASHTEST : FinalInfo : ${thisConnection.username} : ${thisConnection.password}")
                            }


                        }
                    }
                }


            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                connections -= thisConnection
                thisConnection.session.close()
            }
        }
    }
}

fun checkUserDatabase(input: String, databaseMap: MutableMap<DatabaseName, Any>): ExposedUser? {
    val usersService = databaseMap[DatabaseName.USER] as UsersService
    val user: ExposedUser?
    runBlocking {
        user = usersService.findUserByNameOrNull(input = input)
        println("Coroutine : ${user?.name}")
    }
    println("Main Thread : ${user?.name}")
    return user
}
