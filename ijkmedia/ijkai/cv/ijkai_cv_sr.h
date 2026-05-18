/*
 * ijkai_cv_sr.h
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * Super-resolution module internal API.
 * Uses MNN to upscale video frames (e.g., ESPCN, FSRCNN models).
 */

#ifndef IJKAI_CV_SR_H
#define IJKAI_CV_SR_H

#include <stdint.h>
#include "ijkai_cv_mnn_wrap.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Internal super-resolution processing
 * @param mnn_ctx     MNN inference context
 * @param input_data  Input RGBA image data
 * @param in_w        Input width
 * @param in_h        Input height
 * @param scale       Upscale factor (2.0, 3.0, 4.0)
 * @param out_data    [out] Output RGBA image data (caller must free)
 * @param out_w       [out] Output width
 * @param out_h       [out] Output height
 * @return 0 on success, -1 on failure
 */
int ijkai_cv_sr_process_internal(mnn_context *mnn_ctx,
                                  const uint8_t *input_data,
                                  int in_w, int in_h,
                                  float scale,
                                  uint8_t **out_data,
                                  int *out_w, int *out_h);

#ifdef __cplusplus
}
#endif

#endif // IJKAI_CV_SR_H
