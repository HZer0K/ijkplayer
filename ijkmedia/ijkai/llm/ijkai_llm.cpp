/*
 * ijkai_llm.cpp
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * This file is part of ijkPlayer AI Framework.
 */

#include "ijkai_llm_impl.h"

#include "llama.h"

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include <vector>
#include <ctime>

/**
 * LLM上下文结构(内部实现)
 */
struct ijkai_llm_context {
    struct llama_model *model;
    struct llama_context *ctx;
    int n_threads;
    int n_ctx;
    
    // 统计
    int64_t eval_time_ms;
    int token_count;
};

ijkai_llm_context *ijkai_llm_init_impl(const char *model_path, int n_threads) {
    printf("[IJKAI] LLM init: model_path=%s, threads=%d\n", model_path, n_threads);
    
    // 初始化llama后端
    llama_backend_init();
    
    // 加载模型
    struct llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // 使用CPU推理
    
    struct llama_model *model = llama_load_model_from_file(model_path, model_params);
    if (!model) {
        fprintf(stderr, "[IJKAI] Failed to load model: %s\n", model_path);
        llama_backend_free();
        return NULL;
    }
    
    // 创建上下文
    int n_ctx = 4096;
    struct llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    
    struct llama_context *lctx = llama_new_context_with_model(model, ctx_params);
    if (!lctx) {
        fprintf(stderr, "[IJKAI] Failed to create llama context\n");
        llama_free_model(model);
        llama_backend_free();
        return NULL;
    }
    
    ijkai_llm_context *ctx = (ijkai_llm_context *)calloc(1, sizeof(ijkai_llm_context));
    if (!ctx) {
        llama_free(lctx);
        llama_free_model(model);
        llama_backend_free();
        return NULL;
    }
    
    ctx->model = model;
    ctx->ctx = lctx;
    ctx->n_threads = n_threads;
    ctx->n_ctx = n_ctx;
    ctx->eval_time_ms = 0;
    ctx->token_count = 0;
    
    printf("[IJKAI] LLM initialized successfully (n_ctx=%d, threads=%d)\n", n_ctx, n_threads);
    
    return ctx;
}

void ijkai_llm_release_impl(ijkai_llm_context *ctx) {
    if (!ctx) {
        return;
    }
    
    printf("[IJKAI] LLM releasing...\n");
    
    if (ctx->ctx) {
        llama_free(ctx->ctx);
    }
    if (ctx->model) {
        llama_free_model(ctx->model);
    }
    llama_backend_free();
    
    printf("[IJKAI] LLM released\n");
    
    free(ctx);
}

int ijkai_llm_get_token_count_impl(ijkai_llm_context *ctx) {
    if (!ctx) return 0;
    return ctx->token_count;
}

void *ijkai_llm_worker_thread(void *arg) {
    llm_task_data *task = (llm_task_data *)arg;
    if (!task || !task->ctx || !task->prompt || !task->callback) {
        if (task) {
            free(task->prompt);
            free(task);
        }
        return NULL;
    }
    
    ijkai_llm_context *lctx = task->ctx;
    struct llama_context *ctx = lctx->ctx;
    printf("[IJKAI] LLM worker: prompt=%s\n", task->prompt);
    
    // 1. Tokenize prompt
    const int n_prompt = task->max_tokens > 0 ? task->max_tokens : 512;
    (void)n_prompt; // 使用llama_tokenize接口
    
    int n_tokens = (int)strlen(task->prompt) + 4; // 估算最大token数
    std::vector<llama_token> tokens(n_tokens);
    
    n_tokens = llama_tokenize(
        llama_get_model(ctx),
        task->prompt,
        (int32_t)strlen(task->prompt),
        tokens.data(),
        (int32_t)tokens.size(),
        false,
        false
    );
    
    if (n_tokens < 0) {
        fprintf(stderr, "[IJKAI] Failed to tokenize prompt\n");
        task->callback("", true, task->user_data);
        free(task->prompt);
        free(task);
        return NULL;
    }
    
    tokens.resize(n_tokens);
    
    printf("[IJKAI] Tokenized %d tokens\n", n_tokens);
    
    // 2. 评估prompt
    int64_t start_time = (int64_t)time(NULL) * 1000;
    
    // 发送prompt到上下文
    for (int i = 0; i < n_tokens; i++) {
        llama_batch batch = llama_batch_get_one(&tokens[i], 1, i, 0);
        if (llama_decode(ctx, batch)) {
            fprintf(stderr, "[IJKAI] llama_decode failed\n");
            task->callback("", true, task->user_data);
            free(task->prompt);
            free(task);
            return NULL;
        }
    }
    
    // 3. 生成token
    int n_generated = 0;
    const int n_max_gen = task->max_tokens > 0 ? task->max_tokens : 256;
    
    std::vector<char> result_buf;
    result_buf.reserve(8192);
    
    while (n_generated < n_max_gen) {
        // 采样
        auto *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
        // Greedy温度采样（最简单的方式）
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
        
        const llama_token new_token_id = llama_sampler_sample(smpl, ctx, -1);
        llama_sampler_free(smpl);
        
        const struct llama_model *model = llama_get_model(ctx);
        if (new_token_id == llama_token_eos(model) || new_token_id == LLAMA_TOKEN_NULL) {
            break;
        }
        
        // 转换token为文本
        char piece[256] = {0};
        const struct llama_model *model_p = llama_get_model(ctx);
        int n_piece = llama_token_to_piece(model_p, new_token_id, piece, (int32_t)sizeof(piece), 0, false);
        if (n_piece < 0) {
            continue;
        }
        piece[n_piece] = '\0';
        
        // 添加到结果缓冲区
        result_buf.insert(result_buf.end(), piece, piece + n_piece);
        result_buf.push_back('\0');
        
        // 回调(流式输出)
        task->callback(piece, false, task->user_data);
        
        // 准备下一个token
        llama_token new_token_id_sampled = new_token_id;
        llama_batch batch = llama_batch_get_one(&new_token_id_sampled, 1, n_tokens + n_generated, 0);
        if (llama_decode(ctx, batch)) {
            fprintf(stderr, "[IJKAI] llama_decode failed during generation\n");
            break;
        }
        
        n_generated++;
    }
    
    int64_t end_time = (int64_t)time(NULL) * 1000;
    
    // 统计更新
    lctx->eval_time_ms += (end_time - start_time);
    lctx->token_count += n_generated;
    
    printf("[IJKAI] Generated %d tokens in %ld ms\n", n_generated, (end_time - start_time));
    
    // 完成信号
    task->callback("", true, task->user_data);
    
    // 清理
    free(task->prompt);
    free(task);
    
    return NULL;
}
