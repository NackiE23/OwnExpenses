package com.nackie.ownexpenses.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** What came back from the server, without interpretation. */
internal sealed interface ApiResult {
    data class Response(
        val status: Int,
        val body: String,
    ) : ApiResult {
        val isSuccess: Boolean get() = status in HTTP_OK_RANGE
    }

    /** No answer at all: offline, DNS, TLS, timeout. */
    data class NoConnection(
        val cause: Throwable,
    ) : ApiResult
}

private val HTTP_OK_RANGE = 200..299

/** The one endpoint of the OwnBudget server this app talks to. Push only: the server sends nothing back. */
internal class SyncApi(
    private val client: HttpClient,
) {
    suspend fun ping(
        serverUrl: String,
        token: String,
    ): ApiResult =
        call {
            client.get(endpoint(serverUrl)) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }

    suspend fun send(
        serverUrl: String,
        token: String,
        request: SyncRequest,
    ): ApiResult =
        call {
            client.post(endpoint(serverUrl)) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(SyncRequest.serializer(), request))
            }
        }

    private suspend fun call(request: suspend () -> HttpResponse): ApiResult =
        try {
            val response = request()
            ApiResult.Response(response.status.value, response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Engines throw their own exception types (unknown host, TLS, timeouts); all mean "no answer".
            ApiResult.NoConnection(e)
        }

    companion object {
        private const val SYNC_PATH = "api/expenses/sync/"

        private val json = Json { encodeDefaults = true }

        /** `https://budget.example.com` or with a trailing slash; a bare host gets https. */
        fun endpoint(serverUrl: String): String {
            val base = serverUrl.trim().trimEnd('/')
            val withScheme = if ("://" in base) base else "https://$base"
            return "$withScheme/$SYNC_PATH"
        }
    }
}
