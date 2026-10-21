cmake_minimum_required(VERSION 3.22.1)
project("yomu_ml")

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
# The CPU backend is a set of shared libraries, one per ARM feature level; llama_jni.cpp loads the
# best one the phone supports (#290). Backend DL requires shared libs.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
    # Spike #340: the OpenCL backend (Adreno kernels), built when scripts/spike-340-opencl-sdk.sh has
    # prepared ml/opencl-sdk. With GGML_BACKEND_DL it is its own libggml-opencl.so; on a phone without
    # libOpenCL.so its dlopen fails and the CPU variants carry on alone.
    set(YOMU_OPENCL_SDK ${CMAKE_CURRENT_SOURCE_DIR}/../../../opencl-sdk)
    if(EXISTS ${YOMU_OPENCL_SDK}/lib/libOpenCL.so)
        set(GGML_OPENCL ON CACHE BOOL "" FORCE)
        set(GGML_OPENCL_USE_ADRENO_KERNELS ON CACHE BOOL "" FORCE)
        set(GGML_OPENCL_EMBED_KERNELS ON CACHE BOOL "" FORCE)
        set(OpenCL_INCLUDE_DIR ${YOMU_OPENCL_SDK}/include CACHE PATH "" FORCE)
        set(OpenCL_LIBRARY ${YOMU_OPENCL_SDK}/lib/libOpenCL.so CACHE FILEPATH "" FORCE)
    endif()
endif()

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../llama.cpp llama.cpp)

find_library(log-lib log)

add_library(llama_jni SHARED llama_jni.cpp grammar_sampler.cpp chat_prompt.cpp)
target_include_directories(llama_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../../llama.cpp/include)
target_link_libraries(llama_jni PUBLIC llama llama-common ${log-lib})
