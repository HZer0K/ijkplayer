/*
 * test_stubs.c - 桩实现: LLM/CV/FFPipenode 模块
 *
 * 为桌面端测试提供 ijkai.c, ijkai_pipenode.c 的依赖
 */

#define _POSIX_C_SOURCE 200809L

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include "../ijkai.h"

/* ========== LLM Stub 类型 ==========
 * NOTE: ijkai_llm_callback is in ijkai.h
 *       ijkai_cv_backend, ijkai_cv_callback are in ijkai.h */

typedef struct {
    int dummy;
} ijkai_llm_context;

typedef struct {
    void *ctx;
    char *prompt;
    void (*callback)(const char *text, bool is_complete, void *user_data);
    void *user_data;
    int max_tokens;
    uint8_t *image_data;
    int image_width;
    int image_height;
} llm_task_data;

ijkai_llm_context *ijkai_llm_init_impl(const char *model_path, int n_threads) {
    (void)model_path; (void)n_threads;
    ijkai_llm_context *ctx = (ijkai_llm_context*)calloc(1, sizeof(ijkai_llm_context));
    printf("[STUB] ijkai_llm_init_impl(%s, %d) = %p\n", model_path, n_threads, (void*)ctx);
    return ctx;
}

void ijkai_llm_release_impl(ijkai_llm_context *ctx) {
    printf("[STUB] ijkai_llm_release_impl(%p)\n", (void*)ctx);
    free(ctx);
}

void ijkai_llm_worker_thread(void *data) {
    llm_task_data *td = (llm_task_data*)data;
    printf("[STUB] ijkai_llm_worker_thread: prompt=%s\n", td->prompt ? td->prompt : "(null)");
    // 模拟推理: 调用回调
    if (td->callback) {
        td->callback("Hello from stub LLM!", false, td->user_data);
        td->callback("", true, td->user_data);
    }
    // 释放资源
    if (td->prompt) free(td->prompt);
    if (td->image_data) free(td->image_data);
    free(td);
}

int64_t ijkai_llm_get_eval_time_impl(ijkai_llm_context *ctx) {
    (void)ctx;
    return 42; // 模拟42ms
}

int ijkai_llm_get_token_count_impl(ijkai_llm_context *ctx) {
    (void)ctx;
    return 128; // 模拟128 tokens
}

/* ========== CV Stub 类型 ==========
 * ijkai_cv_backend枚举和ijkai_cv_callback已在ijkai.h中定义 */

typedef struct {
    int backend;
    int n_threads;
    char model_path[256];
    int processed;
    int eval_time;
} ijkai_cv_context;

/* MNN context stub */
typedef struct { int dummy; } mnn_context;
int mnn_get_input_dims(mnn_context *ctx, int *n, int *c, int *w, int *h) {
    (void)ctx;
    *n = 1; *c = 3; *w = 640; *h = 640;
    return 0;
}

ijkai_cv_context *ijkai_cv_init(int type, const char *model_path, int n_threads, int backend) {
    (void)type;
    ijkai_cv_context *ctx = (ijkai_cv_context*)calloc(1, sizeof(ijkai_cv_context));
    ctx->backend = backend;
    ctx->n_threads = n_threads;
    strncpy(ctx->model_path, model_path, sizeof(ctx->model_path) - 1);
    printf("[STUB] ijkai_cv_init(type=%d, %s, %d threads, backend=%d) = %p\n",
           type, model_path, n_threads, backend, (void*)ctx);
    return ctx;
}

void ijkai_cv_release(ijkai_cv_context *ctx) {
    printf("[STUB] ijkai_cv_release(%p)\n", (void*)ctx);
    free(ctx);
}

int ijkai_cv_sr_process(ijkai_cv_context *ctx, uint8_t *input_data,
                         int in_w, int in_h, float scale,
                         ijkai_cv_callback callback, void *user_data) {
    (void)ctx; (void)input_data; (void)in_w; (void)in_h; (void)scale;
    printf("[STUB] ijkai_cv_sr_process(%dx%d, scale=%.1f)\n", in_w, in_h, scale);
    if (callback) {
        int out_w = (int)(in_w * scale);
        int out_h = (int)(in_h * scale);
        uint8_t *dummy = (uint8_t*)calloc((size_t)(out_w * out_h * 4), 1);
        callback(dummy, out_w, out_h, true, user_data);
        free(dummy);
    }
    return 0;
}

int ijkai_cv_detect_process(ijkai_cv_context *ctx, uint8_t *input_data,
                             int in_w, int in_h,
                             ijkai_cv_callback callback, void *user_data) {
    (void)ctx; (void)input_data; (void)in_w; (void)in_h;
    printf("[STUB] ijkai_cv_detect_process(%dx%d)\n", in_w, in_h);
    if (callback) {
        callback(NULL, 2, 0, true, user_data); // 2 detections
    }
    return 0;
}

int ijkai_cv_ctx_set_backend(ijkai_cv_context *ctx, int backend) {
    if (!ctx) return -1;
    ctx->backend = backend;
    printf("[STUB] ijkai_cv_ctx_set_backend(%d)\n", backend);
    return 0;
}

int ijkai_cv_get_processed_frames(ijkai_cv_context *ctx) {
    if (!ctx) return 0;
    return ctx->processed;
}

int64_t ijkai_cv_get_eval_time(ijkai_cv_context *ctx) {
    if (!ctx) return 0;
    return ctx->eval_time;
}

/* ========== FFPipenode Stub ========== */

typedef struct IJKFF_Pipenode {
    void *opaque;
    int (*func_run_sync)(struct IJKFF_Pipenode *node);
    int (*func_flush)(struct IJKFF_Pipenode *node);
    void (*func_destroy)(struct IJKFF_Pipenode *node);
} IJKFF_Pipenode;

typedef struct FFPlayer { int dummy; } FFPlayer;

IJKFF_Pipenode *ffpipenode_alloc(size_t opaque_size) {
    IJKFF_Pipenode *node = (IJKFF_Pipenode*)calloc(1, sizeof(IJKFF_Pipenode));
    if (!node) return NULL;
    node->opaque = calloc(1, opaque_size);
    if (!node->opaque) { free(node); return NULL; }
    printf("[STUB] ffpipenode_alloc(%zu) = %p\n", opaque_size, (void*)node);
    return node;
}

void ffpipenode_free(IJKFF_Pipenode *node) {
    if (!node) return;
    printf("[STUB] ffpipenode_free(%p)\n", (void*)node);
    if (node->opaque) {
        // 在 func_destroy 中释放 opaque 内容
        free(node->opaque);
    }
    free(node);
}
