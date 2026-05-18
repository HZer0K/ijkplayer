#!/bin/bash
#
# run_tests.sh - 编译并运行 IJKPlayer AI 框架单元测试
#
# 用法: cd ijkmedia/ijkai/test && chmod +x run_tests.sh && ./run_tests.sh
#
# 测试种类:
#   1. algo       - 算法测试 (IoU, NMS, RGBA↔NCHW) - 纯数学无依赖
#   2. queue      - 异步队列测试 (FIFO, 优先级, 多线程)
#   3. core       - 核心框架测试 (init/release, 路由, 多模态, 统计)
#   4. pipenode   - Pipenode 测试 (create/destroy, run_sync, flush)
#   5. all        - 全部 (默认)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AI_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TEST_DIR="$AI_DIR/test"
CC="${CC:-gcc}"
CFLAGS="-std=c99 -Wall -Werror -Wextra -Wno-unused-parameter -Wno-unused-function -Wno-sign-compare -pthread"
PASS=0
FAIL=0
FAILED_TESTS=""

# 编译选项:
# 对于需要桩模块的源文件, 预-include test_stubs.h (定义 LLM/CV/FFPipenode 类型)
STUB_INCLUDE="-include $TEST_DIR/test_stubs.h"

build_and_run() {
    local name="$1"
    local main_src="$2"
    local extra_objs="$3"
    local extra_flags="$4"
    local binary="$TEST_DIR/$name"

    echo ""
    echo "=========================================="
    echo "  构建 $name ..."
    echo "=========================================="

    rm -f "$binary"

    local cmd="$CC $CFLAGS $extra_flags -I$AI_DIR -I$AI_DIR/async -I$TEST_DIR -o \"$binary\" \"$main_src\" $extra_objs -lm"
    echo "  $cmd"
    eval "$cmd" 2>&1

    if [ $? -ne 0 ]; then
        echo ""
        echo "  ❌ 编译失败: $name"
        FAIL=$((FAIL + 1))
        FAILED_TESTS="$FAILED_TESTS $name(build_fail)"
        return 1
    fi

    echo ""
    echo "  运行 $name ..."
    echo ""
    
    if "$binary"; then
        PASS=$((PASS + 1))
        echo ""
        echo "  ✅ 测试通过: $name"
    else
        FAIL=$((FAIL + 1))
        FAILED_TESTS="$FAILED_TESTS $name"
        echo ""
        echo "  ❌ 测试失败: $name"
    fi

    return 0
}

echo ""
echo "=========================================="
echo "  IJKPlayer AI 框架 - 单元测试套件"
echo "  编译器: $CC"
echo "  测试目录: $TEST_DIR"
echo "  AI 目录: $AI_DIR"
echo "=========================================="

# === 1. 算法测试 (无外部依赖) ===
build_and_run "test_ijkai_algo" \
    "$TEST_DIR/test_ijkai_algo.c" \
    "" \
    ""

# === 2. 队列测试 ===
build_and_run "test_ijkai_queue" \
    "$TEST_DIR/test_ijkai_queue.c" \
    "$AI_DIR/async/ijkai_queue.c" \
    ""

