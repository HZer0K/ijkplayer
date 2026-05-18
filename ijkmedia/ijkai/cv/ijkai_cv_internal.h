/*
 * ijkai_cv_internal.h
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * CV module internal data structures.
 */

#ifndef IJKAI_CV_INTERNAL_H
#define IJKAI_CV_INTERNAL_H

#include "ijkai_cv.h"
#include "ijkai_cv_mnn_wrap.h"
#include "../async/ijkai_queue.h"

#include <stdint.h>
#include <pthread.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * CV sub-module type
 */
typedef enum {
    IJKAI_CV_SUPER_RESOLUTION = 0,
    IJKAI_CV_DETECTION        = 1
} ijkai_cv_sub_type;

/**
 * CV task data (for async queue)
 */
typedef struct {
    ijkai_cv_context    *ctx;
    uint8_t             *input_data;
    int                  in_width;
    int                  in_height;
    int                  in_channels;
    int                  out_width;
    int                  out_height;
    float                scale_factor;
    ijkai_cv_callback    callback;
    void                *user_data;
    ijkai_cv_sub_type    sub_type;
} cv_task_data;

/**
 * CV context (internal)
 */
struct ijkai_cv_context {
    char          *model_path;
    int            n_threads;
    int            backend;
    mnn_context   *mnn_ctx;
    
    // Task queue and worker thread
    ijkai_task_queue *queue;
    pthread_t         worker_thread;
    volatile int      running;
    
    // Statistics
    int64_t eval_time_ms;
    int     processed_frames;
};

#ifdef __cplusplus
}
#endif

#endif // IJKAI_CV_INTERNAL_H
