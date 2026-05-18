/*
 * ijkai_cv_detect.c
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * Object detection module implementation.
 * Converts RGBA input to NCHW float, runs MNN inference,
 * processes output (NMS, thresholding) to produce detection results.
 *
 * Expected model: YOLOv5s/NanoDet exported to .mnn format.
 * Input:  [1, 3, H, W]  float32, normalized [0,1]
 * Output: [1, N, 6]     float32 (x_center, y_center, w, h, conf, class_id)
 */

#define _POSIX_C_SOURCE 199309L

#include "ijkai_cv_detect.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <math.h>

#define MAX_DETECTIONS 100
#define CONFIDENCE_THRESHOLD 0.25f
#define NMS_THRESHOLD 0.45f

/**
 * Detection result
 */
typedef struct {
    float x, y, w, h;      // Bounding box (normalized)
    float confidence;       // Detection confidence
    int   class_id;         // Class ID
} detection_result;

/**
 * RGBA to float NCHW (same as SR module, but localized here)
 */
static void rgba_to_nchw_float(const uint8_t *rgba, int w, int h,
                                float *output) {
    int size = w * h;
    // YOLO typically uses 3-channel RGB input
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int src_idx = (y * w + x) * 4;
            int dst_base = y * w + x;
            output[0 * size + dst_base] = rgba[src_idx + 0] / 255.0f;
            output[1 * size + dst_base] = rgba[src_idx + 1] / 255.0f;
            output[2 * size + dst_base] = rgba[src_idx + 2] / 255.0f;
        }
    }
}

/**
 * Calculate Intersection over Union for two bounding boxes
 */
static float iou(float x1, float y1, float w1, float h1,
                  float x2, float y2, float w2, float h2) {
    float x_left   = (x1 > x2) ? x1 : x2;
    float y_top    = (y1 > y2) ? y1 : y2;
    float x_right  = ((x1 + w1) < (x2 + w2)) ? (x1 + w1) : (x2 + w2);
    float y_bottom = ((y1 + h1) < (y2 + h2)) ? (y1 + h1) : (y2 + h2);

    if (x_left >= x_right || y_top >= y_bottom) {
        return 0.0f;
    }

    float intersect_area = (x_right - x_left) * (y_bottom - y_top);
    float union_area = w1 * h1 + w2 * h2 - intersect_area;
    return (union_area > 0.0f) ? (intersect_area / union_area) : 0.0f;
}

/**
 * Non-Maximum Suppression
 */
static int nms(detection_result *dets, int count,
               detection_result *out, int max_out) {
    // Sort by confidence (descending) - simple insertion sort
    for (int i = 0; i < count - 1; i++) {
        for (int j = i + 1; j < count; j++) {
            if (dets[j].confidence > dets[i].confidence) {
                detection_result tmp = dets[i];
                dets[i] = dets[j];
                dets[j] = tmp;
            }
        }
    }

    int out_count = 0;
    int *suppressed = (int *)calloc((size_t)count, sizeof(int));
    if (!suppressed) return 0;

    for (int i = 0; i < count && out_count < max_out; i++) {
        if (suppressed[i]) continue;

        out[out_count++] = dets[i];

        for (int j = i + 1; j < count; j++) {
            if (suppressed[j]) continue;
            if (dets[j].class_id != dets[i].class_id) continue;

            float iou_val = iou(
                dets[i].x, dets[i].y, dets[i].w, dets[i].h,
                dets[j].x, dets[j].y, dets[j].w, dets[j].h);

            if (iou_val > NMS_THRESHOLD) {
                suppressed[j] = 1;
            }
        }
    }

    free(suppressed);
    return out_count;
}

