package dev.craftstudio.db.account

import dev.craftstudio.db.*
import dev.craftstudio.utils.dollars
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

object BuyerAccounts : Table() {
    val id = reference("id", Accounts.id)

    val subscriptionType = reference("subscription_type", BuyerSubscriptions.id)
    val remainingCommissions = integer("remaining_commissions").default(0)
    val totalCommissions = integer("total_commissions").default(0)

    val subscriptionId = varchar("stripe_subscription_id", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class BuyerAccount(
    override val id: Int,
    val subscriptionType: BuyerSubscriptionType,
    val remainingCommissions: Int,
    val totalCommissions: Int,
    val subscriptionId: String? = null,
) : Access<BuyerAccount> {
    override suspend fun resolve(): BuyerAccount = this
}

object BuyerSubscriptions : Table() {
    val id = integer("id")

    val commissionsPerMonth = integer("commissions_per_month")
    val maxFixedOffer = integer("max_fixed_offer")
    val maxHourlyOffer = integer("max_hourly_offer")
    val price = integer("price")

    val name = varchar("name", 32)
    val stripeId = varchar("stripe_id", 255).nullable()

    override val primaryKey = PrimaryKey(id)

    fun addDefaultSubscriptions() {
        fun insertSubscription(id: Int, commissionsPerMonth: Int, maxFixedOffer: Int, maxHourlyOffer: Int, price: Int, name: String, stripeId: String?) {
            BuyerSubscriptions.insert {
                it[this.id] = id
                it[this.commissionsPerMonth] = commissionsPerMonth
                it[this.maxFixedOffer] = maxFixedOffer
                it[this.maxHourlyOffer] = maxHourlyOffer
                it[this.price] = price
                it[this.name] = name
                it[this.stripeId] = stripeId
            }
        }

        if (BuyerSubscriptions.selectAll().empty()) {
            insertSubscription(id = 0, commissionsPerMonth = 1,  maxFixedOffer = 50.00.dollars,   maxHourlyOffer = -1, price = 0.00.dollars,  name = "free",      null)
            insertSubscription(id = 1, commissionsPerMonth = 3,  maxFixedOffer = 50.00.dollars,   maxHourlyOffer = -1, price = 3.49.dollars,  name = "bronze",    "prod_OsYQDuiLL6Ed54")
            insertSubscription(id = 2, commissionsPerMonth = 3,  maxFixedOffer = 200.00.dollars,  maxHourlyOffer = -1, price = 6.49.dollars,  name = "silver",    "prod_OsYQdvYeJFQjoo")
            insertSubscription(id = 3, commissionsPerMonth = 10, maxFixedOffer = 1000.00.dollars, maxHourlyOffer = -1, price = 12.99.dollars, name = "gold",      "prod_OsYR1IzbPdHZeR")
            insertSubscription(id = 4, commissionsPerMonth = -1, maxFixedOffer = -1,              maxHourlyOffer = -1, price = 19.99.dollars, name = "unlimited", "prod_OsYR4wlDGPDLmm")
        }
    }
}

@Serializable
data class BuyerSubscriptionType(
    override val id: Int,
    val commissionsPerMonth: Int,
    val maxFixedOffer: Int,
    val maxHourlyOffer: Int,
    val price: Int,
    override val name: String,
    override val stripeId: String?,
) : SubscriptionType {
    companion object {
        private var _allTypes: Map<Int, BuyerSubscriptionType>? = null

        val allTypes: Map<Int, BuyerSubscriptionType>
            get() {
                return _allTypes ?: runBlocking {
                    refreshTypes()
                    _allTypes!!
                }
            }

        val FREE_TIER: BuyerSubscriptionType
            get() = allTypes[0]!!

        fun getSubscriptionTypeFromStripeId(stripeId: String): BuyerSubscriptionType? =
            allTypes.values.find { it.stripeId == stripeId }

        private suspend fun refreshTypes() {
            _allTypes = dbQuery {
                BuyerSubscriptions.selectAll().map {
                    BuyerSubscriptionType(
                        id = it[BuyerSubscriptions.id],
                        commissionsPerMonth = it[BuyerSubscriptions.commissionsPerMonth],
                        maxFixedOffer = it[BuyerSubscriptions.maxFixedOffer],
                        maxHourlyOffer = it[BuyerSubscriptions.maxHourlyOffer],
                        price = it[BuyerSubscriptions.price],
                        name = it[BuyerSubscriptions.name],
                        stripeId = it[BuyerSubscriptions.stripeId],
                    )
                }.associateBy { it.id }
            }
        }
    }
}

interface BuyerAccountDAO {
    suspend fun create(accountId: Int): BuyerAccount
    suspend fun read(accountId: Int): BuyerAccount?
    suspend fun delete(accountId: Int): Boolean

    suspend fun updateSubscriptionType(accountId: Int, subscriptionType: BuyerSubscriptionType, subscriptionId: String?, refillCommissions: Boolean): Boolean
    suspend fun updateCommissionCount(accountId: Int, remainingCommissions: Int, totalCommissions: Int): Boolean
}

val buyerAccountDAO: BuyerAccountDAO = BuyerAccountDAOImpl

private object BuyerAccountDAOImpl : BuyerAccountDAO {
    override suspend fun create(accountId: Int): BuyerAccount = dbQuery {
        val insertStatement = BuyerAccounts.insert {
            it[BuyerAccounts.id] = accountId
            it[BuyerAccounts.subscriptionType] = BuyerSubscriptionType.FREE_TIER.id
            it[BuyerAccounts.remainingCommissions] = 0
            it[BuyerAccounts.totalCommissions] = 0
        }
        val buyerAccount = insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToBuyerAccount)
            ?: throw IllegalStateException("Failed to create buyer account")
        Accounts.update({ Accounts.id eq accountId }) {
            it[buyerData] = true
        }
        buyerAccount
    }

    override suspend fun read(accountId: Int): BuyerAccount? = dbQuery {
        BuyerAccounts
            .select { BuyerAccounts.id eq accountId }
            .singleOrNull()
            ?.let(::resultRowToBuyerAccount)
    }

    override suspend fun delete(accountId: Int): Boolean = dbQuery {
        val deleted = BuyerAccounts.deleteWhere { BuyerAccounts.id eq accountId } > 0
        if (deleted) {
            return@dbQuery Accounts.update({ Accounts.id eq accountId }) {
                it[buyerData] = false
            } > 0
        }
        false
    }

    override suspend fun updateSubscriptionType(accountId: Int, subscriptionType: BuyerSubscriptionType, subscriptionId: String?, refillCommissions: Boolean): Boolean = dbQuery {
        BuyerAccounts.update({ BuyerAccounts.id eq accountId }) {
            it[BuyerAccounts.subscriptionType] = subscriptionType.id
            it[BuyerAccounts.subscriptionId] = subscriptionId
            if (refillCommissions) {
                it[BuyerAccounts.remainingCommissions] = subscriptionType.commissionsPerMonth
            }
        } > 0
    }

    override suspend fun updateCommissionCount(
        accountId: Int,
        remainingCommissions: Int,
        totalCommissions: Int
    ): Boolean = dbQuery {
        BuyerAccounts.update({ BuyerAccounts.id eq accountId }) {
            it[BuyerAccounts.remainingCommissions] = remainingCommissions
            it[BuyerAccounts.totalCommissions] = totalCommissions
        } > 0
    }

    private fun resultRowToBuyerAccount(row: ResultRow) = BuyerAccount(
        id = row[BuyerAccounts.id],
        subscriptionType = BuyerSubscriptionType.allTypes[row[BuyerAccounts.subscriptionType]]!!,
        remainingCommissions = row[BuyerAccounts.remainingCommissions],
        totalCommissions = row[BuyerAccounts.totalCommissions],
        subscriptionId = row[BuyerAccounts.subscriptionId],
    )
}