# === 3. 核心框架测试 ===
# 编译策略: ijkai.c 包含真实内部头文件(llm/ijkai_cv), 使用 -include 注入桩
# 但 test_stubs.c 不能被 -include 影响, 所以用分步编译
build_and_run_core() {
    echo ""
    echo "=========================================="
    echo "  构建 test_ijkai_core ..."
    echo "=========================================="
    
    rm -f "$TEST_DIR/test_ijkai_core"
    rm -f "$TEST_DIR/test_ijkai_core_main.o"
    rm -f "$TEST_DIR/test_stubs_core.o"
    
    # 1. 编译主测试文件 (需要 -include 桩)
    local cmd1="$CC $CFLAGS $STUB_INCLUDE -I$AI_DIR -I$AI_DIR/async -I$TEST_DIR -c \"$TEST_DIR/test_ijkai_core.c\" -o \"$TEST_DIR/test_ijkai_core_main.o\""
    echo "  $cmd1"
    eval "$cmd1" 2>&1 || { echo "  ❌ 编译失败: test_ijkai_core"; FAIL=$((FAIL + 1)); FAILED_TESTS="$FAILED_TESTS test_ijkai_core(build_fail)"; return 1; }
    
    # 2. 编译 stubs (不包含 -include, 否则类型双重定义)
    local cmd2="$CC $CFLAGS -I$AI_DIR -I$TEST_DIR -c \"$TEST_DIR/test_stubs.c\" -o \"$TEST_DIR/test_stubs_core.o\""
    echo "  $cmd2"
    eval "$cmd2" 2>&1 || { echo "  ❌ 编译失败: test_stubs_core"; FAIL=$((FAIL + 1)); FAILED_TESTS="$FAILED_TESTS test_ijkai_core(build_fail)"; return 1; }
    
    # 3. 编译 queue (不包含 -include)
    local cmd3="$CC $CFLAGS -I$AI_DIR -I$AI_DIR/async -c \"$AI_DIR/async/ijkai_queue.c\" -o \"$TEST_DIR/ijkai_queue_core.o\""
    echo "  $cmd3"
    eval "$cmd3" 2>&1 || { echo "  ❌ 编译失败: ijkai_queue"; FAIL=$((FAIL + 1)); FAILED_TESTS="$FAILED_TESTS test_ijkai_core(build_fail)"; return 1; }
    
    # 4. 链接
    local cmd4="$CC $CFLAGS -o \"$TEST_DIR/test_ijkai_core\" \"$TEST_DIR/test_ijkai_core_main.o\" \"$TEST_DIR/test_stubs_core.o\" \"$TEST_DIR/ijkai_queue_core.o\" -lm"
    echo "  $cmd4"
    eval "$cmd4" 2>&1 || { echo "  ❌ 链接失败: test_ijkai_core"; FAIL=$((FAIL + 1)); FAILED_TESTS="$FAILED_TESTS test_ijkai_core(link_fail)"; return 1; }
    
    # 运行
    echo ""
    echo "  运行 test_ijkai_core ..."
    echo ""
    if "$TEST_DIR/test_ijkai_core"; then
        PASS=$((PASS + 1))
        echo ""
        echo "  ✅ 测试通过: test_ijkai_core"
    else
        FAIL=$((FAIL + 1))
        FAILED_TESTS="$FAILED_TESTS test_ijkai_core"
        echo ""
        echo "  ❌ 测试失败: test_ijkai_core"
    fi
    
    # 清理
    rm -f "$TEST_DIR/test_ijkai_core_main.o" "$TEST_DIR/test_stubs_core.o" "$TEST_DIR/ijkai_queue_core.o"
}

build_and_run_core

