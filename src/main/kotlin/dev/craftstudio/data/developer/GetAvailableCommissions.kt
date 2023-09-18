package dev.craftstudio.data.developer

import kotlinx.serialization.Serializable

@Serializable
data class GetAvailableCommissionsRequest(
    val page: Int = 1,
    val pageSize: Int = 20,
    val searchQuery: String = "",
    val sortFunction: CommissionSortFunction = CommissionSortFunction.DATE_CREATED,
    val inverseSortFunction: Boolean = false,
)

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
)