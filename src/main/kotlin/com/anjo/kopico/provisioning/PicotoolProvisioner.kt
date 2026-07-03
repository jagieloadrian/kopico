package com.anjo.kopico.provisioning

import java.io.File

class PicotoolProvisioner(
    private val cache: ToolCache,
    private val override: File? = null,
    private val searchPath: String? = System.getenv("PATH"),
) {
    fun provision(): File {
        override?.let { return it }
        ToolCache.findInPath("picotool", searchPath)?.let { return it }
        val dest = cache.dir("picotool", VERSION)
        val executable = findExecutable(dest)
        if (executable != null) return executable

        val archive = File(dest, ASSET)
        cache.download("$BASE_URL/$ASSET", archive)
        cache.extractTarGz(archive, dest)
        archive.delete()
        return findExecutable(dest)
            ?: throw org.gradle.api.GradleException(
                "kopico: extracted picotool does not contain an executable file 'picotool' in $dest",
            )
    }

    private fun findExecutable(dir: File): File? = dir.walkTopDown().firstOrNull(::isPicotool)

    private fun isPicotool(file: File): Boolean = file.name == "picotool" && file.isFile && file.canExecute()

    companion object {
        const val VERSION = "2.2.0-a4"
        private const val ASSET = "picotool-$VERSION-x86_64-lin.tar.gz"
        private const val BASE_URL =
            "https://github.com/raspberrypi/pico-sdk-tools/releases/download/v2.2.0-4"
    }
}
