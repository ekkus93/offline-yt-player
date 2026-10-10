package com.ekkus.offlineytplayer.coregateway

import java.io.Closeable
import java.lang.reflect.Method

interface AppLibraryMutationGateway : Closeable {
    fun renameDisplayTitle(itemId: String, displayTitle: String): CoreGatewayResult<String?>
    fun removeLibraryItem(libraryRoot: String, itemId: String, confirmed: Boolean): CoreGatewayResult<Boolean>
}

class GeneratedUniffiLibraryMutationGateway private constructor(
    private val renameService: Any,
    private val removeService: Any,
) : AppLibraryMutationGateway {
    override fun renameDisplayTitle(itemId: String, displayTitle: String): CoreGatewayResult<String?> {
        checkNotMainThread()
        val result = call(renameService, "renameDisplayTitle", itemId, displayTitle)
        val errorMessage = readNullableMutation(result, "errorMessage", "error_message") as String?
        val renamed = readRequiredBooleanMutation(result, "renamed")
        val title = readNullableMutation(result, "displayTitle", "display_title") as String?
        return CoreGatewayResult(
            value = title.takeIf { renamed },
            error = errorMessage?.let { CoreGatewayError("library_rename", it, false) },
        )
    }

    override fun removeLibraryItem(
        libraryRoot: String,
        itemId: String,
        confirmed: Boolean,
    ): CoreGatewayResult<Boolean> {
        checkNotMainThread()
        val result = call(removeService, "removeLibraryItem", libraryRoot, itemId, confirmed)
        val errorMessage = readNullableMutation(result, "errorMessage", "error_message") as String?
        val removed = readRequiredBooleanMutation(result, "removed")
        return CoreGatewayResult(
            value = removed,
            error = errorMessage?.let { CoreGatewayError("library_remove", it, false) },
        )
    }

    override fun close() = Unit

    private fun call(target: Any, methodName: String, vararg arguments: Any?): Any {
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.size == arguments.size
        } ?: error("Generated library mutation service does not expose $methodName/${arguments.size}")
        return method.invoke(target, *arguments)
            ?: error("Generated library mutation service returned null for $methodName")
    }

    companion object {
        fun open(databasePath: String): GeneratedUniffiLibraryMutationGateway =
            GeneratedUniffiLibraryMutationGateway(
                renameService = openGeneratedService(
                    "com.ekkus.offlineytplayer.core.FfiLibraryRenameService",
                    databasePath,
                ),
                removeService = openGeneratedService(
                    "com.ekkus.offlineytplayer.core.FfiLibraryRemoveService",
                    databasePath,
                ),
            )

        private fun openGeneratedService(className: String, databasePath: String): Any {
            val serviceClass = Class.forName(className)
            serviceClass.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            }?.let { return it.invoke(null, databasePath) }
            val companion = serviceClass.declaredClasses.firstOrNull { it.simpleName == "Companion" }
                ?: error("Generated $className has no open(databasePath)")
            val companionInstance = serviceClass.getDeclaredField("Companion").get(null)
            val open: Method = companion.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            } ?: error("Generated $className.Companion has no open(databasePath)")
            return open.invoke(companionInstance, databasePath)
                ?: error("Generated $className.open returned null")
        }
    }
}

private fun readNullableMutation(target: Any, vararg names: String): Any? {
    for (name in names) {
        target.javaClass.methods.firstOrNull {
            it.name == name || it.name == "get${name.replaceFirstChar(Char::uppercase)}"
        }?.takeIf { it.parameterTypes.isEmpty() }?.let { return it.invoke(target) }
        target.javaClass.declaredFields.firstOrNull { it.name == name }?.let { field ->
            field.isAccessible = true
            return field.get(target)
        }
    }
    return null
}

internal fun readRequiredBooleanMutation(target: Any, vararg names: String): Boolean {
    val value = readNullableMutation(target, *names)
        ?: error("Missing generated property ${names.joinToString("/")}")
    return value as? Boolean
        ?: error("Generated property ${names.joinToString("/")} is not Boolean")
}
