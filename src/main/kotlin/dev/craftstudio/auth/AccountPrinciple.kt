package dev.craftstudio.auth

import dev.craftstudio.db.account.Account
import io.ktor.server.auth.*

data class AccountPrinciple(val account: Account) : Principal