int ijkai_cv_detect_process_internal(mnn_context *mnn_ctx,
                                      const uint8_t *input_data,
                                      int in_w, int in_h,
                                      uint8_t **out_data,
                                      int *out_w, int *out_h) {
    if (!mnn_ctx || !input_data || !out_data || !out_w || !out_h) {
        return -1;
    }

    // Get model input dimensions
    int model_n, model_c, model_w, model_h;
    if (mnn_get_input_dims(mnn_ctx, &model_n, &model_c, &model_w, &model_h) != 0) {
        fprintf(stderr, "[CV_DETECT] Failed to get model input dims\n");
        return -1;
    }

    int input_w = (model_w > 0) ? model_w : 640;
    int input_h = (model_h > 0) ? model_h : 640;

    // Allocate input tensor
    float *input_tensor = (float *)malloc(
        (size_t)(model_n * model_c * input_w * input_h * sizeof(float)));
    if (!input_tensor) return -1;

    // Convert RGBA to NCHW float
    rgba_to_nchw_float(input_data, in_w, in_h, input_tensor);

    // Allocate output buffer (conservative size)
    size_t output_size = 1024 * 1024; // 1MB should be enough
    float *output_tensor = (float *)malloc(output_size);
    if (!output_tensor) {
        free(input_tensor);
        return -1;
    }
    memset(output_tensor, 0, output_size);

    // Run MNN inference
    int ret = mnn_run(mnn_ctx,
                      input_tensor,
                      model_n, model_c, input_w, input_h,
                      output_tensor, (int)output_size);

    if (ret != 0) {
        fprintf(stderr, "[CV_DETECT] MNN inference failed\n");
        free(input_tensor);
        free(output_tensor);
        return -1;
    }

    // Parse detection results
    // Expected output format from detection model:
    // [batch, num_detections, 6] = [1, N, 6] where 6 = (x, y, w, h, conf, class)
    int out_n, out_c, out_w_model, out_h_model;
    detection_result dets[MAX_DETECTIONS];
    int det_count = 0;

    if (mnn_get_output_dims(mnn_ctx, &out_n, &out_c, &out_w_model, &out_h_model) == 0) {
        // Known output shape: parse accordingly
        int num_elements = out_n * out_c * out_w_model * out_h_model;
        int num_detections = num_elements / 6;

        for (int i = 0; i < num_detections && i < MAX_DETECTIONS && det_count < MAX_DETECTIONS; i++) {
            float x = output_tensor[i * 6 + 0]; // x_center
            float y = output_tensor[i * 6 + 1]; // y_center
            float w = output_tensor[i * 6 + 2]; // width
            float h = output_tensor[i * 6 + 3]; // height
            float conf = output_tensor[i * 6 + 4];
            int cls = (int)output_tensor[i * 6 + 5];

            if (conf >= CONFIDENCE_THRESHOLD) {
                dets[det_count].x = (x - w / 2.0f);
                dets[det_count].y = (y - h / 2.0f);
                dets[det_count].w = w;
                dets[det_count].h = h;
                dets[det_count].confidence = conf;
                dets[det_count].class_id = cls;
                det_count++;
            }
        }
    } else {
        // Fallback: sequential output parsing
        // Assume output is already filtered detections
        int total_floats = (int)(output_size / sizeof(float));
        for (int i = 0; i + 5 < total_floats && det_count < MAX_DETECTIONS; i += 6) {
            float conf = output_tensor[i + 4];
            if (conf >= CONFIDENCE_THRESHOLD) {
                float x = output_tensor[i + 0];
                float y = output_tensor[i + 1];
                float w = output_tensor[i + 2];
                float h = output_tensor[i + 3];
                int cls = (int)output_tensor[i + 5];

                dets[det_count].x = (x - w / 2.0f);
                dets[det_count].y = (y - h / 2.0f);
                dets[det_count].w = w;
                dets[det_count].h = h;
                dets[det_count].confidence = conf;
                dets[det_count].class_id = cls;
                det_count++;
            }
        }
    }

    // Apply NMS
    detection_result nms_results[MAX_DETECTIONS];
    int nms_count = nms(dets, det_count, nms_results, MAX_DETECTIONS);

    // Serialize results: for each detection, store 5 floats + 1 int = 24 bytes
    *out_w = nms_count;
    *out_h = 0;

    if (nms_count > 0) {
        *out_data = (uint8_t *)malloc((size_t)(nms_count * 24));
        if (!*out_data) {
            free(input_tensor);
            free(output_tensor);
            return -1;
        }

        uint8_t *ptr = *out_data;
        for (int i = 0; i < nms_count; i++) {
            memcpy(ptr, &nms_results[i], sizeof(float) * 5);
            ptr += 20; // 5 floats
            memcpy(ptr, &nms_results[i].class_id, sizeof(int));
            ptr += 4;
        }
    } else {
        *out_data = NULL;
    }

    free(input_tensor);
    free(output_tensor);

    return 0;
}
