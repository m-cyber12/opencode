package com.opencode.client.core.network

import com.opencode.client.core.AppError
import com.opencode.client.core.Outcome
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    fun client(auth: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(auth)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Executes [block] with a Call and decodes the body, mapping failures onto [AppError].
     * The response body is always closed.
     */
    inline fun <T> execute(
        callFactory: Call.Factory,
        request: Request,
        onBody: (Response) -> T
    ): Outcome<T> {
        val call = callFactory.newCall(request)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = try {
                        response.body?.string()
                    } catch (_: Exception) {
                        null
                    }
                    return Outcome.Err(AppError.Http(response.code, errBody))
                }
                when (val decoded = runCatching { onBody(response) }) {
                    is kotlin.Result.Failure -> Outcome.Err(
                        AppError.Parse(decoded.exception.message ?: "decode failed", null)
                    )
                    is kotlin.Result.Success -> Outcome.Ok(decoded.value)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (call.isCanceled()) return Outcome.Err(AppError.Cancelled())
            Outcome.Err(
                if (e.message?.contains("timeout", ignoreCase = true) == true)
                    AppError.Timeout(e.message ?: "timeout")
                else AppError.Network(e.message ?: e.javaClass.simpleName, e)
            )
        } catch (t: Throwable) {
            Outcome.Err(AppError.from(t))
        }
    }

    fun get(url: String): Request =
        Request.Builder().url(url).get().build()

    fun post(url: String, json: String?): Request =
        Request.Builder()
            .url(url)
            .post((json ?: "").toRequestBody(JSON_MEDIA))
            .build()

    fun delete(url: String): Request =
        Request.Builder().url(url).delete().build()

    fun patch(url: String, json: String?): Request =
        Request.Builder()
            .url(url)
            .patch((json ?: "{}").toRequestBody(JSON_MEDIA))
            .build()
}

/** URL-encodes a query parameter value. */
fun q(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
