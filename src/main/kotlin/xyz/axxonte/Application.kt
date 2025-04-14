package xyz.axxonte

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import xyz.axxonte.database.*
import java.util.*

fun main() {

    val logger = LoggerFactory.getLogger(Application::class.java)

    val mobileList = mutableListOf<Socket>()  // Liste des mobiles connectés
    val computerList = mutableListOf<Socket>()  // Liste des ordinateurs connectés

    // Stockage des channels d'écriture pour la console interne
    val consoleWriteChannel = mutableMapOf<Socket, ByteWriteChannel>()

    //*******************************************************************************//
    //                      Interface de commande interne                            //
    //*******************************************************************************//
    Thread {
        while (true) {
            println("Commande Serveur : ")
            val cmd = readLine()
            if (cmd != null) {
                when (cmd.lowercase(Locale.getDefault()).split(' ')[0]) {
                    "exit", "stop" -> {
                        println("Fermeture des connexions ...")
                        for (socket in mobileList) {
                            try {
                                socket.close()
                            } catch (e: Exception) {
                                logger.info(e.localizedMessage)
                            }
                        }

                        for (socket in computerList) {
                            try {
                                socket.close()
                            } catch (e: Exception) {
                                logger.info(e.localizedMessage)
                            }
                        }

                        System.exit(0)
                    }

                    "users" -> {
                        println("Liste des utilisateurs : ")
                        val users = Manager().getDatabaseMap()[DatabaseName.USER] as UsersService
                        runBlocking {
                            users.readAll().forEach{
                                println("Nom : ${it.name} Password : ${it.password} Id : ${it.id}")
                            }
                        }

                    }

                    "clearusers" -> {
                        val users = Manager().getDatabaseMap()[DatabaseName.USER] as UsersService
                        runBlocking {
                            users.deleteAll()
                        }
                        println("Utilisateurs effacés")
                    }

                    "mobilelist" -> {
                        println("Liste des mobiles connectés : ")
                        mobileList.forEach{
                            println("Socket : ${it.remoteAddress} ConnectionType : ${it}")
                        }
                    }

                    "computerlist" -> {
                        println("Liste des ordinateurs connectés : ")
                        computerList.forEach{
                            println("Socket : ${it.remoteAddress} ConnectionType : ${it}")
                        }
                    }

                    "cmd" -> {
                        try {
                            val targetip = cmd.split(' ').get(1)
                            var target : Socket? = null
                            var sendChannel : ByteWriteChannel? = null

                            var exitcmd = false

                            for (socket in computerList) {
                                if (socket.remoteAddress.toString() == targetip) {
                                    target = socket
                                    sendChannel = consoleWriteChannel[socket]
                                }
                            }

                            if (target == null) {
                                throw Exception("Cible introuvable")
                            }

                            while (!exitcmd) {
                                println("Commande cible : ")
                                val remoteCmd = readLine()
                                if (remoteCmd != null) {
                                    if (cmd.lowercase(Locale.getDefault()) == "exit") {
                                        exitcmd = true
                                    } else {
                                        when (remoteCmd.lowercase(Locale.getDefault())) {
                                            "stop" -> {
                                                runBlocking {
                                                    sendChannel!!.writeStringUtf8("stop\n")
                                                }
                                            }

                                        }
                                    }
                                }
                            }



                        } catch (e: Exception) {
                            println(e.localizedMessage)
                        }
                    }



                    else -> {
                        println("Commande inconnue")
                    }
                }
            }
        }

    }.start()

    // Declaration des variables globales
    lateinit var serverSocket: ServerSocket
    lateinit var selectorManager: SelectorManager
    lateinit var socket: Socket

    var allSetup: Boolean

    runBlocking {
        selectorManager = SelectorManager(Dispatchers.IO)
        serverSocket = aSocket(selectorManager).tcp().bind("192.168.1.129", 9002)

        println("Server listening on ${serverSocket.localAddress}")



        while (true) {
            socket = serverSocket.accept()
            logger.info("WebSocket : Client ${socket.remoteAddress} connected")

            Thread {
                logger.debug("Thread started")
                lateinit var receiveChannel: ByteReadChannel
                lateinit var sendChannel: ByteWriteChannel
                lateinit var user: ExposedUser
                val localSocket = socket

                Thread {
                    runBlocking {
                        receiveChannel = localSocket.openReadChannel()
                        logger.debug("ReceivedChannel : {}", receiveChannel)
                        sendChannel = localSocket.openWriteChannel(autoFlush = true)
                        logger.debug("SendChannel : {}", sendChannel)
                        logger.debug("Sended Greetings")
                        allSetup = true

                        // Stockage du channel d'écriture
                        consoleWriteChannel.put(localSocket, sendChannel)

                        // Demande d'identification du client : COMPUTER ou MOBILE

                        sendChannel.writeStringUtf8("DeviceType\n")

                        val received = receiveChannel.readUTF8Line()
                        println(received)
                        if (received == "COMPUTER") {
                            computerList.add(localSocket)
                            sendChannel.writeStringUtf8("COMPUTER\n") // Acquittement de l'identification
                        } else if (received == "MOBILE") {
                            mobileList.add(localSocket)
                            sendChannel.writeStringUtf8("MOBILE\n") // Acquittement de l'identification
                        } else {
                            socket.close()
                            logger.debug("Socket closed : ${socket.remoteAddress}")
                        }


                        // Les taches secondaires du Socket : Envoi de commandes et gestion des deconnexions

//                        Thread {
//                            runBlocking {
//                                while (true) {
//                                    if (allSetup == true) {
//                                        println("Commande : ")
//                                        val input = readln() + "\n"
//                                        sendChannel.writeStringUtf8(input)
//                                    }
//                                }
//                            }
//                        }.start()

                        Thread {
                            runBlocking {  //Initialisation de l'envoyeur de commandes
                                while (true) {
                                    if (allSetup == true) {
                                        if (receiveChannel.readUTF8Line() == null) {

                                            // Suppression du socket de la liste des connexions

                                            for (cSocket in computerList) {
                                                if (cSocket.equals(localSocket)) {
                                                    computerList.remove(localSocket)
                                                }
                                            }

                                            for (mSocket in mobileList) {
                                                if (mSocket.equals(localSocket)) {
                                                    mobileList.remove(localSocket)
                                                }
                                            }

                                            localSocket.close()
                                            logger.debug("Socket closed : {}", socket.remoteAddress)

                                        } else if (sendChannel.isClosedForWrite) {
                                            sendChannel =
                                                localSocket.openWriteChannel(autoFlush = true)   // Correction du channel d'ecriture qui se ferme
                                        }
                                    }
                                }
                            }
                        }

                        // Fin des taches secondaires

                        try {
                            while (true) {
                                val command = receiveChannel.readUTF8Line()
                                println("Received from ${localSocket.remoteAddress}: $command")
                                if (command == null) {
                                    logger.debug("Disconnected connection : {}", localSocket.remoteAddress)
                                    if (mobileList.contains(localSocket)) {
                                        mobileList.remove(localSocket)
                                    } else if (computerList.contains(localSocket)) {
                                        computerList.remove(localSocket)
                                    }
                                    break
                                }
                                when (command.split(' ').get(0)) {
                                    "ping" -> {
                                        if (command.split(' ').size > 1) {

                                            var string =
                                                command.split(' ').subList(1, (command.split(' ').size)).toString()
                                            string = string.replace(", ", " ").replace("[", "").replace("]", "")

                                            sendChannel.writeStringUtf8("pong : $string\n")
                                            println(string)
                                            generateHashFromString(string)
                                        } else {
                                            sendChannel.writeStringUtf8("PONG\n")
                                        }

                                    }

                                    "mobileList" -> {
                                        sendChannel.writeStringUtf8(mobileList.toString() + "\n")
                                    }

                                    "computerList" -> {
                                        sendChannel.writeStringUtf8(computerList.toString() + "\n")
                                    }

                                    "login" -> {
                                        val username = command.split(' ').get(1)
                                        val password = command.split(' ').get(2)

                                        val usersService = Manager().getDatabaseMap()[DatabaseName.USER] as UsersService
                                        usersService.findUserByNameOrNull(username).let {
                                            if (it != null) {
                                                if (verifyHash(
                                                        passwordToVerify = password,
                                                        hashedString = it.password
                                                    )
                                                ) {
                                                    user =
                                                        ExposedUser(id = it.id, name = it.name, password = it.password)
                                                    sendChannel.writeStringUtf8("Logged in as $username\n")
                                                } else {
                                                    sendChannel.writeStringUtf8("Mot de passe incorrect\n")
                                                }
                                            } else {
                                                sendChannel.writeStringUtf8("Utilisateur inconnu\n")
                                            }
                                        }
                                    }

                                    "register" -> {
                                        val username = command.split(' ').get(1)
                                        val password = command.split(' ').get(2)

                                        val usersService = Manager().getDatabaseMap()[DatabaseName.USER] as UsersService
                                        if (usersService.findUserByNameOrNull(username) != null) {
                                            sendChannel.writeStringUtf8("Utilisateur déjà existant\n")
                                        } else {
                                            usersService.create(
                                                ExposedUser(
                                                    name = username,
                                                    password = generateHashFromString(password),
                                                    id = -1
                                                )
                                            )
                                            sendChannel.writeStringUtf8("Registered\n")
                                        }

                                        logger.debug("----- DB OUTPUT -----")
                                        logger.debug("Username : Password : Id")
                                        for (tUser in usersService.readAll()) {
                                            logger.debug(tUser.name + " : " + tUser.password + " : " + tUser.id)
                                        }
                                    }

                                    /* TODO : Quand la gestions des ordinateurs en dabase sera implémentée */
                                    /*"computerList" -> {

                                        var localComputerList: List<ExposedComputer?>
                                        runBlocking {
                                            localComputerList = getComputersFromDb(user.id!!)
                                        }

                                        for (computer in localComputerList) {
                                            if (computer == null) {
                                                localComputerList.drop(localComputerList.indexOf(computer))
                                            }
                                        }

                                        var computerListOutput = ""
                                        for (computer in localComputerList) {
                                            if (computer != null) {
                                                computerListOutput += computer.name + ", "
                                            }
                                        }

                                        logger.debug("")

                                        sendChannel.writeStringUtf8(computerListOutput + "\n")
                                        *//* TODO : Envoi de la liste des ordinateurs || Ajouter un serialisateur*//*
                                    }*/

                                    else -> {
                                        sendChannel.writeStringUtf8("Unknown command\n")
                                    }

                                }
                            }


                        } catch (e: Throwable) {
                            socket.close()
                        }

                    }
                }.start()


                Thread {
                    runBlocking {
                        while (true) {
                            if (receiveChannel.readUTF8Line() == null || receiveChannel.readUTF8Line() == "null") {
                                localSocket.close()
                                logger.debug("Socket closed : ${socket.remoteAddress}")
                            }
                        }
                    }
                }
            }.start()
        }
    }
}

suspend fun getComputersFromDb(ownerId: Int): List<ExposedComputer?> {
    val computerService = Manager().getDatabaseMap()[DatabaseName.COMPUTER] as ComputerService
    return computerService.readByOwner(ownerId)
}


fun generateHashFromString(input: String): String {
    val hashedString = BCrypt.withDefaults().hashToString(12, input.toCharArray())
    println(hashedString.length)
    return hashedString
}

fun verifyHash(passwordToVerify: String, hashedString: String): Boolean {
    val passwordToVerifyHashed = BCrypt.withDefaults().hashToString(12, passwordToVerify.toCharArray())
    return BCrypt.verifyer().verify(passwordToVerifyHashed.toCharArray(), hashedString).verified
}

enum class ConnectionType {
    COMPUTER, MOBILE
}
