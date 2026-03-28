cmake_minimum_required(VERSION 3.22.1)
project(asr_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(asrwhisper SHARED)

if(ENABLE_WHISPER)
    include(FetchContent)

    FetchContent_Declare(
            whispercpp
            URL https://github.com/ggerganov/whisper.cpp/archive/refs/tags/v1.7.4.tar.gz
    )

    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_OPENVINO OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_CURL OFF CACHE BOOL "" FORCE)

    FetchContent_MakeAvailable(whispercpp)

    target_sources(asrwhisper PRIVATE asrwhisper_jni.cpp)
    target_include_directories(asrwhisper PRIVATE
            ${whispercpp_SOURCE_DIR}
            ${whispercpp_SOURCE_DIR}/include
    )

    if(TARGET whisper)
        target_link_libraries(asrwhisper PRIVATE whisper)
    elseif(TARGET libwhisper)
        target_link_libraries(asrwhisper PRIVATE libwhisper)
    endif()
else()
    target_sources(asrwhisper PRIVATE asrwhisper_stub.cpp)
endif()

find_library(log-lib log)
target_link_libraries(asrwhisper PRIVATE ${log-lib})
