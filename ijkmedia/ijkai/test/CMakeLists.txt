#
# 测试目录 CMakeLists.txt
# 仅在桌面环境下启用 BUILD_TESTING 时编译
#
# 用法:
#   mkdir -p build && cd build
#   cmake .. -DBUILD_TESTING=ON
#   make
#   ctest
#

cmake_minimum_required(VERSION 3.10)

option(BUILD_TESTING "Build AI framework unit tests" OFF)

if(NOT BUILD_TESTING)
    return()
endif()

project(ijkai_tests C)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

find_package(Threads REQUIRED)

# 测试源文件目录
set(TEST_DIR ${CMAKE_CURRENT_SOURCE_DIR})
set(SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/..)

# 编译选项 - 用于覆盖内部头文件
set(STUB_INCLUDES
    -I${TEST_DIR}           # 让 test_stubs.h 优先于真实头文件
    -include ${TEST_DIR}/test_stubs.h
)

# 共享的桩模块
add_library(test_stubs OBJECT ${TEST_DIR}/test_stubs.c)
target_include_directories(test_stubs PRIVATE ${SRC_DIR})

# ========== 测试目标 ==========

# 1. 算法测试 (无外部依赖)
add_executable(test_ijkai_algo ${TEST_DIR}/test_ijkai_algo.c)
target_link_libraries(test_ijkai_algo m)
add_test(NAME algo COMMAND test_ijkai_algo)

# 2. 队列测试 (仅依赖 ijkai_queue)
add_executable(test_ijkai_queue ${TEST_DIR}/test_ijkai_queue.c ${SRC_DIR}/async/ijkai_queue.c)
target_include_directories(test_ijkai_queue PRIVATE ${SRC_DIR} ${SRC_DIR}/async)
target_link_libraries(test_ijkai_queue Threads::Threads)
add_test(NAME queue COMMAND test_ijkai_queue)

# 3. 核心框架测试 (使用桩)
add_executable(test_ijkai_core
    ${TEST_DIR}/test_ijkai_core.c
    $<TARGET_OBJECTS:test_stubs>
    ${SRC_DIR}/async/ijkai_queue.c
)
target_compile_options(test_ijkai_core PRIVATE ${STUB_INCLUDES})
target_include_directories(test_ijkai_core PRIVATE ${SRC_DIR} ${SRC_DIR}/async)
target_link_libraries(test_ijkai_core Threads::Threads)
add_test(NAME core COMMAND test_ijkai_core)

# 4. Pipenode 测试 (使用桩)
add_executable(test_ijkai_pipenode
    ${TEST_DIR}/test_ijkai_pipenode.c
    ${SRC_DIR}/ijkai.c
    ${SRC_DIR}/ijkai_pipenode.c
    ${TEST_DIR}/test_stubs.c
    ${SRC_DIR}/async/ijkai_queue.c
)
target_compile_options(test_ijkai_pipenode PRIVATE -include ${TEST_DIR}/test_stubs.h)
target_include_directories(test_ijkai_pipenode PRIVATE ${SRC_DIR} ${SRC_DIR}/async)
target_link_libraries(test_ijkai_pipenode Threads::Threads)
add_test(NAME pipenode COMMAND test_ijkai_pipenode)
