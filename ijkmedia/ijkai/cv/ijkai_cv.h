/*
 * ijkai_cv.h
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * CV module public API for ijkplayer AI framework.
 * Provides super-resolution and object detection capabilities via MNN.
 */

#ifndef IJKAI_CV_H
#define IJKAI_CV_H

#include "../ijkai.h"
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * CV context (opaque pointer)
 */
typedef struct ijkai_cv_context ijkai_cv_context;

/**
 * Initialize CV context
 * @param type       CV type (IJKAI_TYPE_CV_SR or IJKAI_TYPE_CV_DETECT)
 * @param model_path MNN model file path
 * @param n_threads  Number of threads
 * @param backend    Inference backend
 * @return CV context pointer, NULL on failure
 */
ijkai_cv_context *ijkai_cv_init(ijkai_type type, const char *model_path,
                                int n_threads, ijkai_cv_backend backend);

/**
 * Release CV context
 * @param ctx CV context pointer
 */
void ijkai_cv_release(ijkai_cv_context *ctx);

/**
 * Set CV inference backend
 * @param ctx     CV context
 * @param backend Backend type
 * @return 0 on success, -1 on failure
 */
int ijkai_cv_ctx_set_backend(ijkai_cv_context *ctx, int backend);

/**
 * Process frame with super resolution (async)
 * @param ctx         CV context
 * @param input_data  Input image data (RGBA, byte-per-channel)
 * @param in_width    Input width
 * @param in_height   Input height
 * @param scale_factor Upscale factor (2.0f, 3.0f, 4.0f, etc.)
 * @param callback    Result callback
 * @param user_data   User data passed to callback
 * @return 0 on success, -1 on failure
 */
int ijkai_cv_sr_process(ijkai_cv_context *ctx,
                        uint8_t *input_data, int in_width, int in_height,
                        float scale_factor,
                        ijkai_cv_callback callback, void *user_data);

/**
 * Process frame with object detection (async)
 * @param ctx         CV context
 * @param input_data  Input image data (RGBA, byte-per-channel)
 * @param in_width    Input width
 * @param in_height   Input height
 * @param callback    Result callback
 * @param user_data   User data passed to callback
 * @return 0 on success, -1 on failure
 */
int ijkai_cv_detect_process(ijkai_cv_context *ctx,
                            uint8_t *input_data, int in_width, int in_height,
                            ijkai_cv_callback callback, void *user_data);

/**
 * Get number of processed frames
 * @param ctx CV context
 * @return Number of processed frames
 */
int ijkai_cv_get_processed_frames(ijkai_cv_context *ctx);

/**
 * Get total evaluation time in milliseconds
 * @param ctx CV context
 * @return Evaluation time in ms
 */
int64_t ijkai_cv_get_eval_time(ijkai_cv_context *ctx);

#ifdef __cplusplus
}
#endif

#endif // IJKAI_CV_H
