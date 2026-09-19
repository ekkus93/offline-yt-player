package com.ekkus.offlineytplayer.coregateway

import java.io.Closeable
import java.lang.reflect.Method
import java.util.concurrent.Future

interface AppDownloadControlGateway : Closeable {
    fun pause(jobId: String): CoreGatewayResult<Boolean>
    fun resume(jobId: String): CoreGatewayResult<Boolean>
    fun cancel(jobId: String): CoreGatewayResult<Boolean>
    fun retry(jobId: String): CoreGatewayResult<Boolean>
}

class GeneratedUniffiDownloadControlGateway private constructor(
    private val ffiService: Any,
    private val dispatcher: CoreCallDispatcher,
) : AppDownloadControlGateway {
    fun pauseAsync(jobId: String): Future<CoreGatewayResult<Boolean>> = dispatcher.submit { pause(jobId) }

    override fun pause(jobId: String): CoreGatewayResult<Boolean> = control("pause", jobId)

    override fun resume(jobId: String): CoreGatewayResult<Boolean> = control("resume", jobId)

    override fun cancel(jobId: String): CoreGatewayResult<Boolean> = control("cancel", jobId)

    override fun retry(jobId: String): CoreGatewayResult<Boolean> = control("retry", jobId)

    override fun close() {
        dispatcher.close()
    }

    private fun control(methodName: String, jobId: String): CoreGatewayResult<Boolean> {
        val result = callFfi(methodName, jobId)
        return CoreGatewayResult(
            value = readBoolean(result, "updated"),
            error = readError(result),
        )
    }

    private fun callFfi(methodName: String, argument: String): Any {
        val method = ffiService.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.contentEquals(arrayOf(String::class.java))
        } ?: error("Generated FFI download-control service does not expose $methodName")
        return method.invoke(ffiService, argument)
            ?: error("Generated FFI download-control service returned null for $methodName")
    }

    companion object {
        fun open(
            databasePath: String,
            dispatcher: CoreCallDispatcher = CoreCallDispatcher.singleThreaded(),
        ): GeneratedUniffiDownloadControlGateway {
            val service = openGeneratedService(databasePath)
            return GeneratedUniffiDownloadControlGateway(service, dispatcher)
        }

        private fun openGeneratedService(databasePath: String): Any {
            val serviceClass = Class.forName("com.ekkus.offlineytplayer.core.FfiDownloadControlService")
            serviceClass.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            }?.let { method -> return method.invoke(null, databasePath) }

            val companion = serviceClass.declaredClasses.firstOrNull { it.simpleName == "Companion" }
                ?: error("Generated FfiDownloadControlService has no static or companion open(databasePath)")
            val companionInstance = serviceClass.getDeclaredField("Companion").get(null)
            val open: Method = companion.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            } ?: error("Generated FfiDownloadControlService.Companion has no open(databasePath)")
            return open.invoke(companionInstance, databasePath)
                ?: error("Generated FfiDownloadControlService.open returned null")
        }
    }
}

class FakeDownloadControlGateway : AppDownloadControlGateway {
    val pausedJobIds = mutableListOf<String>()
    val resumedJobIds = mutableListOf<String>()
    val canceledJobIds = mutableListOf<String>()
    val retriedJobIds = mutableListOf<String>()

    override fun pause(jobId: String): CoreGatewayResult<Boolean> {
        pausedJobIds += jobId
        return CoreGatewayResult(value = true, error = null)
    }

    override fun resume(jobId: String): CoreGatewayResult<Boolean> {
        resumedJobIds += jobId
        return CoreGatewayResult(value = true, error = null)
    }

    override fun cancel(jobId: String): CoreGatewayResult<Boolean> {
        canceledJobIds += jobId
        return CoreGatewayResult(value = true, error = null)
    }

    override fun retry(jobId: String): CoreGatewayResult<Boolean> {
        retriedJobIds += jobId
        return CoreGatewayResult(value = true, error = null)
    }

    override fun close() = Unit
}

private fun readError(result: Any): CoreGatewayError? {
    val error = readNullable(result, "error") ?: return null
    return CoreGatewayError(
        kind = readRequired(error, "kind").toString(),
        message = readString(error, "message"),
        retryable = readBoolean(error, "retryable"),
    )
}

private fun readRequired(target: Any, vararg names: String): Any =
    readNullable(target, *names) ?: error("Missing generated property ${names.joinToString("/")}")

private fun readNullable(target: Any, vararg names: String): Any? {
    for (name in names) {
        target.javaClass.methods.firstOrNull { it.name == name || it.name == "get${name.replaceFirstChar(Char::uppercase)}" }
            ?.takeIf { it.parameterTypes.isEmpty() }
            ?.let { return it.invoke(target) }
        target.javaClass.declaredFields.firstOrNull { it.name == name }?.let { field ->
            field.isAccessible = true
            return field.get(target)
        }
    }
    return null
}

private fun readString(target: Any, vararg names: String): String = readRequired(target, *names) as String

private fun readBoolean(target: Any, vararg names: String): Boolean = readRequired(target, *names) as Boolean
