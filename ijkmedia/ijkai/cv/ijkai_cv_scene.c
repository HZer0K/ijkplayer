/*
 * ijkai_cv_scene.c
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * Scene classification implementation.
 * Preprocesses RGB frame -> NCHW float32 -> MNN inference -> argmax -> label.
 */

#define _POSIX_C_SOURCE 200809L

#include "ijkai_cv_scene.h"
#include "ijkai_cv_internal.h"

#include <stdlib.h>
#include <string.h>
#include <stdio.h>

/* ImageNet normalization constants (mean and std per channel) */
static const float IMAGENET_MEAN[3] = {0.485f, 0.456f, 0.406f};
static const float IMAGENET_STD[3]  = {0.229f, 0.224f, 0.225f};

/* Human-readable label names (Chinese) */
static const char *SCENE_LABELS[] = {
    "室内",     /* SCENE_INDOOR */
    "室外",     /* SCENE_OUTDOOR */
    "白天",     /* SCENE_DAYTIME */
    "夜晚",     /* SCENE_NIGHTTIME */
    "特写",     /* SCENE_CLOSEUP */
    "远景",     /* SCENE_WIDESHOT */
    "未知"      /* SCENE_UNKNOWN */
};

const char *ijkai_scene_label_text(ijkai_scene_label label) {
    if (label < 0 || label > SCENE_UNKNOWN)
        return SCENE_LABELS[SCENE_UNKNOWN];
    return SCENE_LABELS[label];
}

/**
 * Bilinear resize from (src_w, src_h) to (dst_w, dst_h) for RGB images.
 * Simple implementation suitable for small target sizes (224x224).
 */
static void rgb_bilinear_resize(const uint8_t *src, int src_w, int src_h,
                                uint8_t *dst, int dst_w, int dst_h) {
    for (int y = 0; y < dst_h; y++) {
        float src_y = (float)y * (float)src_h / (float)dst_h;
        int y0 = (int)src_y;
        int y1 = (y0 + 1 < src_h) ? y0 + 1 : y0;
        float fy = src_y - (float)y0;

        for (int x = 0; x < dst_w; x++) {
            float src_x = (float)x * (float)src_w / (float)dst_w;
            int x0 = (int)src_x;
            int x1 = (x0 + 1 < src_w) ? x0 + 1 : x0;
            float fx = src_x - (float)x0;

            for (int c = 0; c < 3; c++) {
                float v00 = src[(y0 * src_w + x0) * 3 + c];
                float v01 = src[(y0 * src_w + x1) * 3 + c];
                float v10 = src[(y1 * src_w + x0) * 3 + c];
                float v11 = src[(y1 * src_w + x1) * 3 + c];

                float val = v00 * (1 - fx) * (1 - fy)
                          + v01 * fx * (1 - fy)
                          + v10 * (1 - fx) * fy
                          + v11 * fx * fy;

                dst[(y * dst_w + x) * 3 + c] = (uint8_t)(val + 0.5f);
            }
        }
    }
}

/**
 * Convert RGB HWC uint8 to NCHW float32 with ImageNet normalization.
 * Input:  [H, W, 3] uint8 [0, 255]
 * Output: [3, H, W] float32, normalized with ImageNet mean/std
 */
static void rgb_hwc_to_nchw_normalized(const uint8_t *rgb,
                                        int w, int h,
                                        float *nchw) {
    int hw = w * h;
    float *ch_r = nchw;
    float *ch_g = nchw + hw;
    float *ch_b = nchw + hw * 2;

    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int idx = y * w + x;
            int src_idx = idx * 3;
            /* Normalize: (pixel/255 - mean) / std */
            ch_r[idx] = ((float)rgb[src_idx + 0] / 255.0f - IMAGENET_MEAN[0]) / IMAGENET_STD[0];
            ch_g[idx] = ((float)rgb[src_idx + 1] / 255.0f - IMAGENET_MEAN[1]) / IMAGENET_STD[1];
            ch_b[idx] = ((float)rgb[src_idx + 2] / 255.0f - IMAGENET_MEAN[2]) / IMAGENET_STD[2];
        }
    }
}

