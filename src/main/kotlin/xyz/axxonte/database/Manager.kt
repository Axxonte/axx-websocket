package xyz.axxonte.database

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection

class Manager {
    private var database: Database
    private var userDb : UsersService
    private var computerDb : ComputerService

    private var dbMap = mutableMapOf<DatabaseName, Any>()

    init {
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        database = Database.connect(
            "jdbc:sqlite:data/data.db",
            "org.sqlite.JDBC"
        )
        userDb = UsersService(database)
        computerDb = ComputerService(database)

        dbMap.set(DatabaseName.USER, userDb)
        dbMap.set(DatabaseName.COMPUTER, computerDb)
        dbMap.set(DatabaseName.DATABASE, database)
    }
    fun getDatabaseMap() = dbMap

    fun getUserById(id: Int) : ExposedUser? = runBlocking { userDb.read(id) }

    fun getComputerById(id: Int) : ExposedComputer? = runBlocking { computerDb.read(id) }
}

enum class DatabaseName {
    DATABASE, USER, COMPUTER
}