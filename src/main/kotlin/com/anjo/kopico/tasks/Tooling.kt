package com.anjo.kopico.tasks

import org.gradle.api.GradleException
import java.io.File

internal fun runTool(
    command: List<String>,
    workDir: File? = null,
    env: Map<String, String> = emptyMap(),
) {
    val builder = ProcessBuilder(command).redirectErrorStream(true)
    workDir?.let { builder.directory(it) }
    builder.environment().putAll(env)
    val process = builder.start()
    val output = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0) {
        throw GradleException(
            "kopico: command '${command.joinToString(" ")}' failed:\n$output",
        )
    }
}

internal fun copyPluginResource(
    name: String,
    dest: File,
) {
    dest.parentFile.mkdirs()
    val stream =
        object {}.javaClass.getResourceAsStream("/kopico/$name")
            ?: throw GradleException("kopico: missing resource /kopico/$name in the plugin distribution")
    stream.use { input -> dest.outputStream().use { input.copyTo(it) } }
}
