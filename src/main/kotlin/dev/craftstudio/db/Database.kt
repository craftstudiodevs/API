package dev.craftstudio.db

import dev.craftstudio.db.account.*
import dev.craftstudio.utils.Environment
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        val driverClassName = "org.h2.Driver"
        val jdbcURL = Environment.DATABASE_URL
        val database = Database.connect(jdbcURL, driverClassName)
        transaction(database) {
            SchemaUtils.create(Accounts, BuyerAccounts, DeveloperAccounts, Commissions, Bids)

            DeveloperSubscriptions.addDefaultSubscriptions()
            BuyerSubscriptions.addDefaultSubscriptions()
        }
    }
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

interface Access<T> {
    val id: Int

    suspend fun resolve(): T
}

class DatabaseAccess<T>(override val id: Int, val getter: suspend (Int) -> T) : Access<T> {
    private var value: T? = null

    override suspend fun resolve(): T {
        if (value == null) {
            value = dbQuery { getter(id) }
        }
        return value!!
    }
}

class StoredAccess<T>(override val id: Int, private val value: T) : Access<T> {
    override suspend fun resolve(): T = value
}

