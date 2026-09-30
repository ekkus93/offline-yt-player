package com.ekkus.offlineytplayer.coregateway

/** Reads provider-neutral display metadata persisted with durable executable download work. */
class DownloadPresentationGateway private constructor(private val service: Any) {
    fun titlesByJobId(): Map<String, String> {
        val method = service.javaClass.methods.firstOrNull {
            it.name == "downloadPresentations" && it.parameterTypes.isEmpty()
        } ?: error("Generated worker service does not expose downloadPresentations()")
        val result = method.invoke(service) ?: error("Generated worker service returned null")
        val error = readProperty(result, "error")
        check(error == null) { "Unable to read durable download presentation metadata" }
        val items = readProperty(result, "items") as? List<*> ?: emptyList<Any>()
        return items.filterNotNull().associate { item ->
            (readProperty(item, "jobId", "job_id") as String) to
                (readProperty(item, "displayTitle", "display_title") as String)
        }
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
