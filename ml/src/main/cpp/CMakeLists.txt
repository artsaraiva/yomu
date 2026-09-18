cmake_minimum_required(VERSION 3.22.1)
project("yomu_ml")

set(LLAMA_STATIC ON CACHE BOOL "" FORCE)
# common supplies the Jinja chat-template engine (#281); its HTTP/subprocess parts stay off.
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../llama.cpp llama.cpp)

find_library(log-lib log)

add_library(llama_jni SHARED llama_jni.cpp grammar_sampler.cpp chat_prompt.cpp)
target_include_directories(llama_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../../llama.cpp/include)
target_link_libraries(llama_jni PUBLIC llama llama-common ${log-lib})
