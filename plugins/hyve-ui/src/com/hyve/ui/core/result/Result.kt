package com.hyve.ui.core.result

/**
 * Result type for operations that can fail.
 * Follows the Result/Either pattern for explicit error handling.
 *
 * @param T The success type
 * @param E The error type
 */
sealed class Result<out T, out E> {
    /**
     * Successful result containing a value
     */
    data class Success<T>(val value: T) : Result<T, Nothing>() {
        override fun toString(): String = "Success($value)"
    }

    /**
     * Failed result containing an error
     */
    data class Failure<E>(val error: E) : Result<Nothing, E>() {
        override fun toString(): String = "Failure($error)"
    }

    /**
     * Returns true if this is a Success
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Returns true if this is a Failure
     */
    fun isFailure(): Boolean = this is Failure

    /**
     * Transform the success value if present
     */
    inline fun <R> map(transform: (T) -> R): Result<R, E> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    /**
     * Transform the error if present
     */
    inline fun <R> mapError(transform: (E) -> R): Result<T, R> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
    }

    /**
     * Chain result-producing operations
     */
    inline fun <R> flatMap(transform: (T) -> Result<R, @UnsafeVariance E>): Result<R, E> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    /**
     * Fold both success and failure cases into a single result
     */
    inline fun <R> fold(onSuccess: (T) -> R, onFailure: (E) -> R): R = when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(error)
    }

    /**
     * Get the value or compute a default from the error
     */
    inline fun getOrElse(default: (@UnsafeVariance E) -> @UnsafeVariance T): @UnsafeVariance T = when (this) {
        is Success -> value
        is Failure -> default(error)
    }

    /**
     * Get the value or return null
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    /**
     * Get the error or return null
     */
    fun errorOrNull(): E? = when (this) {
        is Success -> null
        is Failure -> error
    }

    /**
     * Get the value or throw an exception
     * Only use when failure is truly exceptional
     */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw IllegalStateException("Result was a failure: $error")
    }

    /**
     * Execute a side effect if successful
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T, E> {
        if (this is Success) action(value)
        return this
    }

    /**
     * Execute a side effect if failed
     */
    inline fun onFailure(action: (E) -> Unit): Result<T, E> {
        if (this is Failure) action(error)
        return this
    }

    companion object {
        /**
         * Create a successful result
         */
        fun <T> success(value: T): Result<T, Nothing> = Success(value)

        /**
         * Create a failed result
         */
        fun <E> failure(error: E): Result<Nothing, E> = Failure(error)

        /**
         * Wrap a potentially throwing operation in a Result
         * Converts exceptions to Failure
         */
        inline fun <T> catching(block: () -> T): Result<T, Throwable> = try {
            Success(block())
        } catch (e: Throwable) {
            Failure(e)
        }
    }
}

/**
 * Extension function to convert Kotlin's standard Result to our Result type
 */
fun <T> kotlin.Result<T>.toResult(): Result<T, Throwable> =
    fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it) }
    )
