package xyz.axxonte.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import xyz.axxonte.database.ComputerService
import xyz.axxonte.database.DatabaseName
import xyz.axxonte.database.UsersService

var timeout: Int = 0

fun Application.configureRouting(database: MutableMap<DatabaseName, Any>) {

    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        get("/testdb") {

            timeout += 1
            if (timeout == 1) {
                runBlocking {
                    val users = UsersService(database[DatabaseName.DATABASE] as Database).readAll()
                    var response = ""
                    users.forEach {
                        response += "${it.id} - ${it.name}\n"
                    }
                    call.respondText(response)
                }
            }
            Thread.sleep(2000)
            timeout = 0
        }

        get("/list") {
            timeout += 1
            if (timeout == 1) {
                var output = ""
                runBlocking {
                    val computers = ComputerService(database[DatabaseName.DATABASE] as Database).readAll()

                    for (c in computers) {
                        output += "${c.name} - ${c.token}\n"
                    }
                }
                call.respondText(output)
            }

            Thread.sleep(2000)
            timeout = 0
        }

        get("/clear") {
            timeout += 1
            if (timeout == 1) {
                runBlocking {
                    UsersService(database[DatabaseName.DATABASE] as Database).deleteAll()
                }
            }
            call.respondText("DataBase Cleared")
            Thread.sleep(2000)
            timeout = 0
        }

        /*get("/add/{name}") {
            timeout += 1
            if (timeout == 1) {
                runBlocking {
                    UsersService(database[DatabaseName.DATABASE] as Database).create(
                        ExposedUser(
                            call.parameters["name"] ?: "", null
                        )
                    )
                }
            }
        }*/
    }
}
