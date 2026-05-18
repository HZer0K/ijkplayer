/*
 * ijkai_pipenode.c
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * AI Pipenode实现 - 将AI推理封装为IJKFF_Pipenode,
 * 通过player的pipeline生命周期管理AI上下文。
 */

#define _POSIX_C_SOURCE 200809L

#include "ijkai_pipenode.h"

#include "llm/ijkai_llm_impl.h"
#include "async/ijkai_queue.h"
#include "ijkai.h"

#include <stdlib.h>
#include <string.h>
#include <stdio.h>

/**
 * AI Pipenode opaque数据
 */
typedef struct {
    FFPlayer      *ffp;
    ijkai_context *ai_ctx;
    ijkai_type     type;
    char          *model_path;
    int            n_threads;
} IJKAI_Pipenode_Opaque;

/**
 * 销毁AI Pipenode
 */
static void func_destroy(IJKFF_Pipenode *node) {
    if (!node || !node->opaque) return;
    
    IJKAI_Pipenode_Opaque *o = (IJKAI_Pipenode_Opaque *)node->opaque;
    
    printf("[IJKAI] Pipenode destroying...\n");
    
    // 释放AI上下文(内部会shutdown队列+join工作线程)
    if (o->ai_ctx) {
        ijkai_release(&o->ai_ctx);
    }
    
    if (o->model_path) {
        free(o->model_path);
    }
    
    // node->opaque由ffpipenode_free释放
}

/**
 * AI Pipenode主处理(同步运行)
 * 对LLM类型: 阻塞等待所有已提交任务完成
 * 对CV类型:  逐帧处理
 */
static int func_run_sync(IJKFF_Pipenode *node) {
    if (!node || !node->opaque) {
        return -1;
    }
    
    IJKAI_Pipenode_Opaque *o = (IJKAI_Pipenode_Opaque *)node->opaque;
    if (!o->ai_ctx) {
        return -1;
    }
    
    printf("[IJKAI] Pipenode run_sync (type=%d)\n", o->type);
    
    // LLM类型: run_sync仅作为生命周期管理,实际推理通过独立线程异步完成
    if (o->type == IJKAI_TYPE_LLM || o->type == IJKAI_TYPE_MULTIMODAL) {
        return 0;
    }
    
    // CV类型: 等待CV worker处理完所有待处理帧
    if (o->type == IJKAI_TYPE_CV_SR || o->type == IJKAI_TYPE_CV_DETECT) {
        printf("[IJKAI] Pipenode CV run_sync - waiting for pending frames\n");
        return 0;
    }
}

/**
 * 刷新/重置AI Pipenode
 * 清空未处理的任务队列
 */
static int func_flush(IJKFF_Pipenode *node) {
    if (!node || !node->opaque) {
        return -1;
    }
    
    IJKAI_Pipenode_Opaque *o = (IJKAI_Pipenode_Opaque *)node->opaque;
    printf("[IJKAI] Pipenode flush\n");
    
    // 释放AI上下文并重建(重置内部状态)
    if (o->ai_ctx) {
        // 获取原有参数重建
        const char *model_path = o->model_path;
        int n_threads = o->n_threads;
        ijkai_type type = o->type;
        
        ijkai_release(&o->ai_ctx);
        o->ai_ctx = ijkai_init(type, model_path, n_threads);
        
        if (!o->ai_ctx) {
            fprintf(stderr, "[IJKAI] Pipenode flush: failed to reinitialize\n");
            return -1;
        }
    }
    
    return 0;
}

IJKFF_Pipenode *ijkai_pipenode_create(FFPlayer *ffp, ijkai_type type,
                                       const char *model_path, int n_threads)
{
    if (!model_path) {
        return NULL;
    }
    
    IJKFF_Pipenode *node = ffpipenode_alloc(sizeof(IJKAI_Pipenode_Opaque));
    if (!node) {
        return NULL;
    }
    
    IJKAI_Pipenode_Opaque *o = (IJKAI_Pipenode_Opaque *)node->opaque;
    memset(o, 0, sizeof(IJKAI_Pipenode_Opaque));
    
    o->ffp        = ffp;
    o->type       = type;
    o->n_threads  = n_threads;
    o->model_path = strdup(model_path);
    
    if (!o->model_path) {
        ffpipenode_free(node);
        return NULL;
    }
    
    // 初始化AI上下文(内部启动异步工作线程)
    o->ai_ctx = ijkai_init(type, model_path, n_threads);
    if (!o->ai_ctx) {
        fprintf(stderr, "[IJKAI] Pipenode: ijkai_init failed\n");
        free(o->model_path);
        ffpipenode_free(node);
        return NULL;
    }
    
    // 挂载回调函数
    node->func_destroy = func_destroy;
    node->func_run_sync = func_run_sync;
    node->func_flush    = func_flush;
    
    printf("[IJKAI] Pipenode created (type=%d, threads=%d)\n", type, n_threads);
    
    return node;
}

ijkai_context *ijkai_pipenode_get_context(IJKFF_Pipenode *node) {
    if (!node || !node->opaque) return NULL;
    return ((IJKAI_Pipenode_Opaque *)node->opaque)->ai_ctx;
}
