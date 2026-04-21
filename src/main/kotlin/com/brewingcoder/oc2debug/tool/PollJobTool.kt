package com.brewingcoder.oc2debug.tool

import com.google.gson.JsonObject

object PollJobTool : Tool(
    name = "poll_job",
    description = "Poll the result of an async job (e.g. find_biome). Returns {\"status\":\"running\"} while in progress, or the job result when done.",
    inputSchemaJson = """{"type":"object","required":["job_id"],"properties":{"job_id":{"type":"string"}}}""",
) {
    override fun invoke(args: JsonObject): String {
        val id = args.strReq("job_id")
        return AsyncJobRegistry.poll(id)
    }
}
