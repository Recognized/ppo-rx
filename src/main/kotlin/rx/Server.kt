package rx

import com.mongodb.client.result.InsertOneResult
import com.mongodb.reactivestreams.client.MongoDatabase
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.list
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.types.ObjectId
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.toList
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.reactivestreams.getCollection
import org.reactivestreams.Publisher


fun main() {
    runBlocking {
        val db = getDb()
        embeddedServer(Netty, 8080) {
            configure(db)
        }.start(wait = true)
    }
}

suspend fun getDb(name: String = "Shop"): MongoDatabase {
    val client = KMongo.createClient("mongodb://localhost:27017")
    val db = client.getDatabase(name)
    val collections = db.listCollectionNames().toList()
    val wait = mutableListOf<Publisher<*>>()
    if ("user" !in collections) {
        wait += db.createCollection("user")
    }
    if ("good" !in collections) {
        wait += db.createCollection("good")
    }
    wait.forEach {
        it.awaitFirstOrNull()
    }
    return db
}

fun Application.configure(db: MongoDatabase) {
    install(CallLogging)

    routing {
        post("createUser") {
            val currency = call.request.queryParameters["currency"]!!.asCurrency()
            val user = User(currency)

            call.respondResult(user._id, db.getCollection<User>().insertOne(user).awaitSingle())
        }

        get("goods") {
            val userCurrency = call.request.queryParameters["id"]?.let {
                db.getUserCurrency(it)
            } ?: Currency.USD

            call.respond(
                stringify(
                    GoodDTO.serializer().list,
                    db.getCollection<Good>().find().toList().map {
                        GoodDTO(it.name, userCurrency.convert(it.priceUsd))
                    }
                )
            )
        }

        post("addGood") {
            val userId = call.request.queryParameters["id"]!!
            val name = call.request.queryParameters["name"]!!
            val price = call.request.queryParameters["price"]!!.toDouble()

            val usdPrice = db.getUserCurrency(userId).convert(price)
            val good = Good(name, usdPrice)

            call.respondResult(good._id, db.getCollection<Good>().insertOne(good).awaitSingle())
        }
    }
}

suspend fun MongoDatabase.getUserCurrency(userId: String): Currency {
    return getCollection<User>()
        .find(BsonDocument().append("_id", BsonObjectId(ObjectId(userId))))
        .awaitSingle()
        .currency
}

suspend fun ApplicationCall.respondResult(id: Id<*>, result: InsertOneResult) {
    if (result.wasAcknowledged()) {
        respond(
            HttpStatusCode.OK,
            stringify(InsertedId.serializer(), InsertedId(id.toString()))
        )
    } else {
        respond(HttpStatusCode.InternalServerError)
    }
}

fun <T> stringify(strategy: SerializationStrategy<T>, data: T): String {
    return Json(JsonConfiguration.Stable).stringify(strategy, data)
}