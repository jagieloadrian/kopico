package com.anjo.kopico

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

abstract class KopicoExtension {
    abstract val board: Property<String>

    abstract val sdkPath: DirectoryProperty
}
