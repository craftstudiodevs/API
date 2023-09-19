package dev.craftstudio.db.account

import dev.craftstudio.db.*
import dev.craftstudio.utils.dollars
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*

object BuyerAccounts : Table() {
    val id = reference("id", Accounts.id)

    val subscriptionType = reference("subscription_type", BuyerSubscriptions.id)
    val remainingCommissions = integer("remaining_commissions").default(0)
    val totalCommissions = integer("total_commissions").default(0)

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class BuyerAccount(
    override val id: Int,
    val subscriptionType: BuyerSubscriptionType,
    val remainingCommissions: Int,
    val totalCommissions: Int,
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

    override val primaryKey = PrimaryKey(id)

    fun addDefaultSubscriptions() {
        fun insertSubscription(id: Int, commissionsPerMonth: Int, maxFixedOffer: Int, maxHourlyOffer: Int, price: Int, name: String) {
            BuyerSubscriptions.insert {
                it[this.id] = id
                it[this.commissionsPerMonth] = commissionsPerMonth
                it[this.maxFixedOffer] = maxFixedOffer
                it[this.maxHourlyOffer] = maxHourlyOffer
                it[this.price] = price
                it[this.name] = name
            }
        }

        if (BuyerSubscriptions.selectAll().empty()) {
            insertSubscription(id = 0, commissionsPerMonth = 1,  maxFixedOffer = 50.00.dollars,   maxHourlyOffer = -1, price = 0.00.dollars,  name = "free")
            insertSubscription(id = 1, commissionsPerMonth = 3,  maxFixedOffer = 50.00.dollars,   maxHourlyOffer = -1, price = 3.49.dollars,  name = "bronze")
            insertSubscription(id = 2, commissionsPerMonth = 3,  maxFixedOffer = 200.00.dollars,  maxHourlyOffer = -1, price = 6.49.dollars,  name = "silver")
            insertSubscription(id = 3, commissionsPerMonth = 10, maxFixedOffer = 1000.00.dollars, maxHourlyOffer = -1, price = 12.99.dollars, name = "gold")
            insertSubscription(id = 4, commissionsPerMonth = -1, maxFixedOffer = -1,              maxHourlyOffer = -1, price = 19.99.dollars, name = "unlimited")
        }
    }
}

@Serializable
data class BuyerSubscriptionType(
    val id: Int,
    val commissionsPerMonth: Int,
    val maxFixedOffer: Int,
    val maxHourlyOffer: Int,
    val price: Int,
    val name: String,
) {
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
                    )
                }.associateBy { it.id }
            }
        }
    }
}
