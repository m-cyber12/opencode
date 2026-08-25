package com.opencode.client.core

/**
 * Typed application errors. Everything user-visible flows through this hierarchy so the UI can
 * present friendly copy while retaining technical detail in an expandable diagnostics section.
 */
sealed class AppError(
    val userMessage: String,
    val technical: String? = null,
    cause: Throwable? = null
) : Exception(technical ?: userMessage, cause) {

    /** No server is currently connected. */
    class NotConnected : AppError("Not connected to an OpenCode server. Connect first.")

    /** The endpoint exists but returned a non-success HTTP status. */
    class Http(val code: Int, body: String?) : AppError(
        userMessage = when (code) {
            401, 403 -> "Authentication failed. Check the server credentials."
            404 -> "This OpenCode server does not provide that capability."
            in 500..599 -> "OpenCode server returned an internal error."
            else -> "The server rejected the request (HTTP $code)."
        },
        technical = "HTTP $code${body?.take(2000)?.let { " :: $it" } ?: ""}"
    )

    /** Request failed at the transport level (DNS, socket, TLS...). */
    class Network(detail: String, cause: Throwable) : AppError(
        "Could not reach the OpenCode server.",
        technical = detail,
        cause = cause
    )

    /** Request timed out. */
    class Timeout(detail: String) : AppError(
        "The OpenCode server took too long to respond.",
        technical = detail
    )

    /** A response could not be decoded. Never fatal - surfaced per-feature. */
    class Parse(what: String, snippet: String?) : AppError(
        "Received an unexpected response from the server.",
        technical = "$what :: ${snippet?.take(600) ?: "<empty>"}"
    )

    /** The SSE stream dropped and reconnection attempts are exhausted for now. */
    class StreamLost(detail: String, cause: Throwable? = null) : AppError(
        "Live connection lost. Retrying...",
        technical = detail,
        cause = cause
    )

    companion object {
        fun from(t: Throwable): AppError = when (t) {
            is AppError -> t
            is kotlinx.coroutines.CancellationException -> Cancelled()
            is java.io.IOException -> Network(t.message ?: t.javaClass.simpleName, t)
            else -> Unknown(t.message ?: t.javaClass.simpleName, t)
        }
    }

    class Cancelled : AppError("Cancelled.")
    class Unknown(detail: String, cause: Throwable) : AppError("Something went wrong.", detail, cause)
}

/** Minimal Result type used across repositories (avoids boxing through kotlin.Result). */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

inline fun <T> Outcome<T>.onOk(block: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Ok) block(value)
    return this
}

inline fun <T> Outcome<T>.onErr(block: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Err) block(error)
    return this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Ok)?.value

suspend fun <T> runCatchingOutcome(block: suspend () -> T): Outcome<T> = try {
    Outcome.Ok(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (t: Throwable) {
    Outcome.Err(AppError.from(t))
}
