/*
 * ijkai.c
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * This file is part of ijkPlayer AI Framework.
 */

#define _POSIX_C_SOURCE 200809L

#include "ijkai.h"
#include "async/ijkai_queue.h"
#include "llm/ijkai_llm_impl.h"

#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <stdio.h>

/**
 * AI上下文结构(内部实现)
 */
struct ijkai_context {
    ijkai_type type;
    
    // 内部模块
    union {
        ijkai_llm_context *llm_ctx;
        void *cv_ctx;
    };
    
    // 异步队列
    ijkai_task_queue *queue;
    
    // 工作线程
    pthread_t worker_thread;
    volatile int running;
    
    // 统计
    int64_t eval_time_ms;
    int token_count;
    int processed_frames;
};

/**
 * 工作线程: 从队列取出任务并分发
 */
static void *ijkai_worker_loop(void *arg) {
    ijkai_context *ctx = (ijkai_context *)arg;
    if (!ctx) {
        return NULL;
    }
    
    printf("[IJKAI] Worker thread started\n");
    
    while (ctx->running) {
        ijkai_task task;
        int ret = ijkai_queue_pop(ctx->queue, &task, 500); // 500ms超时
        if (ret != 0) {
            continue; // 超时,继续检查running状态
        }
        
        // 处理任务
        if (task.type == IJKAI_TASK_LLM && task.task_data) {
            // LLM推理在工作线程中直接执行
            ijkai_llm_worker_thread(task.task_data);
            // 更新统计
            ctx->processed_frames++;
        }
        // TODO: CV任务处理
    }
    
    printf("[IJKAI] Worker thread stopped\n");
    return NULL;
}

ijkai_context *ijkai_init(ijkai_type type, const char *model_path, int n_threads) {
    if (!model_path) {
        return NULL;
    }
    
    ijkai_context *ctx = (ijkai_context *)calloc(1, sizeof(ijkai_context));
    if (!ctx) {
        return NULL;
    }
    
    ctx->type = type;
    ctx->eval_time_ms = 0;
    ctx->token_count = 0;
    ctx->processed_frames = 0;
    ctx->running = 1;
    
    // 创建异步队列(最多5个任务)
    ctx->queue = ijkai_queue_create(5);
    if (!ctx->queue) {
        free(ctx);
        return NULL;
    }
    
    // 根据类型初始化对应模块
    if (type == IJKAI_TYPE_LLM || type == IJKAI_TYPE_MULTIMODAL) {
        ctx->llm_ctx = ijkai_llm_init_impl(model_path, n_threads);
        if (!ctx->llm_ctx) {
            ijkai_queue_release(ctx->queue);
            free(ctx);
            return NULL;
        }
    }
    
    // 启动工作线程
    pthread_create(&ctx->worker_thread, NULL, ijkai_worker_loop, ctx);
    pthread_detach(ctx->worker_thread); // 分离线程,自动回收
    
    printf("[IJKAI] AI context initialized (type=%d)\n", type);
    
    return ctx;
}

int ijkai_llm_prompt(
    ijkai_context *ctx,
    const char *prompt,
    ijkai_llm_callback callback,
    void *user_data,
    int max_tokens
) {
    if (!ctx || ctx->type != IJKAI_TYPE_LLM || !prompt || !callback) {
        return -1;
    }
    
    ijkai_task task;
    task.type = IJKAI_TASK_LLM;
    task.timestamp = 0;
    
    llm_task_data *data = (llm_task_data *)malloc(sizeof(llm_task_data));
    if (!data) {
        return -1;
    }
    
    data->ctx = ctx->llm_ctx;
    data->prompt = strdup(prompt);
    data->callback = callback;
    data->user_data = user_data;
    data->max_tokens = max_tokens;
    
    task.task_data = data;
    
    // 推入队列(不阻塞主线程)
    return ijkai_queue_push(ctx->queue, &task);
}

int ijkai_cv_process(
    ijkai_context *ctx,
    uint8_t *input_data, int in_width, int in_height,
    int out_width, int out_height,
    ijkai_cv_callback callback,
    void *user_data
) {
    (void)ctx;
    (void)input_data;
    (void)in_width;
    (void)in_height;
    (void)out_width;
    (void)out_height;
    (void)callback;
    (void)user_data;
    return -1;
}

int ijkai_multimodal(
    ijkai_context *ctx,
    uint8_t *image_data, int width, int height,
    const char *question,
    ijkai_llm_callback callback,
    void *user_data
) {
    (void)ctx;
    (void)image_data;
    (void)width;
    (void)height;
    (void)question;
    (void)callback;
    (void)user_data;
    return -1;
}

int64_t ijkai_get_eval_time(ijkai_context *ctx) {
    if (!ctx) return 0;
    return ctx->eval_time_ms;
}

int ijkai_get_token_count(ijkai_context *ctx) {
    if (!ctx) return 0;
    if (ctx->llm_ctx) {
        return ijkai_llm_get_token_count_impl(ctx->llm_ctx);
    }
    return 0;
}

int ijkai_get_processed_frames(ijkai_context *ctx) {
    if (!ctx) return 0;
    return ctx->processed_frames;
}

void ijkai_release(ijkai_context **ctx) {
    if (!ctx || !*ctx) {
        return;
    }
    
    ijkai_context *c = *ctx;
    
    // 停止工作线程
    c->running = 0;
    
    // 释放对应模块
    if (c->type == IJKAI_TYPE_LLM || c->type == IJKAI_TYPE_MULTIMODAL) {
        if (c->llm_ctx) {
            ijkai_llm_release_impl(c->llm_ctx);
        }
    }
    
    // 释放队列
    if (c->queue) {
        ijkai_queue_release(c->queue);
    }
    
    free(c);
    *ctx = NULL;
    
    printf("[IJKAI] AI context released\n");
}
