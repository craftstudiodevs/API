package dev.craftstudio.data.developer

import kotlinx.serialization.Serializable

@Serializable
data class SubmitBidRequest(
    val fixedBidAmount: Int,
    val hourlyBidAmount: Int,
    val testimony: String? = null,
)

@Serializable
data class SubmitBidResponse(
    val success: Boolean,
    val bidId: Int,
)