package dev.craftstudio.db.account

import dev.craftstudio.db.*
import dev.craftstudio.utils.dollars
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

object DeveloperAccounts : Table() {
    val id = reference("id", Accounts.id)

    val subscriptionType = reference("subscription_type", DeveloperSubscriptions.id)
    val remainingBids = integer("remaining_bids").default(0)
    val totalBids = integer("total_bids").default(0)

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class DeveloperAccount(
    override val id: Int,
    val subscriptionType: DeveloperSubscriptionType,
    val remainingBids: Int,
    val totalBids: Int,
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

    override val primaryKey = PrimaryKey(id)

    fun addDefaultSubscriptions() {
        fun insertSubscription(id: Int, bidsPerMonth: Int, fixedOfferLimit: Int, hourlyOfferLimit: Int, price: Int, name: String) {
            DeveloperSubscriptions.insert {
                it[this.id] = id
                it[this.bidsPerMonth] = bidsPerMonth
                it[this.fixedOfferLimit] = fixedOfferLimit
                it[this.hourlyOfferLimit] = hourlyOfferLimit
                it[this.price] = price
                it[this.name] = name
            }
        }

        if (DeveloperSubscriptions.selectAll().empty()) {
            insertSubscription(id = 0, bidsPerMonth = 1,  fixedOfferLimit = 40.00.dollars,   hourlyOfferLimit = -1, price = 0.00.dollars,  name = "free")
            insertSubscription(id = 1, bidsPerMonth = 3,  fixedOfferLimit = 50.00.dollars,   hourlyOfferLimit = -1, price = 1.99.dollars,  name = "bronze")
            insertSubscription(id = 2, bidsPerMonth = 5,  fixedOfferLimit = 300.00.dollars,  hourlyOfferLimit = -1, price = 4.99.dollars,  name = "silver")
            insertSubscription(id = 3, bidsPerMonth = 10, fixedOfferLimit = 1000.00.dollars, hourlyOfferLimit = -1, price = 8.49.dollars,  name = "gold")
            insertSubscription(id = 4, bidsPerMonth = -1, fixedOfferLimit = -1,              hourlyOfferLimit = -1, price = 15.19.dollars, name = "unlimited")
        }
    }
}

@Serializable
data class DeveloperSubscriptionType(
    val id: Int,
    val bidsPerMonth: Int,
    val fixedOfferLimit: Int,
    val hourlyOfferLimit: Int,
    val price: Int,
    val name: String,
) {
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
