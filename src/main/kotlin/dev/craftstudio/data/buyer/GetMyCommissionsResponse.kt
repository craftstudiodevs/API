package dev.craftstudio.data.buyer

import dev.craftstudio.data.AccountDetails
import dev.craftstudio.db.CommissionStatus
import kotlinx.serialization.Serializable

typealias GetMyCommissionsResponse = List<BuyerCommissionPreview>

@Serializable
data class BuyerCommissionPreview (
    val commissionId: Int,
    val title: String,
    val summary: String,
    val status: CommissionStatus,
    val developer: AccountDetails?,
)