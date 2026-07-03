package com.anjo.kopico.provisioning

import java.io.File

class OpenOcdProvisioner(
    private val cache: ToolCache,
    private val override: File? = null,
    private val searchPath: String? = System.getenv("PATH"),
) {
    fun provision(): File {
        override?.let { return it }
        ToolCache.findInPath("openocd", searchPath)?.let { return it.parentFile.parentFile }
        val dest = cache.dir("openocd", VERSION)
        if (dest.isDirectory && dest.walkTopDown().any { it.name == "openocd" && it.canExecute() }) {
            return dest
        }
        val archive = File(dest, ASSET)
        cache.download("$BASE_URL/$ASSET", archive)
        cache.extractTarGz(archive, dest)
        archive.delete()
        return dest
    }

    companion object {
        const val VERSION = "0.12.0+dev"
        private const val ASSET = "openocd-$VERSION-x86_64-lin.tar.gz"
        private const val BASE_URL =
            "https://github.com/raspberrypi/pico-sdk-tools/releases/download/v2.2.0-4"
    }
}
