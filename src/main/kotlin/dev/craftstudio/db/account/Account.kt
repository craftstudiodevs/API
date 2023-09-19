package dev.craftstudio.db.account

import dev.craftstudio.db.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

object Accounts : Table() {
    val id = integer("id").autoIncrement()
    val discordId = varchar("discord_id", 20)
    val username = varchar("username", 32)
    val email = varchar("email", 320)
    val accessToken = uuid("access_token").autoGenerate()
    val buyerData = bool("buyer_data").default(false)
    val developerData = bool("developer_data").default(false)

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class Account(
    override val id: Int,
    val discordId: String,
    val username: String,
    val email: String,
    val accessToken: String,
    val buyerAccount: Access<BuyerAccount>? = null,
    val developerAccount: Access<DeveloperAccount>? = null,
) : Access<Account> {
    val isBuyer get() = buyerAccount != null
    val isDeveloper get() = developerAccount != null

    override suspend fun resolve(): Account = this
}

interface AccountsDAO {
    suspend fun create(discordId: String, username: String, email: String): Account?
    suspend fun read(id: Int): Account?
    suspend fun update(id: Int, account: Account): Boolean
    suspend fun delete(id: Int): Boolean
    suspend fun all(): List<Account>

    suspend fun addBuyerData(id: Int): BuyerAccount
    suspend fun readBuyerData(id: Int): BuyerAccount?

    suspend fun addDeveloperData(id: Int): DeveloperAccount
    suspend fun readDeveloperData(id: Int): DeveloperAccount?

    suspend fun readByAccessToken(accessToken: String): Account?
    suspend fun readByDiscordId(discordId: String): Account?
}

val accountsDAO: AccountsDAO = AccountsDAOImpl()

class AccountsDAOImpl : AccountsDAO {
    override suspend fun all(): List<Account> = dbQuery {
        Accounts.selectAll().map(::resultRowToAccount)
    }

    override suspend fun read(id: Int): Account? = dbQuery {
        Accounts
            .select { Accounts.id eq id }
            .map { resultRowToAccount(it) }
            .singleOrNull()
    }

    override suspend fun create(discordId: String, username: String, email: String): Account? = dbQuery {
        val insertStatement = Accounts.insert {
            it[Accounts.discordId] = discordId
            it[Accounts.username] = username
            it[Accounts.email] = email
            it[buyerData] = false
            it[developerData] = false
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToAccount)
    }

    override suspend fun update(id: Int, account: Account): Boolean = dbQuery {
        Accounts.update({ Accounts.id eq id }) {
            it[discordId] = account.discordId
            it[username] = account.username
            it[email] = account.email
            it[buyerData] = account.buyerAccount != null
            it[developerData] = account.developerAccount != null
        } > 0
    }

    override suspend fun delete(id: Int): Boolean = dbQuery {
        Accounts.deleteWhere { Accounts.id eq id } > 0
    }

    override suspend fun addBuyerData(id: Int): BuyerAccount = dbQuery {
        val insertStatement = BuyerAccounts.insert {
            it[BuyerAccounts.id] = id
            it[BuyerAccounts.subscriptionType] = BuyerSubscriptionType.FREE_TIER.id
            it[BuyerAccounts.remainingCommissions] = 0
            it[BuyerAccounts.totalCommissions] = 0
        }
        val buyerAccount = insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToBuyerAccount)
            ?: throw IllegalStateException("Failed to create buyer account")
        Accounts.update({ Accounts.id eq id }) {
            it[buyerData] = true
        }
        buyerAccount
    }

    override suspend fun readBuyerData(id: Int): BuyerAccount? = dbQuery {
        BuyerAccounts.select { BuyerAccounts.id eq id }.singleOrNull()?.let(::resultRowToBuyerAccount)
    }

    override suspend fun addDeveloperData(id: Int): DeveloperAccount = dbQuery {
        val insertStatement = DeveloperAccounts.insert {
            it[DeveloperAccounts.id] = id
            it[DeveloperAccounts.subscriptionType] = DeveloperSubscriptionType.FREE_TIER.id
            it[DeveloperAccounts.remainingBids] = 0
            it[DeveloperAccounts.totalBids] = 0
        }
        val developerAccount = insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToDeveloperAccount)
            ?: throw IllegalStateException("Failed to create developer account")
        Accounts.update({ Accounts.id eq id }) {
            it[developerData] = true
        }
        developerAccount
    }

    override suspend fun readDeveloperData(id: Int): DeveloperAccount? = dbQuery {
        DeveloperAccounts.select { DeveloperAccounts.id eq id }.singleOrNull()?.let(::resultRowToDeveloperAccount)
    }

    override suspend fun readByAccessToken(accessToken: String): Account? = dbQuery {
        Accounts
            .select { Accounts.accessToken eq UUID.fromString(accessToken) }
            .map { resultRowToAccount(it) }
            .singleOrNull()
    }

    override suspend fun readByDiscordId(discordId: String): Account? = dbQuery {
        Accounts
            .select { Accounts.discordId eq discordId }
            .map { resultRowToAccount(it) }
            .singleOrNull()
    }

    private fun resultRowToAccount(row: ResultRow) = Account(
        id = row[Accounts.id],
        discordId = row[Accounts.discordId],
        username = row[Accounts.username],
        email = row[Accounts.email],
        accessToken = row[Accounts.accessToken].toString(),
        buyerAccount = if(row[Accounts.buyerData]) DatabaseAccess(row[Accounts.id]) { readBuyerData(it)!! } else null,
        developerAccount = if(row[Accounts.developerData]) DatabaseAccess(row[Accounts.id]) { readDeveloperData(it)!! } else null,
    )

    private fun resultRowToBuyerAccount(row: ResultRow) = BuyerAccount(
        id = row[BuyerAccounts.id],
        subscriptionType = BuyerSubscriptionType.allTypes[row[BuyerAccounts.subscriptionType]]!!,
        remainingCommissions = row[BuyerAccounts.remainingCommissions],
        totalCommissions = row[BuyerAccounts.totalCommissions],
    )

    private fun resultRowToDeveloperAccount(row: ResultRow) = DeveloperAccount(
        id = row[DeveloperAccounts.id],
        subscriptionType = DeveloperSubscriptionType.allTypes[row[DeveloperAccounts.subscriptionType]]!!,
        remainingBids = row[DeveloperAccounts.remainingBids],
        totalBids = row[DeveloperAccounts.totalBids],
    )
}

