package dev.craftstudio.auth

import dev.craftstudio.db.Account
import io.ktor.server.auth.*

data class AccountPrinciple(val account: Account) : Principal
