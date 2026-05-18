/*
 * ijkai_cv.c
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * CV module core implementation.
 * Manages MNN inference sessions and dispatches tasks to worker thread.
 */

#define _POSIX_C_SOURCE 200809L

#include "ijkai_cv_internal.h"
#include "ijkai_cv_sr.h"
#include "ijkai_cv_detect.h"

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <time.h>

/**
 * CV worker thread: processes tasks from the queue
 */
static void *ijkai_cv_worker_loop(void *arg) {
    ijkai_cv_context *ctx = (ijkai_cv_context *)arg;
    if (!ctx) return NULL;

    printf("[IJKAI_CV] Worker thread started\n");

    while (ctx->running) {
        ijkai_task task;
        int ret = ijkai_queue_pop(ctx->queue, &task, 500);
        if (ret != 0) {
            continue;
        }

        cv_task_data *data = (cv_task_data *)task.task_data;
        if (!data) continue;

        uint8_t *output_data = NULL;
        int out_w = 0, out_h = 0;
        int success = 1;

        if (data->sub_type == IJKAI_CV_SUPER_RESOLUTION) {
            // Super-resolution processing
            int sr_ret = ijkai_cv_sr_process_internal(
                ctx->mnn_ctx,
                data->input_data, data->in_width, data->in_height,
                data->scale_factor,
                &output_data, &out_w, &out_h);
            if (sr_ret != 0 || !output_data) {
                success = 0;
            }
        } else if (data->sub_type == IJKAI_CV_DETECTION) {
            // Detection processing
            int det_ret = ijkai_cv_detect_process_internal(
                ctx->mnn_ctx,
                data->input_data, data->in_width, data->in_height,
                &output_data, &out_w, &out_h);
            if (det_ret != 0 || !output_data) {
                success = 0;
            }
        } else {
            success = 0;
        }

        // Invoke callback
        if (data->callback) {
            data->callback(output_data, out_w, out_h, success, data->user_data);
        }

        // Free allocated resources
        if (data->input_data) free(data->input_data);
        if (output_data && output_data != data->input_data) free(output_data);
        free(data);

        ctx->processed_frames++;
    }

    printf("[IJKAI_CV] Worker thread stopped\n");
    return NULL;
}

ijkai_cv_context *ijkai_cv_init(ijkai_type type, const char *model_path,
                                int n_threads, ijkai_cv_backend backend) {
    (void)type;

    if (!model_path) {
        fprintf(stderr, "[IJKAI_CV] Error: model_path is NULL\n");
        return NULL;
    }

    ijkai_cv_context *ctx = (ijkai_cv_context *)calloc(1, sizeof(ijkai_cv_context));
    if (!ctx) return NULL;

    ctx->model_path = strdup(model_path);
    ctx->n_threads = (n_threads > 0) ? n_threads : 4;
    ctx->backend = (int)backend;
    ctx->running = 1;

    // Initialize MNN inference context
    ctx->mnn_ctx = mnn_init(model_path, ctx->backend, ctx->n_threads);
    if (!ctx->mnn_ctx) {
        fprintf(stderr, "[IJKAI_CV] Failed to initialize MNN\n");
        free(ctx->model_path);
        free(ctx);
        return NULL;
    }

    // Create task queue
    ctx->queue = ijkai_queue_create(8);
    if (!ctx->queue) {
        mnn_release(ctx->mnn_ctx);
        free(ctx->model_path);
        free(ctx);
        return NULL;
    }

    // Start worker thread
    pthread_create(&ctx->worker_thread, NULL, ijkai_cv_worker_loop, ctx);

    printf("[IJKAI_CV] Initialized: model=%s, threads=%d, backend=%d\n",
           model_path, ctx->n_threads, ctx->backend);

    return ctx;
}

