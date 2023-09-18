package dev.craftstudio.data.developer

import dev.craftstudio.data.AccountDetails
import dev.craftstudio.db.Commission
import kotlinx.serialization.Serializable

@Serializable
data class DeveloperCommissionResponse(
    val title: String,
    val summary: String,
    val requirements: String,
    val buyerFixedPricePref: Int,
    val buyerRatePricePref: Int,
    val expiryTime: Long,
    val commissionId: Int,
    val buyer: AccountDetails,
    val minimumReputation: Int,
)

suspend fun DeveloperCommissionResponse(commission: Commission) = DeveloperCommissionResponse(
    title = commission.title,
    summary = commission.summary,
    requirements = commission.requirements,
    buyerFixedPricePref = commission.fixedPriceAmount,
    buyerRatePricePref = commission.hourlyPriceAmount,
    expiryTime = commission.expiryTime,
    commissionId = commission.commissionId,
    buyer = AccountDetails(commission.owner.resolve()),
    minimumReputation = commission.minimumReputation,
)