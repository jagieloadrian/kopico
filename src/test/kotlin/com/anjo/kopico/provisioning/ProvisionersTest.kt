package com.anjo.kopico.provisioning

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.api.GradleException
import java.io.File
import kotlin.io.path.createTempDirectory

class ProvisionersTest : FunSpec({

    fun tempDir(prefix: String): File = createTempDirectory(prefix).toFile()

    context("ToolCache") {
        test("download over file:// writes the file to the destination") {
            val cache = ToolCache(tempDir("guh"))
            val source = File(tempDir("src"), "tool.bin").apply { writeText("payload") }
            val dest = File(cache.dir("tool", "1.0"), "tool.bin")
            cache.download(source.toURI().toString(), dest)
            dest.readText() shouldBe "payload"
        }

        test("unreachable source gives a readable configuration error") {
            val cache = ToolCache(tempDir("guh"))
            val e =
                shouldThrow<GradleException> {
                    cache.download("file:///nonexistent/kopico/tool.bin", File(tempDir("dst"), "t"))
                }
            e.message shouldContain "failed to download"
        }

        test("bad checksum gives a readable error and removes the file") {
            val cache = ToolCache(tempDir("guh"))
            val file = File(tempDir("f"), "archive.tar.gz").apply { writeText("data") }
            val e = shouldThrow<GradleException> { cache.verifySha256(file, "deadbeef") }
            e.message shouldContain "checksum mismatch"
            file.shouldNotExist()
        }

        test("correct checksum passes") {
            val cache = ToolCache(tempDir("guh"))
            val file = File(tempDir("f"), "archive.tar.gz").apply { writeText("data") }
            cache.verifySha256(file, cache.sha256(file))
            file.shouldExist()
        }

        test("findInPath finds an executable file") {
            val bin = tempDir("bin")
            val tool =
                File(bin, "sometool").apply {
                    writeText("#!/bin/true\n")
                    setExecutable(true)
                }
            ToolCache.findInPath("sometool", bin.absolutePath) shouldBe tool
            ToolCache.findInPath("othertool", bin.absolutePath) shouldBe null
        }
    }

    context("ArmToolchainProvisioner (FR-012/FR-013)") {
        fun fakeGcc(
            root: File,
            version: String,
        ) {
            File(root, "bin").mkdirs()
            File(root, "bin/arm-none-eabi-gcc").apply {
                writeText("#!/bin/bash\necho $version\n")
                setExecutable(true)
            }
        }

        test("tool from PATH at the pinned major version takes precedence — zero downloads") {
            val root = tempDir("gcc-root")
            fakeGcc(root, "15.2.1")
            val provisioner =
                ArmToolchainProvisioner(
                    cache = ToolCache(tempDir("guh")),
                    searchPath = File(root, "bin").absolutePath,
                )
            provisioner.provision() shouldBe root
        }

        test("gcc from PATH at a different major version is skipped in favor of the cache") {
            val pathRoot = tempDir("gcc-13-root")
            fakeGcc(pathRoot, "13.2.1")
            val cache = ToolCache(tempDir("guh"))
            val version = ArmToolchainProvisioner.VERSION
            val installed = File(cache.dir("arm-toolchain", version), "xpack-arm-none-eabi-gcc-$version")
            fakeGcc(installed, "15.2.1")
            val provisioner = ArmToolchainProvisioner(cache, searchPath = File(pathRoot, "bin").absolutePath)
            provisioner.provision() shouldBe installed
        }

        test("cache hit returns the installed toolchain without network access") {
            val guh = tempDir("guh")
            val cache = ToolCache(guh)
            val version = ArmToolchainProvisioner.VERSION
            val installed = File(cache.dir("arm-toolchain", version), "xpack-arm-none-eabi-gcc-$version")
            File(installed, "bin").mkdirs()
            File(installed, "bin/arm-none-eabi-gcc").apply {
                writeText("")
                setExecutable(true)
            }
            val provisioner = ArmToolchainProvisioner(cache, searchPath = null)
            provisioner.provision() shouldBe installed
        }
    }

    context("PicoSdkProvisioner (FR-003/FR-011)") {
        fun sdkDir(
            major: Int,
            minor: Int,
            revision: Int,
        ): File {
            val sdk = tempDir("sdk")
            File(sdk, "pico_sdk_version.cmake").writeText(
                """
                set(PICO_SDK_VERSION_MAJOR $major)
                set(PICO_SDK_VERSION_MINOR $minor)
                set(PICO_SDK_VERSION_REVISION $revision)
                """.trimIndent(),
            )
            return sdk
        }

        test("explicit sdkPath at version >= 2.2.0 is accepted") {
            val provisioner = PicoSdkProvisioner(ToolCache(tempDir("guh")))
            val sdk = sdkDir(2, 2, 0)
            provisioner.provision(sdk) shouldBe sdk
        }

        test("SDK at version < 2.2.0 is rejected with a readable message") {
            val provisioner = PicoSdkProvisioner(ToolCache(tempDir("guh")))
            val e = shouldThrow<GradleException> { provisioner.validateUserSdk(sdkDir(1, 5, 1)) }
            e.message shouldContain "1.5.1"
            e.message shouldContain "2.2.0"
        }

        test("sdkPath without pico_sdk_version.cmake is rejected") {
            val provisioner = PicoSdkProvisioner(ToolCache(tempDir("guh")))
            val e = shouldThrow<GradleException> { provisioner.validateUserSdk(tempDir("not-sdk")) }
            e.message shouldContain "pico_sdk_version.cmake"
        }

        test("version comparison works per component") {
            PicoSdkProvisioner.isAtLeastMinVersion(listOf(2, 2, 0)).shouldBeTrue()
            PicoSdkProvisioner.isAtLeastMinVersion(listOf(3, 0, 0)).shouldBeTrue()
            PicoSdkProvisioner.isAtLeastMinVersion(listOf(2, 1, 9)).shouldBeFalse()
            PicoSdkProvisioner.isAtLeastMinVersion(listOf(1, 5, 1)).shouldBeFalse()
        }
    }

    context("KotlinNativeProvisioner — patch .bc (poc/konan-target-spike.md § Round 3)") {
        fun fakeClang(): File {
            val bin = tempDir("fake-llvm")
            val clang = File(bin, "clang")
            clang.writeText(
                """
                #!/bin/bash
                out=-; in=""; prev=""
                for a in "$@"; do
                  if [[ "${'$'}prev" == "-o" ]]; then out="${'$'}a"; prev="${'$'}a"; continue; fi
                  case "${'$'}a" in
                    -) in="-";;
                    -*) ;;
                    ir) ;;
                    *) in="${'$'}a";;
                  esac
                  prev="${'$'}a"
                done
                if [[ "${'$'}in" == "-" || -z "${'$'}in" ]]; then data=$(cat); else data=$(cat "${'$'}in"); fi
                if [[ "${'$'}out" == "-" ]]; then printf '%s' "${'$'}data"; else printf '%s' "${'$'}data" > "${'$'}out"; fi
                """.trimIndent(),
            )
            clang.setExecutable(true)
            return clang
        }

        fun fakeDist(): File {
            val dist = tempDir("kn-dist")
            val native = File(dist, "konan/targets/linux_arm32_hfp/native").also { it.mkdirs() }
            File(native, "runtime.bc").writeText(
                """define void @f() "target-cpu"="arm1176jzf-s" "target-features"="-thumb-mode" { ret void }""",
            )
            return dist
        }

        test("patch replaces attributes and is idempotent") {
            val provisioner = KotlinNativeProvisioner(ToolCache(tempDir("guh")))
            val dist = fakeDist()
            val clang = fakeClang()
            val bc = File(dist, "konan/targets/linux_arm32_hfp/native/runtime.bc")

            provisioner.patchBitcodeIfNeeded(dist, clang).shouldBeTrue()
            val patchedOnce = bc.readText()
            patchedOnce shouldContain "cortex-m0plus"
            patchedOnce shouldContain "+thumb-mode"
            File(dist, "konan/targets/linux_arm32_hfp/native.bak/runtime.bc").shouldExist()

            provisioner.patchBitcodeIfNeeded(dist, clang).shouldBeFalse()
            bc.readText() shouldBe patchedOnce
        }

        test("incomplete distribution gives a readable error") {
            val provisioner = KotlinNativeProvisioner(ToolCache(tempDir("guh")))
            val e =
                shouldThrow<GradleException> {
                    provisioner.patchBitcodeIfNeeded(tempDir("empty-dist"), fakeClang())
                }
            e.message shouldContain "incomplete"
        }
    }
})
