package xyz.axxonte.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

data class ExposedUser(val name: String, val password: String, val id: Int?)
class UsersService(private val database: Database){

    object Users : Table() {
        val id: Column<Int> = integer("id").autoIncrement()
        val name: Column<String> = varchar("name", length = 20).uniqueIndex()
        val password: Column<String> = varchar("password", length = 60)

        override val primaryKey = PrimaryKey(id, name = "PK_User_ID")
    }

    init {
        transaction {
            SchemaUtils.create(Users)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun create(user: ExposedUser): Int = dbQuery {
        Users.insert {
            it[name] = user.name
            it[password] = user.password
        }[Users.id]
    }

    suspend fun read(id: Int): ExposedUser? {
        return dbQuery {
            Users.selectAll()
                .where { Users.id eq id }
                .map { ExposedUser(it[Users.name], it[Users.password], it[Users.id]) }
                .singleOrNull()
        }
    }

    suspend fun readAll() : List<ExposedUser> {
        return dbQuery {
            Users.selectAll()
                .map { ExposedUser(it[Users.name], it[Users.password], it[Users.id]) }
        }
    }

    suspend fun findPasswdByUsername(username: String): String? {
        return dbQuery {
            Users.selectAll()
                .where { Users.name eq username }
                .map { it[Users.password] }
                .singleOrNull()
        }
    }

    suspend fun update(id: Int, user: ExposedUser) {
        dbQuery {
            Users.update({ Users.id eq id }) {
                it[name] = user.name
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            Users.deleteWhere { Users.id.eq(id) }
        }
    }

    suspend fun deleteAll() {
        dbQuery {
            Users.deleteAll()
        }
    }

    suspend fun findUserByNameOrNull(input: String): ExposedUser? {
        return dbQuery {
            Users.selectAll()
                .where { Users.name eq input }
                .map { ExposedUser(it[Users.name], it[Users.password], it[Users.id]) }
                .singleOrNull()
        }
    }

    suspend fun findUserByIdOrNull(input: String): ExposedUser? {
        return dbQuery {
            Users.selectAll()
                .where { Users.name eq input }
                .map { ExposedUser(it[Users.name], it[Users.password], it[Users.id]) }
                .singleOrNull()
        }
    }
}