# === 4. Pipenode 测试 ===
build_and_run_pipenode() {
    echo ""
    echo "=========================================="
    echo "  构建 test_ijkai_pipenode ..."
    echo "=========================================="
    
    rm -f "$TEST_DIR/test_ijkai_pipenode"
    rm -f "$TEST_DIR/test_ijkai_pipenode_main.o"
    rm -f "$TEST_DIR/test_stubs_pn.o"
    rm -f "$TEST_DIR/ijkai_queue_pn.o"
    rm -f "$TEST_DIR/ijkai_pn.o"
    rm -f "$TEST_DIR/ijkai_pipenode_pn.o"
    
    # 1. 编译主测试文件
    local cmd1="$CC $CFLAGS $STUB_INCLUDE -I$AI_DIR -I$AI_DIR/async -I$AI_DIR/.. -c \"$TEST_DIR/test_ijkai_pipenode.c\" -o \"$TEST_DIR/test_ijkai_pipenode_main.o\""
    echo "  $cmd1"
    eval "$cmd1" 2>&1 || { echo "  ❌ 编译失败: test_ijkai_pipenode_main"; FAIL=$((FAIL + 1)); FAILED_TESTS="$FAILED_TESTS test_ijkai_pipenode(build_fail)"; return 1; }
    
    # 2. 编译 ijkai.c (需要 -include 桩) - 同时需要 -I 来查找 ff_ffpipenode.h
    local cmd2="$CC $CFLAGS $STUB_INCLUDE -I$AI_DIR -I$AI_DIR/async -I$AI_DIR/.. -c \"$AI_DIR/ijkai.c\" -o \"$TEST_DIR/ijkai_pn.o\""
    echo "  $cmd2"
    eval "$cmd2" 2>&1 || { echo "  ❌ 编译失败: ijkai.c"; FAIL=$((FAIL + 1)); FAILED_TESTS="$FAILED_TESTS test_ijkai_pipenode(build_fail)"; return 1; }
    
    # 3. 编译 ijkai_pipenode.c (需要 -include 桩 + -I 来查找 ff_ffpipenode.h)
    local cmd3="$CC $CFLAGS $STUB_INCLUDE -I$AI_DIR -I$AI_DIR/async -I$AI_DIR/.. -c \"$AI_DIR/ijkai_pipenode.c\" -o \"$TEST_DIR/ijkai_pipenode_pn.o\""
    echo "  $cmd3"
    eval "$cmd3" 2>&1 || { echo "  ❌ 编译失败: ijkai_pipenode.c"; FAIL=$((FAIL + 1)); FAILED_TESTS="$FAILED_TESTS test_ijkai_pipenode(build_fail)"; return 1; }
    
    # 4. 编译 stubs (不包含 -include)
    local cmd4="$CC $CFLAGS -I$AI_DIR -I$AI_DIR/async -I$AI_DIR/.. -c \"$TEST_DIR/test_stubs.c\" -o \"$TEST_DIR/test_stubs_pn.o\""
    echo "  $cmd4"
    eval "$cmd4" 2>&1 || { echo "  ❌ 编译失败: test_stubs_pn"; FAIL=$((FAIL + 1)); FAILED_TESTS="$FAILED_TESTS test_ijkai_pipenode(build_fail)"; return 1; }
    
    # 5. 编译 queue (不包含 -include)
    local cmd5="$CC $CFLAGS -I$AI_DIR -I$AI_DIR/async -c \"$AI_DIR/async/ijkai_queue.c\" -o \"$TEST_DIR/ijkai_queue_pn.o\""
    echo "  $cmd5"
    eval "$cmd5" 2>&1 || { echo "  ❌ 编译失败: ijkai_queue"; FAIL=$((FAIL + 1)); FAILED_TESTS="$FAILED_TESTS test_ijkai_pipenode(build_fail)"; return 1; }
    
    # 6. 链接
    local cmd6="$CC $CFLAGS -o \"$TEST_DIR/test_ijkai_pipenode\" \"$TEST_DIR/test_ijkai_pipenode_main.o\" \"$TEST_DIR/ijkai_pn.o\" \"$TEST_DIR/ijkai_pipenode_pn.o\" \"$TEST_DIR/test_stubs_pn.o\" \"$TEST_DIR/ijkai_queue_pn.o\" -lm"
    echo "  $cmd6"
    eval "$cmd6" 2>&1 || { echo "  ❌ 链接失败: test_ijkai_pipenode"; FAIL=$((FAIL + 1)); FAILED_TESTS="$FAILED_TESTS test_ijkai_pipenode(link_fail)"; return 1; }
    
    # 运行
    echo ""
    echo "  运行 test_ijkai_pipenode ..."
    echo ""
    if "$TEST_DIR/test_ijkai_pipenode"; then
        PASS=$((PASS + 1))
        echo ""
        echo "  ✅ 测试通过: test_ijkai_pipenode"
    else
        FAIL=$((FAIL + 1))
        FAILED_TESTS="$FAILED_TESTS test_ijkai_pipenode"
        echo ""
        echo "  ❌ 测试失败: test_ijkai_pipenode"
    fi
    
    # 清理 .o 文件
    rm -f "$TEST_DIR/test_ijkai_pipenode_main.o" "$TEST_DIR/ijkai_pn.o" "$TEST_DIR/ijkai_pipenode_pn.o"
    rm -f "$TEST_DIR/test_stubs_pn.o" "$TEST_DIR/ijkai_queue_pn.o"
}

build_and_run_pipenode

# === 汇总 ===
echo ""
echo "=========================================="
echo "  测试完成"
echo "=========================================="
echo "  通过: $PASS"
echo "  失败: $FAIL"
echo ""

if [ $FAIL -gt 0 ]; then
    echo "  失败项:$FAILED_TESTS"
    echo ""
    exit 1
else
    echo "  🎉 全部测试通过!"
    echo ""
    exit 0
fi
