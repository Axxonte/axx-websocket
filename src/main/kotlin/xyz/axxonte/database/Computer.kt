package xyz.axxonte.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

data class ExposedComputer (val name: String, val token: String, val ownerId: Int)
class ComputerService(private val database: Database) {
    object Computer : Table() {
        val id: Column<Int> = integer("id").autoIncrement()
        val name: Column<String> = varchar("name", length = 12)
        val token: Column<String> = varchar("token", length = 20)
        val owner: Column<Int> = integer("owner")

        override val primaryKey = PrimaryKey(id, name = "PK_Computer_ID")
    }

    init {
        transaction {
            SchemaUtils.create(Computer)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun create(computer: ExposedComputer): Int = dbQuery {
        Computer.insert {
            it[name] = computer.name
            it[token] = computer.token
            it[owner] = computer.ownerId
        }[Computer.id]
    }

    suspend fun read(id: Int): ExposedComputer? {
        return dbQuery {
            Computer.selectAll()
                .where { Computer.id eq id }
                .map { ExposedComputer(it[Computer.name], it[Computer.token], it[Computer.owner]) }
                .singleOrNull()
        }
    }

    suspend fun readByOwner(id: Int): List<ExposedComputer?> {
        return dbQuery {
            Computer.selectAll()
                .where { Computer.owner eq id }
                .map { ExposedComputer(it[Computer.name], it[Computer.token], it[Computer.owner]) }
        }
    }

    suspend fun read(token: String) : ExposedComputer? {
        return dbQuery {
            Computer.selectAll()
                .where {Computer.token eq token}
                .map { ExposedComputer(it[Computer.name], it[Computer.token], it[Computer.owner]) }
                .singleOrNull()
        }
    }

    suspend fun readAll() : List<ExposedComputer> {
        return dbQuery {
            Computer.selectAll()
                .map { ExposedComputer(it[Computer.name], it[Computer.token], it[Computer.owner]) }
        }
    }

    suspend fun update(id: Int, computer: ExposedComputer) {
        dbQuery {
            Computer.update({ Computer.id eq id }) {
                it[name] = computer.name
                it[token] = computer.token
                it[owner] = computer.ownerId
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            Computer.deleteWhere { UsersService.Users.id.eq(id) }
        }
    }

    suspend fun deleteAll() {
        dbQuery {
            ComputerService.Computer.deleteAll()
        }
    }
}