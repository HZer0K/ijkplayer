/*
 * test_stubs.h
 *
 * 桩模块声明 - 提供 LLM/CV/FFPipenode 的桩类型和函数声明
 * 
 * 使用方法:
 *   1. 在 test_*.c 中先 #define 对应头文件的 include guard
 *   2. 然后 #include "test_stubs.h"
 *   3. 最后 #include "../ijkai.c" (或 ../ijkai_pipenode.c)
 *   4. 链接 test_stubs.c
 */

#ifndef TEST_STUBS_H
#define TEST_STUBS_H

#define _POSIX_C_SOURCE 200809L

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <pthread.h>

/* ==================== LLM Stub ==================== */

#ifndef IJKAI_LLM_IMPL_H
#define IJKAI_LLM_IMPL_H

/* NOTE: ijkai_llm_callback is already defined in ijkai.h */

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

ijkai_llm_context *ijkai_llm_init_impl(const char *model_path, int n_threads);
void ijkai_llm_release_impl(ijkai_llm_context *ctx);
void ijkai_llm_worker_thread(void *data);
int64_t ijkai_llm_get_eval_time_impl(ijkai_llm_context *ctx);
int ijkai_llm_get_token_count_impl(ijkai_llm_context *ctx);

#endif /* IJKAI_LLM_IMPL_H */

/* ==================== CV Stub ==================== */

#ifndef IJKAI_CV_H
#define IJKAI_CV_H

/* NOTE: ijkai_cv_backend, ijkai_cv_callback are defined in ijkai.h */

/* 内部 CV context */
typedef struct {
    int backend;
    int n_threads;
    char model_path[256];
    int processed;
    int eval_time;
} ijkai_cv_context;

/* MNN context stub */
typedef struct { int dummy; } mnn_context;

int mnn_get_input_dims(mnn_context *ctx, int *n, int *c, int *w, int *h);
int mnn_get_output_dims(mnn_context *ctx, int *n, int *c, int *w, int *h);
int mnn_run(mnn_context *ctx, float *input, int n, int c, int w, int h,
            float *output, int output_size);

/* CV public API (给 ijkai.c 使用) */
ijkai_cv_context *ijkai_cv_init(int type, const char *model_path, int n_threads, int backend);
void ijkai_cv_release(ijkai_cv_context *ctx);
int ijkai_cv_sr_process(ijkai_cv_context *ctx, uint8_t *input_data,
                         int in_w, int in_h, float scale,
                         void (*callback)(uint8_t*,int,int,bool,void*), void *user_data);
int ijkai_cv_detect_process(ijkai_cv_context *ctx, uint8_t *input_data,
                             int in_w, int in_h,
                             void (*callback)(uint8_t*,int,int,bool,void*), void *user_data);
int ijkai_cv_ctx_set_backend(ijkai_cv_context *ctx, int backend);

/* 返回 CV context 统计 */
int ijkai_cv_get_processed_frames(ijkai_cv_context *ctx);
int64_t ijkai_cv_get_eval_time(ijkai_cv_context *ctx);

#endif /* IJKAI_CV_H */

/* ==================== FFPipenode Stub ==================== */

#ifndef FF_FFPIPENODE_H
#define FF_FFPIPENODE_H

typedef struct IJKFF_Pipenode {
    void *opaque;
    int (*func_run_sync)(struct IJKFF_Pipenode *node);
    int (*func_flush)(struct IJKFF_Pipenode *node);
    void (*func_destroy)(struct IJKFF_Pipenode *node);
} IJKFF_Pipenode;

typedef struct FFPlayer { int dummy; } FFPlayer;

IJKFF_Pipenode *ffpipenode_alloc(size_t opaque_size);
void ffpipenode_free(IJKFF_Pipenode *node);

#endif /* FF_FFPIPENODE_H */

/* 阻止真实 ff_ffpipenode.h 被加载(它使用不同的 include guard) */
#define FFPLAY__FF_FFPIPENODE_H

#endif /* TEST_STUBS_H */
