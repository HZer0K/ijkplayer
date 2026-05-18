/*
 * ijkai_cv_sr.c
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * Super-resolution module implementation.
 * Converts RGBA input to NCHW float, runs MNN inference,
 * converts NCHW output back to RGBA.
 *
 * Expected model: lightweight SR model (ESPCN/FSRCNN) exported to .mnn format.
 * Input:  [1, 3, H, W]  float32, normalized [0,1]
 * Output: [1, 3, H*scale, W*scale] float32, normalized [0,1]
 */

#define _POSIX_C_SOURCE 199309L

#include "ijkai_cv_sr.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

/**
 * RGBA (byte-per-channel) to float NCHW, normalized to [0,1]
 */
static void rgba_to_nchw_float(const uint8_t *rgba, int w, int h,
                         float *output, int channels) {
    int size = w * h;
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int src_idx = (y * w + x) * 4;
            int dst_base = y * w + x;
            for (int c = 0; c < channels && c < 3; c++) {
                output[c * size + dst_base] = rgba[src_idx + c] / 255.0f;
            }
            if (channels == 4) {
                output[3 * size + dst_base] = rgba[src_idx + 3] / 255.0f;
            }
        }
    }
}

static void nchw_float_to_rgba(const float *input, int w, int h, int channels,
                         uint8_t *output) {
    int size = w * h;
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int dst_idx = (y * w + x) * 4;
            int src_base = y * w + x;
            for (int c = 0; c < channels && c < 3; c++) {
                float v = input[c * size + src_base];
                if (v < 0.0f) v = 0.0f;
                if (v > 1.0f) v = 1.0f;
                output[dst_idx + c] = (uint8_t)(v * 255.0f);
            }
            if (channels >= 4) {
                float v = input[3 * size + src_base];
                if (v < 0.0f) v = 0.0f;
                if (v > 1.0f) v = 1.0f;
                output[dst_idx + 3] = (uint8_t)(v * 255.0f);
            } else {
                output[dst_idx + 3] = 255;
            }
        }
    }
}

int ijkai_cv_sr_process_internal(mnn_context *mnn_ctx,
                                  const uint8_t *input_data,
                                  int in_w, int in_h,
                                  float scale,
                                  uint8_t **out_data,
                                  int *out_w, int *out_h) {
    if (!mnn_ctx || !input_data || !out_data || !out_w || !out_h) {
        return -1;
    }

    int model_n, model_c, model_w, model_h;
    if (mnn_get_input_dims(mnn_ctx, &model_n, &model_c, &model_w, &model_h) != 0) {
        fprintf(stderr, "[CV_SR] Failed to get model input dims\n");
        return -1;
    }

    int eff_w = in_w;
    int eff_h = in_h;
    if (model_w > 0 && model_h > 0) {
        eff_w = model_w;
        eff_h = model_h;
    }

    float *input_tensor = (float *)malloc(
        (size_t)(model_n * model_c * eff_w * eff_h * sizeof(float)));
    if (!input_tensor) {
        return -1;
    }

    int channels = (model_c > 0) ? model_c : 3;
    rgba_to_nchw_float(input_data, in_w, in_h, input_tensor, channels);

    int out_n, out_c, out_w_model, out_h_model;
    if (mnn_get_output_dims(mnn_ctx, &out_n, &out_c, &out_w_model, &out_h_model) != 0) {
        out_n = 1;
        out_c = channels;
        out_w_model = (int)(eff_w * scale);
        out_h_model = (int)(eff_h * scale);
    }

    int out_w_final = (out_w_model > 0) ? out_w_model : (int)(in_w * scale);
    int out_h_final = (out_h_model > 0) ? out_h_model : (int)(in_h * scale);
    size_t out_elements = (size_t)(out_n * out_c * out_w_final * out_h_final);
    float *output_tensor = (float *)calloc(out_elements, sizeof(float));
    if (!output_tensor) {
        free(input_tensor);
        return -1;
    }

    int ret = mnn_run(mnn_ctx,
                      input_tensor,
                      model_n, model_c, eff_w, eff_h,
                      output_tensor, (int)(out_elements * sizeof(float)));

    if (ret != 0) {
        fprintf(stderr, "[CV_SR] MNN inference failed\n");
        free(input_tensor);
        free(output_tensor);
        return -1;
    }

    *out_w = out_w_final;
    *out_h = out_h_final;
    *out_data = (uint8_t *)malloc(
        (size_t)(out_w_final * out_h_final * 4));
    if (!*out_data) {
        free(input_tensor);
        free(output_tensor);
        return -1;
    }

    nchw_float_to_rgba(output_tensor, out_w_final, out_h_final, out_c, *out_data);

    free(input_tensor);
    free(output_tensor);

    return 0;
}
