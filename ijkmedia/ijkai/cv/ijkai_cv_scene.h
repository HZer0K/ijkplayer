/*
 * ijkai_cv_scene.h
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * Scene classification module for ijkplayer AI framework.
 * Classifies video frames into 6 scene categories:
 *   indoor, outdoor, daytime, nighttime, close-up, wide-shot.
 * Uses MobileNetV3-Small via MNN inference backend.
 */

#ifndef IJKAI_CV_SCENE_H
#define IJKAI_CV_SCENE_H

#include "../ijkai.h"
#include "ijkai_cv.h"
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Model input dimensions */
#define SCENE_MODEL_INPUT_W   224
#define SCENE_MODEL_INPUT_H   224
#define SCENE_MODEL_INPUT_C   3
#define SCENE_MODEL_NUM_CLASSES 6

/**
 * Scene classification labels
 */
typedef enum {
    SCENE_INDOOR    = 0,  /**< Indoor scene */
    SCENE_OUTDOOR   = 1,  /**< Outdoor scene */
    SCENE_DAYTIME   = 2,  /**< Daytime scene */
    SCENE_NIGHTTIME = 3,  /**< Nighttime scene */
    SCENE_CLOSEUP   = 4,  /**< Close-up shot */
    SCENE_WIDESHOT  = 5,  /**< Wide-angle / long shot */
    SCENE_UNKNOWN   = 6   /**< Unknown / unclassified */
} ijkai_scene_label;

/**
 * Scene classification result
 */
typedef struct {
    ijkai_scene_label label;        /**< Top-1 predicted label */
    float             confidence;   /**< Confidence score [0,1] */
    char              label_text[64]; /**< Human-readable text, e.g. "室外/白天" */
} ijkai_scene_result;

/**
 * Scene classification callback
 * @param result    Classification result (NULL on failure)
 * @param user_data User data passed to enqueue function
 */
typedef void (*ijkai_scene_callback)(ijkai_scene_result *result, void *user_data);

/**
 * Initialize scene classification CV context.
 * Creates MNN inference session and async task queue.
 *
 * @param model_path Path to .mnn model file
 * @param n_threads  Number of inference threads
 * @param backend    MNN backend (CPU/OpenCL/Vulkan)
 * @return CV context pointer, NULL on failure
 */
ijkai_cv_context *ijkai_cv_scene_init(const char *model_path,
                                       int n_threads,
                                       ijkai_cv_backend backend);

/**
 * Enqueue a frame for scene classification (async, non-blocking).
 * Input must be RGB (3-channel, byte-per-channel) data.
 * The frame will be resized to 224x224 and normalized before inference.
 *
 * @param ctx       CV context (from ijkai_cv_scene_init)
 * @param rgb_data  RGB image data (HWC layout, 3 channels)
 * @param w         Image width
 * @param h         Image height
 * @param callback  Result callback
 * @param user_data User data passed to callback
 * @return 0 on success, -1 on failure
 */
int ijkai_cv_scene_process(ijkai_cv_context *ctx,
                            uint8_t *rgb_data, int w, int h,
                            ijkai_scene_callback callback,
                            void *user_data);

/**
 * Internal: process scene classification on worker thread.
 * Called by CV worker loop; not intended for external use.
 *
 * @param mnn_ctx  MNN inference context
 * @param rgb_data RGB image data
 * @param w        Image width
 * @param h        Image height
 * @param result   [out] Classification result
 * @return 0 on success, -1 on failure
 */
int ijkai_cv_scene_process_internal(mnn_context *mnn_ctx,
                                     uint8_t *rgb_data, int w, int h,
                                     ijkai_scene_result *result);

/**
 * Get human-readable label text for a scene label.
 * Returns Chinese text: 室内/室外/白天/夜晚/特写/远景.
 *
 * @param label Scene label enum value
 * @return Static string pointer (do not free)
 */
const char *ijkai_scene_label_text(ijkai_scene_label label);

#ifdef __cplusplus
}
#endif

#endif // IJKAI_CV_SCENE_H
