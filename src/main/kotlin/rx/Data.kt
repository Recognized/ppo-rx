package rx

import kotlinx.serialization.Serializable
import org.litote.kmongo.Id
import org.litote.kmongo.newId

enum class Currency {
    EUR, USD, RUR;

    fun convert(usd: Double): Double {
        return when (this) {
            EUR -> usd * 0.8
            RUR -> usd * 74.0
            else -> usd
        }
    }
}

data class User(
    val currency: Currency,
    val _id: Id<Good> = newId()
)

data class Good(
    val name: String,
    val priceUsd: Double,
    val _id: Id<Good> = newId()
)

@Serializable
data class GoodDTO(
    val name: String,
    val localPrice: Double
)

@Serializable
data class InsertedId(
    val id: String
)

fun String.asCurrency(): Currency {
    return Currency.values().find { it.name == this } ?: error("Not a currency")
}
