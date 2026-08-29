cmake_minimum_required(VERSION 3.22)
project(llamajni LANGUAGES C CXX)

# llama.cpp source location (prebuilt once; not committed to keep repo lean).
set(LLAMA_DIR "C:/Users/rajor/AppData/Local/Temp/opencode/llama.cpp" CACHE PATH "llama.cpp source dir")

# Disable building llama.cpp extras — we only need the library.
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} llama_build)

add_library(llamajni SHARED llamajni.cpp)
set_target_properties(llamajni PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON)
target_include_directories(llamajni PRIVATE ${LLAMA_DIR}/include)
target_link_libraries(llamajni llama ggml log)