void ijkai_cv_release(ijkai_cv_context *ctx) {
    if (!ctx) return;

    printf("[IJKAI_CV] Releasing...\n");

    // Stop worker thread
    ctx->running = 0;
    if (ctx->queue) {
        ijkai_queue_shutdown(ctx->queue);
    }
    pthread_join(ctx->worker_thread, NULL);

    // Release MNN
    if (ctx->mnn_ctx) {
        mnn_release(ctx->mnn_ctx);
        ctx->mnn_ctx = NULL;
    }

    // Release queue
    if (ctx->queue) {
        ijkai_queue_release(ctx->queue);
        ctx->queue = NULL;
    }

    if (ctx->model_path) {
        free(ctx->model_path);
    }

    free(ctx);
    printf("[IJKAI_CV] Released\n");
}

int ijkai_cv_ctx_set_backend(ijkai_cv_context *ctx, int backend) {
    if (!ctx) return -1;
    ctx->backend = backend;
    printf("[IJKAI_CV] Backend set to %d (effective on next model reload)\n", backend);
    // Note: actual backend change requires model reinitialization
    return 0;
}

static int push_cv_task(ijkai_cv_context *ctx, cv_task_data *data) {
    ijkai_task task;
    task.type = IJKAI_TASK_CV;
    task.priority = IJKAI_PRIORITY_NORMAL;
    task.timestamp = 0; // auto-set by queue
    task.task_data = data;

    return ijkai_queue_push(ctx->queue, &task);
}

int ijkai_cv_sr_process(ijkai_cv_context *ctx,
                        uint8_t *input_data, int in_width, int in_height,
                        float scale_factor,
                        ijkai_cv_callback callback, void *user_data) {
    if (!ctx || !input_data || in_width <= 0 || in_height <= 0) {
        return -1;
    }

    cv_task_data *data = (cv_task_data *)malloc(sizeof(cv_task_data));
    if (!data) return -1;

    data->ctx = ctx;
    data->input_data = (uint8_t *)malloc((size_t)(in_width * in_height * 4));
    if (!data->input_data) {
        free(data);
        return -1;
    }
    memcpy(data->input_data, input_data, (size_t)(in_width * in_height * 4));

    data->in_width    = in_width;
    data->in_height   = in_height;
    data->in_channels = 3; // RGB, skip alpha for model input
    data->out_width   = (int)(in_width * scale_factor);
    data->out_height  = (int)(in_height * scale_factor);
    data->scale_factor = scale_factor;
    data->callback    = callback;
    data->user_data   = user_data;
    data->sub_type    = IJKAI_CV_SUPER_RESOLUTION;

    return push_cv_task(ctx, data);
}

int ijkai_cv_detect_process(ijkai_cv_context *ctx,
                            uint8_t *input_data, int in_width, int in_height,
                            ijkai_cv_callback callback, void *user_data) {
    if (!ctx || !input_data || in_width <= 0 || in_height <= 0) {
        return -1;
    }

    cv_task_data *data = (cv_task_data *)malloc(sizeof(cv_task_data));
    if (!data) return -1;

    data->ctx = ctx;
    data->input_data = (uint8_t *)malloc((size_t)(in_width * in_height * 4));
    if (!data->input_data) {
        free(data);
        return -1;
    }
    memcpy(data->input_data, input_data, (size_t)(in_width * in_height * 4));

    data->in_width    = in_width;
    data->in_height   = in_height;
    data->in_channels = 3;
    data->out_width   = 0;  // will be set by detection result
    data->out_height  = 0;
    data->scale_factor = 1.0f;
    data->callback    = callback;
    data->user_data   = user_data;
    data->sub_type    = IJKAI_CV_DETECTION;

    return push_cv_task(ctx, data);
}

int ijkai_cv_get_processed_frames(ijkai_cv_context *ctx) {
    if (!ctx) return 0;
    return ctx->processed_frames;
}

int64_t ijkai_cv_get_eval_time(ijkai_cv_context *ctx) {
    if (!ctx) return 0;
    return ctx->eval_time_ms;
}
