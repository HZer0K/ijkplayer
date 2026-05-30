#! /usr/bin/env bash
#
# module-ai.sh - AI 推理框架配置
#
# 使用方式:
#   source config/module-ai.sh
#   cd android/contrib && ./compile-llama.sh  # 使用此配置编译 llama.cpp
#
# 或者在 init-android-llama.sh 之前 source 此文件

#--------------------
# AI 框架总开关
export IJKAI_ENABLE_LLM=${IJKAI_ENABLE_LLM:-yes}    # LLM (llama.cpp) 支持
export IJKAI_ENABLE_CV=${IJKAI_ENABLE_CV:-yes}      # CV (MNN) 支持

#--------------------
# LLM (llama.cpp) 配置
export IJKAI_LLM_THREADS=${IJKAI_LLM_THREADS:-4}     # 推理线程数
export IJKAI_LLM_CTX_SIZE=${IJKAI_LLM_CTX_SIZE:-4096} # 上下文窗口大小

#--------------------
# CV (MNN) 配置
export IJKAI_CV_BACKEND=${IJKAI_CV_BACKEND:-cpu}      # cpu / opencl / vulkan / auto

#--------------------
# 异步队列配置
export IJKAI_QUEUE_SIZE=${IJKAI_QUEUE_SIZE:-30}       # 队列最大容量
export IJKAI_TASK_TIMEOUT_MS=${IJKAI_TASK_TIMEOUT_MS:-500}  # 任务超时(毫秒)

#--------------------
# 集成提示
if [ "$IJKAI_ENABLE_LLM" = "yes" ]; then
    echo "AI: LLM enabled (threads=$IJKAI_LLM_THREADS, ctx=$IJKAI_LLM_CTX_SIZE)"
fi
if [ "$IJKAI_ENABLE_CV" = "yes" ]; then
    echo "AI: CV enabled (backend=$IJKAI_CV_BACKEND)"
fi
