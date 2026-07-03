package com.anjo.kopico

object KonanRetargeting {
    const val HOST_TARGET = "linux_arm32_hfp"

    private const val FEATURES =
        "+strict-align,+thumb-mode,+soft-float,-neon,-vfp2,-vfp2sp,-vfp3,-vfp4,-fp64,-dsp"

    fun cpuFor(board: BoardVariant): String =
        when (board.chip) {
            Chip.RP2040 -> "cortex-m0plus"
            Chip.RP2350 -> "cortex-m33"
        }

    fun overrideProperties(board: BoardVariant): String =
        listOf(
            "targetCpu.$HOST_TARGET=${cpuFor(board)}",
            "targetCpuFeatures.$HOST_TARGET=$FEATURES",
            "targetTriple.$HOST_TARGET=${board.targetTriple}",
            "staticLibraryRelocationMode.$HOST_TARGET=static",
            "dynamicLibraryRelocationMode.$HOST_TARGET=static",
            "clangFlags.$HOST_TARGET=-cc1 -mfloat-abi soft -emit-obj -disable-llvm-optzns -x ir",
        ).joinToString(";")

    val binaryFlags: List<String> =
        listOf(
            "-Xbinary=gc=noop",
            "-Xbinary=gcSchedulerType=manual",
            "-Xallocator=std",
        )
}
