package dev.craftstudio.db.account

import dev.craftstudio.db.*
import dev.craftstudio.utils.dollars
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

object DeveloperAccounts : Table() {
    val id = reference("id", Accounts.id)

    val subscriptionType = reference("subscription_type", DeveloperSubscriptions.id)
    val remainingBids = integer("remaining_bids").default(0)
    val totalBids = integer("total_bids").default(0)
    val subscriptionId = varchar("stripe_subscription_id", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class DeveloperAccount(
    override val id: Int,
    val subscriptionType: DeveloperSubscriptionType,
    val remainingBids: Int,
    val totalBids: Int,
    val subscriptionId: String? = null,
) : Access<DeveloperAccount> {
    override suspend fun resolve(): DeveloperAccount = this
}

object DeveloperSubscriptions : Table() {
    val id = integer("id")

    val bidsPerMonth = integer("bids_per_month")
    val fixedOfferLimit = integer("fixed_offer_limit")
    val hourlyOfferLimit = integer("hourly_offer_limit")
    val price = integer("price")

    val name = varchar("name", 32)

    val stripeId = varchar("stripe_id", 255).nullable()

    override val primaryKey = PrimaryKey(id)

    fun addDefaultSubscriptions() {
        fun insertSubscription(id: Int, bidsPerMonth: Int, fixedOfferLimit: Int, hourlyOfferLimit: Int, price: Int, name: String, stripeId: String?) {
            DeveloperSubscriptions.insert {
                it[this.id] = id
                it[this.bidsPerMonth] = bidsPerMonth
                it[this.fixedOfferLimit] = fixedOfferLimit
                it[this.hourlyOfferLimit] = hourlyOfferLimit
                it[this.price] = price
                it[this.name] = name
                it[this.stripeId] = stripeId
            }
        }

        if (DeveloperSubscriptions.selectAll().empty()) {
            insertSubscription(id = 0, bidsPerMonth = 1,  fixedOfferLimit = 40.00.dollars,   hourlyOfferLimit = -1, price = 0.00.dollars,  name = "free", null)
            insertSubscription(id = 1, bidsPerMonth = 3,  fixedOfferLimit = 50.00.dollars,   hourlyOfferLimit = -1, price = 1.99.dollars,  name = "bronze", "prod_OsYRAaRkzeigNb")
            insertSubscription(id = 2, bidsPerMonth = 5,  fixedOfferLimit = 300.00.dollars,  hourlyOfferLimit = -1, price = 4.99.dollars,  name = "silver", "prod_OsYSs9CjSb9iTq")
            insertSubscription(id = 3, bidsPerMonth = 10, fixedOfferLimit = 1000.00.dollars, hourlyOfferLimit = -1, price = 8.49.dollars,  name = "gold", "prod_OsYS2jlDjmiEBs")
            insertSubscription(id = 4, bidsPerMonth = -1, fixedOfferLimit = -1,              hourlyOfferLimit = -1, price = 15.19.dollars, name = "unlimited", "prod_OsYS8cjWUKt2Jc")
        }
    }
}

@Serializable
data class DeveloperSubscriptionType(
    override val id: Int,
    val bidsPerMonth: Int,
    val fixedOfferLimit: Int,
    val hourlyOfferLimit: Int,
    val price: Int,
    override val name: String,
    override val stripeId: String? = null,
): SubscriptionType {
    companion object {
        private var _allTypes: Map<Int, DeveloperSubscriptionType>? = null

        val allTypes: Map<Int, DeveloperSubscriptionType>
            get() {
                return _allTypes ?: runBlocking {
                    refreshTypes()
                    _allTypes!!
                }
            }

        val FREE_TIER: DeveloperSubscriptionType
            get() = allTypes[0]!!

        fun getSubscriptionTypeFromStripeId(stripeId: String): DeveloperSubscriptionType? =
            allTypes.values.firstOrNull { it.stripeId == stripeId }

        private suspend fun refreshTypes() {
            _allTypes = dbQuery {
                DeveloperSubscriptions.selectAll().map {
                    DeveloperSubscriptionType(
                        id = it[DeveloperSubscriptions.id],
                        bidsPerMonth = it[DeveloperSubscriptions.bidsPerMonth],
                        fixedOfferLimit = it[DeveloperSubscriptions.fixedOfferLimit],
                        hourlyOfferLimit = it[DeveloperSubscriptions.hourlyOfferLimit],
                        price = it[DeveloperSubscriptions.price],
                        name = it[DeveloperSubscriptions.name],
                    )
                }.associateBy { it.id }
            }
        }
    }
}

interface DeveloperAccountDAO {
    suspend fun create(accountId: Int): DeveloperAccount
    suspend fun read(accountId: Int): DeveloperAccount?
    suspend fun delete(accountId: Int): Boolean

    suspend fun updateSubscriptionType(accountId: Int, subscription: DeveloperSubscriptionType, subscriptionId: String?, refillBids: Boolean): Boolean
    suspend fun updateBidCount(accountId: Int, remainingBids: Int, totalBids: Int): Boolean
}

val developerAccountDAO: DeveloperAccountDAO = DeveloperAccountDAOImpl

private object DeveloperAccountDAOImpl : DeveloperAccountDAO {
    override suspend fun create(accountId: Int): DeveloperAccount = dbQuery {
        val insertStatement = DeveloperAccounts.insert {
            it[this.id] = accountId
            it[this.subscriptionType] = DeveloperSubscriptionType.FREE_TIER.id
        }
        val developerAccount = insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToDeveloperAccount)
            ?: throw IllegalStateException("Failed to create developer account")
        Accounts.update({ Accounts.id eq accountId }) {
            it[Accounts.developerData] = true
        }
        developerAccount
    }

    override suspend fun read(accountId: Int): DeveloperAccount? = dbQuery {
        DeveloperAccounts
            .select { DeveloperAccounts.id eq accountId }
            .singleOrNull()
            ?.let(::resultRowToDeveloperAccount)
    }

    override suspend fun delete(accountId: Int): Boolean = dbQuery {
        val deleted = DeveloperAccounts.deleteWhere { DeveloperAccounts.id eq accountId } > 0
        if (deleted) {
            return@dbQuery Accounts.update({ Accounts.id eq accountId }) {
                it[developerData] = false
            } > 0
        }
        false
    }

    override suspend fun updateSubscriptionType(
        accountId: Int,
        subscription: DeveloperSubscriptionType,
        subscriptionId: String?,
        refillBids: Boolean
    ): Boolean = dbQuery {
        DeveloperAccounts.update({ DeveloperAccounts.id eq accountId }) {
            it[DeveloperAccounts.subscriptionType] = subscription.id
            it[DeveloperAccounts.subscriptionId] = subscriptionId
            if (refillBids) {
                it[DeveloperAccounts.remainingBids] = subscription.bidsPerMonth
            }
        } > 0
    }

    override suspend fun updateBidCount(accountId: Int, remainingBids: Int, totalBids: Int): Boolean = dbQuery {
        DeveloperAccounts.update({ DeveloperAccounts.id eq accountId }) {
            it[DeveloperAccounts.remainingBids] = remainingBids
            it[DeveloperAccounts.totalBids] = totalBids
        } > 0
    }

    private fun resultRowToDeveloperAccount(row: ResultRow) = DeveloperAccount(
        id = row[DeveloperAccounts.id],
        subscriptionType = DeveloperSubscriptionType.allTypes[row[DeveloperAccounts.subscriptionType]]!!,
        remainingBids = row[DeveloperAccounts.remainingBids],
        totalBids = row[DeveloperAccounts.totalBids],
        subscriptionId = row[DeveloperAccounts.subscriptionId],
    )
}
