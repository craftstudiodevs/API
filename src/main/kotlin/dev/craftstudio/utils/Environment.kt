package dev.craftstudio.utils

import io.github.cdimascio.dotenv.dotenv

val dotenv = dotenv {
    ignoreIfMissing = true
}

object Environment {
    val HOST = dotenv["HOST"]!!
    val PORT = dotenv["PORT"]!!.toInt()

    val DATABASE_URL = dotenv["DATABASE_URL"]!!
    val DATABASE_TYPE = dotenv["DATABASE_TYPE"]!!

    val DISCORD_CLIENT_ID = dotenv["DISCORD_CLIENT_ID"]!!
    val DISCORD_CLIENT_SECRET = dotenv["DISCORD_CLIENT_SECRET"]!!
}