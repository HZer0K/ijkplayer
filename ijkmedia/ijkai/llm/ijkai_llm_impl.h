/*
 * ijkai_llm_impl.h
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * This file is part of ijkPlayer AI Framework.
 */

#ifndef IJKAI_LLM_IMPL_H
#define IJKAI_LLM_IMPL_H

#include "../ijkai.h"
#include <pthread.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * LLM上下文结构(内部使用)
 */
typedef struct ijkai_llm_context ijkai_llm_context;

/**
 * LLM任务数据(内部使用)
 */
typedef struct {
    ijkai_llm_context *ctx;
    char *prompt;
    ijkai_llm_callback callback;
    void *user_data;
    int max_tokens;
    
    // 多模态数据(当有图像时使用)
    uint8_t *image_data;
    int image_width;
    int image_height;
} llm_task_data;

/**
 * 初始化LLM上下文
 * @param model_path 模型路径
 * @param n_threads 线程数
 * @return LLM上下文,失败返回NULL
 */
ijkai_llm_context *ijkai_llm_init_impl(const char *model_path, int n_threads);

/**
 * 释放LLM上下文
 * @param ctx LLM上下文
 */
void ijkai_llm_release_impl(ijkai_llm_context *ctx);

/**
 * 获取LLM上下文中的token计数
 */
int ijkai_llm_get_token_count_impl(ijkai_llm_context *ctx);

/**
 * 获取LLM累计推理时间(毫秒)
 */
int64_t ijkai_llm_get_eval_time_impl(ijkai_llm_context *ctx);

/**
 * LLM推理工作线程
 * @param arg 任务数据
 * @return NULL
 */
void *ijkai_llm_worker_thread(void *arg);

#ifdef __cplusplus
}
#endif

#endif // IJKAI_LLM_IMPL_H
