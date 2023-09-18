package dev.craftstudio.data.requests

import kotlinx.serialization.Serializable

@Serializable
data class BidInfo(
    val bidId: Int,
    val fixedBidAmount: Int,
    val hourlyBidAmount: Int,
    val testimony: String? = null,
    val bidder: AccountDetails,
)
