cmake_minimum_required(VERSION 3.13)

include($ENV{PICO_SDK_PATH}/external/pico_sdk_import.cmake)

project(kopico_app C CXX ASM)
pico_sdk_init()

add_executable(kopico_app wrapper.c kopico_shim.c kopico_stdio_globals.c)
target_compile_definitions(kopico_app PRIVATE USE_KOTLIN_MAIN=1)
target_link_libraries(kopico_app ${KOTLIN_APP_LIB} ${KOPICO_SDK_LIBS})
target_link_options(kopico_app PRIVATE "-B${LLD_DIR}" "-fuse-ld=lld")
pico_set_linker_script(kopico_app ${CMAKE_CURRENT_LIST_DIR}/memmap_kopico.ld)
