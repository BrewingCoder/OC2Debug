package com.brewingcoder.oc2debug.tool

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

object AsyncJobRegistry {
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "oc2debug-async").also { it.isDaemon = true }
    }

    private data class Job(
        val future: Future<String>,
        val startedMs: Long = System.currentTimeMillis(),
    )

    private val jobs = ConcurrentHashMap<String, Job>()

    /** Submit a callable; returns a job ID immediately. */
    fun submit(block: () -> String): String {
        val id = UUID.randomUUID().toString()
        val future = executor.submit(block)
        jobs[id] = Job(future)
        return id
    }

    /** Cancel and discard all in-flight jobs — call on world unload to avoid blocking shutdown. */
    fun cancelAll() {
        jobs.values.forEach { it.future.cancel(true) }
        jobs.clear()
    }

    /**
     * Poll a job. Returns:
     *  - `{"status":"running","elapsed_ms":N}` if still in progress
     *  - the job's result string if done (and removes it from the registry)
     *  - `{"status":"error","message":"..."}` if the job threw
     *  - `{"status":"not_found"}` if the id is unknown
     */
    fun poll(id: String): String {
        val job = jobs[id] ?: return """{"status":"not_found"}"""
        if (!job.future.isDone) {
            val elapsed = System.currentTimeMillis() - job.startedMs
            return """{"status":"running","elapsed_ms":$elapsed}"""
        }
        jobs.remove(id)
        return try {
            job.future.get()
        } catch (e: Exception) {
            val msg = (e.cause ?: e).message?.replace("\\", "\\\\")?.replace("\"", "\\\"") ?: "unknown"
            """{"status":"error","message":"$msg"}"""
        }
    }
}
