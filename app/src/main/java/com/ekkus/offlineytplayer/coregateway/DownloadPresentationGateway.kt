package com.ekkus.offlineytplayer.coregateway

/** Reads provider-neutral display metadata persisted with durable executable download work. */
class DownloadPresentationGateway private constructor(private val service: Any) {
    fun titlesByJobId(): Map<String, String> {
        val method = service.javaClass.methods.firstOrNull {
            it.name == "downloadPresentations" && it.parameterTypes.isEmpty()
        } ?: error("Generated worker service does not expose downloadPresentations()")
        val result = method.invoke(service) ?: error("Generated worker service returned null")
        val error = readRequiredNullablePresentationError(result)
        check(error == null) { "Unable to read durable download presentation metadata" }
        val items = readRequiredPresentationItems(result)
        return mapRequiredPresentationItems(items)
    }

    companion object {
        fun open(databasePath: String): DownloadPresentationGateway {
            val serviceClass = Class.forName("com.ekkus.offlineytplayer.core.FfiDownloadWorkerService")
            val service = serviceClass.methods.firstOrNull {
                it.name == "open" && it.parameterTypes.contentEquals(arrayOf(String::class.java))
            }?.invoke(null, databasePath) ?: run {
                val companionInstance = serviceClass.getDeclaredField("Companion").get(null)
                val open = companionInstance.javaClass.methods.first {
                    it.name == "open" && it.parameterTypes.contentEquals(arrayOf(String::class.java))
                }
                open.invoke(companionInstance, databasePath)
            }
            return DownloadPresentationGateway(service)
        }
    }
}


internal fun readRequiredPresentationItems(target: Any): List<*> {
    val value = readProperty(target, "items")
        ?: error("Missing generated property items")
    return value as? List<*>
        ?: error("Generated property items is not a List")
}

internal fun mapRequiredPresentationItems(items: List<*>): Map<String, String> {
    val entries = items.map { entry ->
        val item = requireNotNull(entry) { "Null generated presentation item" }
        val jobId = readProperty(item, "jobId", "job_id") as? String
            ?: error("Missing generated presentation job ID")
        val title = readProperty(item, "displayTitle", "display_title") as? String
            ?: error("Missing generated presentation title")
        check(jobId.isNotBlank() && title.isNotBlank()) { "Blank generated presentation identity or title" }
        jobId to title
    }
    check(entries.map { it.first }.toSet().size == entries.size) { "Duplicate generated presentation job ID" }
    return entries.toMap()
}

private fun readProperty(target: Any, vararg names: String): Any? {
    for (name in names) {
        target.javaClass.methods.firstOrNull {
            it.parameterTypes.isEmpty() && (it.name == name || it.name == "get${name.replaceFirstChar(Char::uppercase)}")
        }?.let { return it.invoke(target) }
        target.javaClass.declaredFields.firstOrNull { it.name == name }?.let {
            it.isAccessible = true
            return it.get(target)
        }
    }
    return null
}

internal fun readRequiredNullablePresentationError(target: Any): Any? {
    val method = target.javaClass.methods.firstOrNull {
        it.parameterTypes.isEmpty() && (it.name == "error" || it.name == "getError")
    }
    if (method != null) return method.invoke(target)
    val field = target.javaClass.declaredFields.firstOrNull { it.name == "error" }
        ?: error("Missing generated presentation error property")
    field.isAccessible = true
    return field.get(target)
}
