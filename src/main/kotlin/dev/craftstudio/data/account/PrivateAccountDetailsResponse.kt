package dev.craftstudio.data.account

import kotlinx.serialization.Serializable

@Serializable
data class PrivateAccountDetailsResponse(
    val id: Int,
    val discordId: String,
    val username: String,
    val email: String,
    val buyerAccount: BuyerAccountDetails? = null,
    val developerAccount: DeveloperAccountDetails? = null,
)

@Serializable
data class BuyerAccountDetails(
    val subscriptionType: String,
    val remainingCommissions: Int,
    val totalCommissions: Int,
)

@Serializable
data class DeveloperAccountDetails(
    val subscriptionType: String,
    val remainingBids: Int,
    val totalBids: Int,
)