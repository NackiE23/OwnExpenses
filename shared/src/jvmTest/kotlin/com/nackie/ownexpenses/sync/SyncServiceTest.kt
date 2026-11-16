package com.nackie.ownexpenses.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nackie.ownexpenses.data.MutableClock
import com.nackie.ownexpenses.data.createTestDatabase
import com.nackie.ownexpenses.data.insertCategory
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.repository.RoomTransactionRepository
import com.nackie.ownexpenses.domain.model.TransactionInput
import com.nackie.ownexpenses.domain.model.TransactionType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SyncServiceTest {
    private lateinit var database: AppDatabase
    private lateinit var transactions: RoomTransactionRepository
    private val clock = MutableClock(Instant.parse("2026-11-16T10:00:00Z"))
    private val directory = Files.createTempDirectory("sync-test").toFile().apply { deleteOnExit() }

    /** Bodies of the POST requests the server got, in order. */
    private val sent = mutableListOf<JsonObject>()
    private val authorization = mutableListOf<String?>()

    /** What the fake server does with the n-th request (1-based). */
    private var server: suspend (Int) -> HttpStatusCode = { HttpStatusCode.OK }

    private var food = 0L
    private var cafe = 0L
    private var salary = 0L

    @BeforeTest
    fun setUp() =
        runTest {
            database = createTestDatabase()
            transactions = RoomTransactionRepository(database, clock)
            food = database.insertCategory("Food", TransactionType.EXPENSE)
            cafe = database.insertCategory("Cafe", TransactionType.EXPENSE, parentId = food)
            salary = database.insertCategory("Salary", TransactionType.INCOME)
        }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private fun TestScope.createService(serverUrl: String = "https://budget.example.com"): SyncService {
        val engine =
            MockEngine { request ->
                authorization += request.headers[HttpHeaders.Authorization]
                if (request.method == HttpMethod.Post) {
                    sent += Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                }
                val status = server(sent.size)
                respond("{}", status)
            }
        val dataStore =
            PreferenceDataStoreFactory.createWithPath(scope = backgroundScope) {
                directory.resolve("sync-${System.nanoTime()}.preferences_pb").toOkioPath()
            }
        return SyncService(
            database = database,
            stateStore = SyncStateStore(dataStore),
            api = SyncApi(HttpClient(engine)),
            config = SyncConfig(serverUrl = serverUrl, appVersion = "1.4.0"),
            clock = clock,
        )
    }

    private suspend fun add(
        category: Long,
        amount: Long,
    ): Long = transactions.addTransaction(TransactionInput(category, amount, LocalDate(2026, 11, 14), "coffee"))

    private suspend fun pending(service: SyncService): Int = service.status.first().pendingCount

    @Test
    fun nothingIsSentWithoutServerOrToken() =
        runTest {
            add(cafe, 85_00)
            assertEquals(SyncOutcome.NotConfigured, createService().sync())
            val withoutServer = createService(serverUrl = "")
            withoutServer.setToken("token")
            assertEquals(SyncOutcome.NotConfigured, withoutServer.sync())
            assertEquals(0, sent.size)
        }

    @Test
    fun firstSyncSendsCategoriesAndRecordsOnce() =
        runTest {
            val service = createService()
            service.setToken("secret-token")
            add(cafe, 85_00)
            add(salary, 30_000_00)

            assertEquals(SyncOutcome.Success(2), service.sync())

            val body = sent.single()
            assertEquals(listOf("Bearer secret-token"), authorization)
            assertEquals("1.4.0", body["app_version"]?.jsonPrimitive?.content)
            val categories = body.getValue("categories").jsonArray.map { it.jsonObject }
            assertEquals(listOf("Food", "Salary", "Cafe"), categories.map { it.getValue("name").jsonPrimitive.content })
            val foodId = categories[0].getValue("id").jsonPrimitive.content
            assertEquals(foodId, categories[2].getValue("parent_id").jsonPrimitive.content)

            val operations = body.getValue("operations").jsonArray.map { it.jsonObject }
            assertEquals(listOf("8500", "3000000"), operations.map { it.getValue("amount_minor").jsonPrimitive.content })
            assertEquals(categories[2].getValue("id"), operations[0].getValue("category_id"))
            assertEquals("2026-11-14", operations[0].getValue("date").jsonPrimitive.content)
            assertEquals(0, pending(service))

            // Nothing changed: no request at all.
            assertEquals(SyncOutcome.Success(0), service.sync())
            assertEquals(1, sent.size)
            assertNull(service.status.first().lastError)
        }

    @Test
    fun editDuringRequestStaysPending() =
        runTest {
            val service = createService()
            service.setToken("token")
            val id = add(cafe, 85_00)
            server = { number ->
                if (number == 1) {
                    clock.now = Instant.parse("2026-11-16T10:00:05Z")
                    transactions.updateTransaction(id, TransactionInput(cafe, 90_00, LocalDate(2026, 11, 14)))
                }
                HttpStatusCode.OK
            }

            service.sync()

            // The loop sent the newer version in a second batch.
            assertEquals(2, sent.size)
            val second = sent[1].getValue("operations").jsonArray.single().jsonObject
            assertEquals("9000", second.getValue("amount_minor").jsonPrimitive.content)
            assertEquals(0, pending(service))
        }

    @Test
    fun deletionsAreSentOnceAndUndoBeforeSyncSendsNoDeletion() =
        runTest {
            val service = createService()
            service.setToken("token")
            val kept = add(cafe, 85_00)
            val removed = add(cafe, 40_00)
            service.sync()

            val deleted = transactions.deleteTransaction(removed)!!
            service.sync()
            val deletion = sent.last().getValue("deleted_operations").jsonArray.single().jsonObject
            assertEquals(deleted.syncId, deletion.getValue("id").jsonPrimitive.content)
            assertEquals(0, pending(service))

            // Deleted and undone before any sync: the server never hears about the deletion.
            val again = transactions.deleteTransaction(kept)!!
            transactions.restoreTransaction(again)
            val requests = sent.size
            service.sync()
            assertEquals(requests + 1, sent.size)
            assertEquals(0, sent.last().getValue("deleted_operations").jsonArray.size)
        }

    @Test
    fun failuresKeepEverythingPending() =
        runTest {
            val service = createService()
            service.setToken("token")
            add(cafe, 85_00)

            server = { HttpStatusCode.InternalServerError }
            assertEquals(SyncOutcome.Failed(SyncError.SERVER), service.sync())
            server = { HttpStatusCode.Unauthorized }
            assertEquals(SyncOutcome.Failed(SyncError.UNAUTHORIZED), service.sync())
            server = { HttpStatusCode.BadRequest }
            assertEquals(SyncOutcome.Failed(SyncError.REJECTED), service.sync())

            assertEquals(1, pending(service))
            assertEquals(SyncError.REJECTED, service.status.first().lastError)
            server = { HttpStatusCode.OK }
            service.sync()
            assertEquals(0, pending(service))
            assertNull(service.status.first().lastError)
        }

    @Test
    fun requestWithoutAnswerIsRetriedWithTheSameBatchId() =
        runTest {
            val service = createService()
            service.setToken("token")
            add(cafe, 85_00)
            server = { number -> if (number == 1) error("connection reset") else HttpStatusCode.OK }

            assertEquals(SyncOutcome.Success(1), service.sync())
            assertEquals(2, sent.size)
            assertEquals(sent[0]["batch_id"], sent[1]["batch_id"])
        }

    @Test
    fun categoryChangeAloneIsSent() =
        runTest {
            val service = createService()
            service.setToken("token")
            service.sync()
            val category = database.categoryDao().getById(salary)!!
            database.categoryDao().update(category.copy(name = "Allowance"))

            service.sync()

            assertEquals(2, sent.size)
            val categories = sent[1].getValue("categories").jsonArray
            val names = categories.map { it.jsonObject.getValue("name").jsonPrimitive.content }
            assertEquals(listOf("Food", "Allowance", "Cafe"), names)
            assertEquals(0, sent[1].getValue("operations").jsonArray.size)
        }

    @Test
    fun newTokenSendsEverythingAgain() =
        runTest {
            val service = createService()
            service.setToken("token")
            add(cafe, 85_00)
            add(cafe, 40_00)
            service.sync()

            service.setToken("token-of-another-device")

            assertEquals(2, pending(service))
            assertIs<SyncOutcome.Success>(service.sync())
            assertEquals(2, sent.last().getValue("operations").jsonArray.size)
        }

    @Test
    fun endpointAddsSchemeAndPath() {
        assertEquals("https://budget.example.com/api/expenses/sync/", SyncApi.endpoint("budget.example.com"))
        assertEquals("http://10.0.2.2:8000/api/expenses/sync/", SyncApi.endpoint(" http://10.0.2.2:8000/ "))
    }

    @Test
    fun syncIdsAreUuidV4() {
        val ids = List(1000) { newSyncId() }
        assertEquals(ids.size, ids.toSet().size)
        val uuidV4 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        ids.forEach { id -> assertTrue(uuidV4.matches(id), id) }
    }
}
