package dev.craftstudio.data.requests

import dev.craftstudio.db.Account
import kotlinx.serialization.Serializable

@Serializable
data class AccountDetails(
    val id: Int,
    val name: String,
    val profilePictureUrl: String,
) {
    constructor(buyer: Account) : this(
        id = buyer.accountId,
        name = buyer.username,
        profilePictureUrl = "https://cdn.craftstudio.dev/profile-pictures/${buyer.username}.png"
    )
}
