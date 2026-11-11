package com.rel.adbhelper.core.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

interface ProcessRunner {
    suspend fun run(command: List<String>): ProcessResult
}

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

class RealProcessRunner : ProcessRunner {
    override suspend fun run(command: List<String>): ProcessResult = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(command).start()
        // Read stdout and stderr concurrently: a process that fills one pipe's buffer while
        // blocked writing to the other would otherwise deadlock a sequential read.
        val stdoutJob = async { process.inputStream.bufferedReader().readText() }
        val stderrJob = async { process.errorStream.bufferedReader().readText() }
        val (stdout, stderr) = awaitAll(stdoutJob, stderrJob)
        val exitCode = process.waitFor()
        ProcessResult(exitCode, stdout, stderr)
    }
}
