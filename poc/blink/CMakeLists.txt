cmake_minimum_required(VERSION 3.13)

include($ENV{PICO_SDK_PATH}/external/pico_sdk_import.cmake)

project(kblink C CXX ASM)
pico_sdk_init()

add_executable(kblink
    wrapper.c
)

# ponytail: czysty C blink na start (sanity check toolchaina);
# libapp.a z Kotlina dolinkowywana w drugim kroku przez KOTLIN_APP_LIB
if(DEFINED KOTLIN_APP_LIB)
    target_sources(kblink PRIVATE kopico_shim.c kopico_stdio_globals.c)
    target_link_libraries(kblink ${KOTLIN_APP_LIB})
    target_compile_definitions(kblink PRIVATE USE_KOTLIN_MAIN=1)
    # ld.bfd nie trawi relokacji Thumb z obiektów LLVM — linkujemy przez lld
    if(DEFINED LLD_DIR)
        target_link_options(kblink PRIVATE "-B${LLD_DIR}" "-fuse-ld=lld")
    endif()
    # własny linker script: .got z obiektów K/N musi trafić do FLASH
    pico_set_linker_script(kblink ${CMAKE_CURRENT_LIST_DIR}/memmap_kopico.ld)
endif()

target_link_libraries(kblink pico_stdlib)

pico_add_extra_outputs(kblink)
