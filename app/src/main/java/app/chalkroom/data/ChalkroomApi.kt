package app.chalkroom.data

import app.chalkroom.shared.CreateDeckRequest
import app.chalkroom.shared.CreateDeckResponse
import app.chalkroom.shared.ErrorBody
import app.chalkroom.shared.StudentId
import app.chalkroom.shared.TurnRequest
import app.chalkroom.shared.TurnResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    /** [code] is a server `ErrorCodes` value, or [NETWORK] when the server couldn't be reached or answered oddly. */
    data class Failure(val code: String) : ApiResult<Nothing>

    companion object {
        const val NETWORK = "network"
    }
}

/** The app's only door to the server. Never throws to the UI; cancellation still propagates. */
class ChalkroomApi(private val client: HttpClient, private val baseUrl: String) {

    suspend fun createDeck(notes: String, studentId: StudentId): ApiResult<CreateDeckResponse> =
        call { client.post("$baseUrl/decks") { json(CreateDeckRequest(notes, studentId)) } }

    suspend fun turn(request: TurnRequest): ApiResult<TurnResponse> =
        call { client.post("$baseUrl/lessons/turn") { json(request) } }

    private fun io.ktor.client.request.HttpRequestBuilder.json(body: Any) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend inline fun <reified T> call(request: () -> HttpResponse): ApiResult<T> = try {
        val response = request()
        if (response.status.isSuccess()) {
            ApiResult.Success(response.body<T>())
        } else {
            ApiResult.Failure(runCatching { response.body<ErrorBody>().code }.getOrDefault(ApiResult.NETWORK))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiResult.Failure(ApiResult.NETWORK)
    }
}
