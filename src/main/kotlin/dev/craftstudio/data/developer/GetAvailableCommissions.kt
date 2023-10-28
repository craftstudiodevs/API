package dev.craftstudio.data.developer

import dev.craftstudio.db.CommissionCategory
import kotlinx.serialization.Serializable

@Serializable
enum class CommissionSortFunction {
    DATE_CREATED,
    DATE_EXPIRY,
    FIXED_PRICE,
    HOURLY_PRICE,
    REPUTATION,
}

@Serializable
data class GetAvailableCommissionsResponse(
    val commissions: List<DeveloperCommissionPreview>
)

@Serializable
data class DeveloperCommissionPreview(
    val title: String,
    val summary: String,
    val commissionId: Int,
    val fixedPriceAmount: Int,
    val hourlyPriceAmount: Int,
    val category: CommissionCategory,
)