package dev.craftstudio.data.buyer

import dev.craftstudio.db.CommissionCategory
import kotlinx.serialization.Serializable

@Serializable
data class SubmitCommissionRequestData(
    val title: String,
    val summary: String,
    val requirements: String,
    val fixedPriceAmount: Int,
    val hourlyPriceAmount: Int,
    val expiryDays: Int,
    val minimumReputation: Int,
    val category: CommissionCategory,
)
