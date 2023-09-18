package dev.craftstudio.db

import dev.craftstudio.db.DatabaseFactory.dbQuery
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

@Serializable
data class Account(
    override val accountId: Int,
    val type: AccountType,
    val discordId: String,
    val username: String,
    val email: String,
    val accessToken: String,
) : ResolveableAccount {
    override suspend fun resolve(): Account = this
}

@Serializable
enum class AccountType { Buyer, Developer }

interface ResolveableAccount {
    val accountId: Int

    suspend fun resolve(): Account
}

class DatabaseAccount(
    override val accountId: Int,
    private val accountsDAO: AccountsDAO,
) : ResolveableAccount {
    private var account: Account? = null

    override suspend fun resolve(): Account {
        if (account == null) {
            account = accountsDAO.read(accountId) ?: error("Account $accountId does not exist")
        }
        return account!!
    }
}

object Accounts : Table() {
    val id = integer("id").autoIncrement()
    val type = enumeration("type", AccountType::class)
    val discordId = varchar("discord_id", 20)
    val username = varchar("username", 32)
    val email = varchar("email", 320)
    val accessToken = varchar("access_token", 255)

    override val primaryKey = PrimaryKey(id)
}

interface AccountsDAO {
    // CRUD
    suspend fun create(type: AccountType, discordId: String, username: String, email: String): Account?
    suspend fun read(id: Int): Account?
    suspend fun update(id: Int, account: Account): Boolean
    suspend fun delete(id: Int): Boolean
    suspend fun all(): List<Account>

    // Token Authentication
    suspend fun readByAccessToken(accessToken: String): Account?

    // OAuth Flow
    suspend fun readByDiscordId(discordId: String): Account?
}

val accountsDAO: AccountsDAO = AccountsDAOImpl()

class AccountsDAOImpl : AccountsDAO {
    private fun resultRowToAccount(row: ResultRow) = Account(
        accountId = row[Accounts.id],
        type = row[Accounts.type],
        discordId = row[Accounts.discordId],
        username = row[Accounts.username],
        email = row[Accounts.email],
        accessToken = row[Accounts.accessToken],
    )

    override suspend fun all(): List<Account> = dbQuery {
        Accounts.selectAll().map(::resultRowToAccount)
    }

    override suspend fun read(id: Int): Account? = dbQuery {
        Accounts
            .select { Accounts.id eq id }
            .map { resultRowToAccount(it) }
            .singleOrNull()
    }

    override suspend fun create(type: AccountType, discordId: String, username: String, email: String): Account? = dbQuery {
        val insertStatement = Accounts.insert {
            it[Accounts.type] = type
            it[Accounts.discordId] = discordId
            it[Accounts.username] = username
            it[Accounts.email] = email
            it[Accounts.accessToken] = UUID.randomUUID().toString()
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToAccount)
    }

    override suspend fun update(id: Int, account: Account): Boolean = dbQuery {
        Accounts.update({ Accounts.id eq id }) {
            it[Accounts.type] = account.type
            it[Accounts.discordId] = account.discordId
            it[Accounts.username] = account.username
            it[Accounts.email] = account.email
        } > 0
    }

    override suspend fun delete(id: Int): Boolean = dbQuery {
        Accounts.deleteWhere { Accounts.id eq id } > 0
    }

    override suspend fun readByAccessToken(accessToken: String): Account? = dbQuery {
        Accounts
            .select { Accounts.accessToken eq accessToken }
            .map { resultRowToAccount(it) }
            .singleOrNull()
    }

    override suspend fun readByDiscordId(discordId: String): Account? = dbQuery {
        Accounts
            .select { Accounts.discordId eq discordId }
            .map { resultRowToAccount(it) }
            .singleOrNull()
    }
}

