package dev.craftstudio.data

import dev.craftstudio.db.account.Account
import kotlinx.serialization.Serializable

@Serializable
data class AccountDetails(
    val id: Int,
    val name: String,
    val profilePictureUrl: String,
) {
    constructor(buyer: Account) : this(
        id = buyer.id,
        name = buyer.username,
        profilePictureUrl = "https://cdn.craftstudio.dev/profile-pictures/${buyer.username}.png"
    )
}
