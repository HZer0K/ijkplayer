/*
 * ijkai_cv_mnn_wrap.h
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * Pure C wrapper around MNN C++ API.
 * Provides inference interface for CV modules.
 */

#ifndef IJKAI_CV_MNN_WRAP_H
#define IJKAI_CV_MNN_WRAP_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * MNN backend type
 */
typedef enum {
    MNN_BACKEND_CPU     = 0,
    MNN_BACKEND_OPENCL  = 1,
    MNN_BACKEND_VULKAN  = 2,
    MNN_BACKEND_AUTO    = 3
} mnn_backend_type;

/**
 * MNN inference context (opaque pointer)
 */
typedef struct mnn_context mnn_context;

/**
 * Initialize MNN inference context
 * @param model_path Path to MNN model file (.mnn)
 * @param backend    Inference backend (CPU/OpenCL/Vulkan)
 * @param n_threads  Number of threads (for CPU backend)
 * @return MNN context pointer, NULL on failure
 */
mnn_context *mnn_init(const char *model_path, int backend, int n_threads);

/**
 * Run inference
 * @param ctx     MNN context
 * @param input   Input tensor data (float32, NCHW)
 * @param in_n    Batch size
 * @param in_c    Input channels
 * @param in_w    Input width
 * @param in_h    Input height
 * @param output  Output tensor buffer (float32)
 * @param out_size Output buffer size in bytes
 * @return 0 on success, -1 on failure
 */
int mnn_run(mnn_context *ctx,
            const float *input,
            int in_n, int in_c, int in_w, int in_h,
            float *output, int out_size);

/**
 * Run inference with multiple outputs
 * @param ctx      MNN context
 * @param input    Input tensor data (float32, NCHW)
 * @param in_n     Batch size
 * @param in_c     Input channels
 * @param in_w     Input width
 * @param in_h     Input height
 * @param outputs  Array of output buffers (float32)
 * @param out_sizes Array of output buffer sizes in bytes
 * @param out_count Number of outputs
 * @return 0 on success, -1 on failure
 */
int mnn_run_multi_output(mnn_context *ctx,
                         const float *input,
                         int in_n, int in_c, int in_w, int in_h,
                         float **outputs, int *out_sizes, int out_count);

/**
 * Get input tensor dimensions
 * @param ctx  MNN context
 * @param n    [out] Batch size
 * @param c    [out] Channels
 * @param w    [out] Width
 * @param h    [out] Height
 * @return 0 on success, -1 on failure
 */
int mnn_get_input_dims(mnn_context *ctx, int *n, int *c, int *w, int *h);

/**
 * Get output tensor dimensions
 * @param ctx  MNN context
 * @param n    [out] Batch size
 * @param c    [out] Channels
 * @param w    [out] Width
 * @param h    [out] Height
 * @return 0 on success, -1 on failure
 */
int mnn_get_output_dims(mnn_context *ctx, int *n, int *c, int *w, int *h);

/**
 * Get output tensor dimensions by index (for multi-output models)
 * @param ctx   MNN context
 * @param index Output index
 * @param n     [out] Batch size
 * @param c     [out] Channels
 * @param w     [out] Width
 * @param h     [out] Height
 * @return 0 on success, -1 on failure
 */
int mnn_get_output_dims_by_index(mnn_context *ctx, int index,
                                 int *n, int *c, int *w, int *h);

/**
 * Release MNN inference context
 * @param ctx MNN context pointer
 */
void mnn_release(mnn_context *ctx);

#ifdef __cplusplus
}
#endif

#endif // IJKAI_CV_MNN_WRAP_H
