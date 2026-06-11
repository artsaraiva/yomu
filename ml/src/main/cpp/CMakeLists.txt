cmake_minimum_required(VERSION 3.22.1)
project("yomu_ml")

add_library(llama_jni SHARED llama_jni.cpp)
find_library(log-lib log)
target_link_libraries(llama_jni ${log-lib})
