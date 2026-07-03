package com.anjo.kopico.tasks

import com.anjo.kopico.provisioning.PicotoolProvisioner
import com.anjo.kopico.provisioning.ToolCache
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = " Work in progress")
abstract class GenerateUf2Task : DefaultTask() {
    @get:Internal
    abstract val gradleUserHome: Property<File>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val elfFile: RegularFileProperty

    @get:OutputFile
    abstract val uf2File: RegularFileProperty

    @TaskAction
    fun run() {
        val picotool = PicotoolProvisioner(ToolCache(gradleUserHome.get())).provision()
        uf2File.get().asFile.parentFile.mkdirs()
        runTool(
            listOf(
                picotool.absolutePath,
                "uf2",
                "convert",
                elfFile.get().asFile.absolutePath,
                uf2File.get().asFile.absolutePath,
            ),
        )
    }
}
