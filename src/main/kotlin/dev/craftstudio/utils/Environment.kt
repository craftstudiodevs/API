package dev.craftstudio.utils

import io.github.cdimascio.dotenv.dotenv

val dotenv = dotenv {
    ignoreIfMissing = true
}

object Environment {
    val HOST = dotenv["HOST"]!!
    val PORT = dotenv["PORT"]!!.toInt()

    val DATABASE_URL = dotenv["DATABASE_URL"]!!
    val DATABASE_TYPE = dotenv["DATABASE_TYPE"] ?: "h2"

    val DISCORD_CLIENT_ID = dotenv["DISCORD_CLIENT_ID"]!!
    val DISCORD_CLIENT_SECRET = dotenv["DISCORD_CLIENT_SECRET"]!!

    val SUBSCRIPTION_CHECK_FREQUENCY = dotenv["SUBSCRIPTION_CHECK_FREQUENCY"]?.toLong() ?: 60L

    val STRIPE_API_KEY = dotenv["STRIPE_API_KEY"]
    val STRIPE_SECRET = dotenv["STRIPE_SECRET"]
    val STRIPE_WH_SECRET = dotenv["STRIPE_WH_SECRET"] ?: STRIPE_SECRET

    val TEST_TOKEN = dotenv["TEST_TOKEN"] ?: "test"
}