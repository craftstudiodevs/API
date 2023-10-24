package dev.craftstudio.db.account

import dev.craftstudio.db.*
import dev.craftstudio.utils.Environment
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
    val stripeCustomerId = varchar("stripe_customer_id", 64).nullable()

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
    val stripeCustomerId: String?,
) : Access<Account> {
    val isBuyer get() = buyerAccount != null
    val isDeveloper get() = developerAccount != null

    override suspend fun resolve(): Account = this
}

private val testAccount = Account(
    id = -1,
    discordId = "",
    username = "test-account",
    email = "example@example.com",
    accessToken = Environment.TEST_TOKEN,
    buyerAccount = BuyerAccount(
        id = -1,
        subscriptionType = BuyerSubscriptionType.FREE_TIER,
        remainingCommissions = 5,
        totalCommissions = 5,
    ),
    developerAccount = DeveloperAccount(
        id = -1,
        subscriptionType = DeveloperSubscriptionType.FREE_TIER,
        remainingBids = 5,
        totalBids = 5,
    ),
    stripeCustomerId = null,
)

interface AccountsDAO {
    suspend fun create(discordId: String, username: String, email: String): Account?
    suspend fun read(id: Int): Account?
    suspend fun update(id: Int, account: Account): Boolean
    suspend fun delete(id: Int): Boolean
    suspend fun all(): List<Account>

    suspend fun updateStripeCustomerId(id: Int, stripeCustomerId: String?): Boolean

    suspend fun readByAccessToken(accessToken: String): Account?
    suspend fun readByDiscordId(discordId: String): Account?
    suspend fun readByStripeCustomerId(stripeCustomerId: String): Account?
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
            it[stripeCustomerId] = account.stripeCustomerId
        } > 0
    }

    override suspend fun delete(id: Int): Boolean = dbQuery {
        Accounts.deleteWhere { Accounts.id eq id } > 0
    }

    override suspend fun updateStripeCustomerId(id: Int, stripeCustomerId: String?): Boolean = dbQuery {
        Accounts.update({ Accounts.id eq id }) {
            it[Accounts.stripeCustomerId] = stripeCustomerId
        } > 0
    }

    override suspend fun readByAccessToken(accessToken: String): Account? = dbQuery {
        if (accessToken == Environment.TEST_TOKEN) {
            return@dbQuery testAccount
        }

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

    override suspend fun readByStripeCustomerId(stripeCustomerId: String): Account? = dbQuery {
        Accounts
            .select { Accounts.stripeCustomerId eq stripeCustomerId }
            .map { resultRowToAccount(it) }
            .singleOrNull()
    }

    private fun resultRowToAccount(row: ResultRow) = Account(
        id = row[Accounts.id],
        discordId = row[Accounts.discordId],
        username = row[Accounts.username],
        email = row[Accounts.email],
        accessToken = row[Accounts.accessToken].toString(),
        buyerAccount = if(row[Accounts.buyerData]) DatabaseAccess(row[Accounts.id]) { buyerAccountDAO.read(it)!! } else null,
        developerAccount = if(row[Accounts.developerData]) DatabaseAccess(row[Accounts.id]) { developerAccountDAO.read(it)!! } else null,
        stripeCustomerId = row[Accounts.stripeCustomerId],
    )
}

interface SubscriptionType {
    val id: Int
    val name: String
    val stripeId: String?
}

