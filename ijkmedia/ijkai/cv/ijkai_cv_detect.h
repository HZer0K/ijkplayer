/*
 * ijkai_cv_detect.h
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * Object detection module internal API.
 * Uses MNN to run detection models (e.g., YOLOv5s, NanoDet).
 */

#ifndef IJKAI_CV_DETECT_H
#define IJKAI_CV_DETECT_H

#include <stdint.h>
#include "ijkai_cv_mnn_wrap.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Internal detection processing
 * @param mnn_ctx     MNN inference context
 * @param input_data  Input RGBA image data
 * @param in_w        Input width
 * @param in_h        Input height
 * @param out_data    [out] Detection result data (JSON-like struct, caller must free)
 * @param out_w       [out] Number of detections
 * @param out_h       [out] Reserved (0)
 * @return 0 on success, -1 on failure
 *
 * out_data format: array of detection results, each containing:
 *   float x, y, w, h (normalized bounding box)
 *   float confidence
 *   int class_id
 * Stored as sequential float+int pairs.
 */
int ijkai_cv_detect_process_internal(mnn_context *mnn_ctx,
                                      const uint8_t *input_data,
                                      int in_w, int in_h,
                                      uint8_t **out_data,
                                      int *out_w, int *out_h);

#ifdef __cplusplus
}
#endif

#endif // IJKAI_CV_DETECT_H