int ijkai_cv_scene_process_internal(mnn_context *mnn_ctx,
                                     uint8_t *rgb_data, int w, int h,
                                     ijkai_scene_result *result) {
    if (!mnn_ctx || !rgb_data || w <= 0 || h <= 0 || !result)
        return -1;

    memset(result, 0, sizeof(ijkai_scene_result));
    result->label = SCENE_UNKNOWN;
    result->confidence = 0.0f;
    snprintf(result->label_text, sizeof(result->label_text), "%s",
             SCENE_LABELS[SCENE_UNKNOWN]);

    /* Step 1: Resize to model input size (224x224) */
    int model_w = SCENE_MODEL_INPUT_W;
    int model_h = SCENE_MODEL_INPUT_H;
    size_t resized_size = (size_t)(model_w * model_h * 3);
    uint8_t *resized = (uint8_t *)malloc(resized_size);
    if (!resized) return -1;

    rgb_bilinear_resize(rgb_data, w, h, resized, model_w, model_h);

    /* Step 2: Convert to NCHW float32 with ImageNet normalization */
    size_t nchw_size = (size_t)(1 * SCENE_MODEL_INPUT_C * model_w * model_h);
    float *nchw_input = (float *)malloc(nchw_size * sizeof(float));
    if (!nchw_input) {
        free(resized);
        return -1;
    }

    rgb_hwc_to_nchw_normalized(resized, model_w, model_h, nchw_input);
    free(resized);

    /* Step 3: Run MNN inference */
    float output[SCENE_MODEL_NUM_CLASSES] = {0};
    int ret = mnn_run(mnn_ctx, nchw_input,
                      1, SCENE_MODEL_INPUT_C, model_w, model_h,
                      output, (int)(SCENE_MODEL_NUM_CLASSES * sizeof(float)));
    free(nchw_input);

    if (ret != 0) {
        fprintf(stderr, "[IJKAI_SCENE] mnn_run failed\n");
        return -1;
    }

    /* Step 4: Argmax to find top-1 class */
    int best_idx = 0;
    float best_val = output[0];
    for (int i = 1; i < SCENE_MODEL_NUM_CLASSES; i++) {
        if (output[i] > best_val) {
            best_val = output[i];
            best_idx = i;
        }
    }

    /* Step 5: Fill result */
    result->label = (ijkai_scene_label)best_idx;
    result->confidence = best_val;

    /* Build composite label text: e.g. "室外" (just the top-1 label) */
    snprintf(result->label_text, sizeof(result->label_text), "%s (%.0f%%)",
             SCENE_LABELS[best_idx], best_val * 100.0f);

    printf("[IJKAI_SCENE] result: %s (%.2f%%)\n",
           SCENE_LABELS[best_idx], best_val * 100.0f);

    return 0;
}

ijkai_cv_context *ijkai_cv_scene_init(const char *model_path,
                                       int n_threads,
                                       ijkai_cv_backend backend) {
    /* Delegate to shared CV init with scene type */
    return ijkai_cv_init(IJKAI_TYPE_CV_SCENE, model_path, n_threads, backend);
}

int ijkai_cv_scene_process(ijkai_cv_context *ctx,
                            uint8_t *rgb_data, int w, int h,
                            ijkai_scene_callback callback,
                            void *user_data) {
    if (!ctx || !rgb_data || w <= 0 || h <= 0)
        return -1;

    /* Allocate task data and copy input */
    cv_task_data *data = (cv_task_data *)malloc(sizeof(cv_task_data));
    if (!data) return -1;

    data->ctx = ctx;
    size_t data_size = (size_t)(w * h * 3);
    data->input_data = (uint8_t *)malloc(data_size);
    if (!data->input_data) {
        free(data);
        return -1;
    }
    memcpy(data->input_data, rgb_data, data_size);

    data->in_width    = w;
    data->in_height   = h;
    data->in_channels = 3; /* RGB */
    data->out_width   = 0;
    data->out_height  = 0;
    data->scale_factor = 1.0f;
    data->callback    = NULL; /* Not used for scene tasks */
    data->scene_callback = callback;
    data->user_data   = user_data;
    data->sub_type    = IJKAI_CV_SCENE_CLASSIFY;

    /* Push to async queue */
    ijkai_task task;
    task.type = IJKAI_TASK_CV;
    task.priority = IJKAI_PRIORITY_NORMAL;
    task.timestamp = 0;
    task.task_data = data;

    return ijkai_queue_push(ctx->queue, &task);
}
