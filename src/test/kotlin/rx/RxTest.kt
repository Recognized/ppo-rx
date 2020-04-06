package rx

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.list
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.junit.After
import org.junit.AfterClass
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class RxTest : CoroutineScope {
    override val coroutineContext: CoroutineContext = Job()
    private val db = runBlocking {
        getDb("ShopTest")
    }
    private val server = embeddedServer(Netty, port = 7999) {
        configure(db)
    }.also {
        launch {
            it.start(wait = true)
        }
    }
    private val host = "http://localhost:7999"

    @Test
    fun `test endpoints`() = apiTest {
        assertEquals(
            HttpStatusCode.OK,
            post("$host/createUser") {
                parameter("currency", "EUR")
            }
        )


    }

    @Test
    fun `test different currency view`() = apiTest {
        val id1 = post<String>("$host/createUser") {
            parameter("currency", "EUR")
        }.parse(InsertedId.serializer()).id

        val id2 = post<String>("$host/createUser") {
            parameter("currency", "RUR")
        }.parse(InsertedId.serializer()).id

        val id3 = post<String>("$host/createUser") {
            parameter("currency", "USD")
        }.parse(InsertedId.serializer()).id

        post<HttpStatusCode>("$host/addGood") {
            parameter("id", id3)
            parameter("name", "car")
            parameter("price", "1000.0")
        }

        val price1 = get<String>("$host/goods") {
            parameter("id", id1)
        }.parse(GoodDTO.serializer().list).first()

        val price2 = get<String>("$host/goods") {
            parameter("id", id2)
        }.parse(GoodDTO.serializer().list).first()

        println("First sees $price1 in EUR")
        println("Second sees $price2 in RUR")

        assertTrue {
            price2.localPrice > price1.localPrice * 60
        }
    }

    fun apiTest(closure: suspend HttpClient.() -> Unit) {
        runBlocking {
            HttpClient(CIO).apply {
                closure()
            }
        }
    }

    @After
    fun tearDown() {
        server.stop(1000, 3000)
    }
}

fun <T> String.parse(strategy: DeserializationStrategy<T>): T {
    return Json(JsonConfiguration.Stable).parse(strategy, this)
}