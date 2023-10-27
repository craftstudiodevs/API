package dev.craftstudio.utils

import io.github.cdimascio.dotenv.dotenv
import kotlin.properties.ReadOnlyProperty

val dotenv = dotenv {
    ignoreIfMissing = true
}

object Environment {
    val HOST by env { it!! }
    val PORT by env { it!!.toInt() }

    val DATABASE_URL by env { it!! }
    val DATABASE_TYPE by env { it ?: "h2" }

    val DISCORD_CLIENT_ID by env { it!! }
    val DISCORD_CLIENT_SECRET by env { it!! }

    val STRIPE_API_KEY by env { it }
    val STRIPE_SECRET by env { it }
    val STRIPE_WH_SECRET by env { it }

    val TEST_TOKEN by env { it ?: "test" }
}

class env<T>(private val transformer: (String?) -> T) : ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T {
        return transformer(dotenv[property.name])
    }
}