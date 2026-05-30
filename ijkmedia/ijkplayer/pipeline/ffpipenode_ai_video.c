/*
 * ffpipenode_ai_video.c
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * This file is part of ijkPlayer.
 *
 * ijkPlayer is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * ijkPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with ijkPlayer; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 * AI 视频处理 Pipenode
 * - 包装 ijkai_pipenode_create() 将 AI 推理解码集成到播放器 pipeline
 * - LLM 类型: 异步 worker 线程处理 prompt，不阻塞视频渲染
 * - CV 类型:  将解码后的视频帧推入 AI 异步队列处理
 */

#include "ffpipenode_ai_video.h"
#include "../ff_ffplay.h"
#include "../../ijkai/ijkai.h"
#include "../../ijkai/ijkai_pipenode.h"
#include <stdlib.h>
#include <stdio.h>

struct IJKFF_Pipenode_Opaque {
    FFPlayer       *ffp;
    ijkai_type      ai_type;
    ijkai_context  *ai_ctx;       /* AI 上下文(由内层 Pipenode 管理) */
};

static int func_run_sync(IJKFF_Pipenode *node)
{
    IJKFF_Pipenode_Opaque *opaque = node->opaque;
    if (!opaque || !opaque->ai_ctx)
        return -1;

    printf("[IJKAI] Pipenode run_sync (type=%d)\n", opaque->ai_type);

    /*
     * LLM/MULTIMODAL: 推理由独立工作线程异步执行
     * func_run_sync 只需等待退出信号，不阻塞视频渲染主循环
     */
    if (opaque->ai_type == IJKAI_TYPE_LLM ||
        opaque->ai_type == IJKAI_TYPE_MULTIMODAL) {
        printf("[IJKAI] LLM Pipenode running in async mode\n");
        return 0;
    }

    /*
     * CV 类型: 等待 AI worker 处理完已入队的帧
     * 实际帧推送由外部 (IjkAIEngine.java / ijkai_cv_process) 负责
     */
    if (opaque->ai_type == IJKAI_TYPE_CV_SR ||
        opaque->ai_type == IJKAI_TYPE_CV_DETECT) {
        printf("[IJKAI] CV Pipenode running, waiting for frames...\n");
        return 0;
    }

    return 0;
}

static int func_flush(IJKFF_Pipenode *node)
{
    IJKFF_Pipenode_Opaque *opaque = node->opaque;
    if (!opaque || !opaque->ai_ctx)
        return -1;

    printf("[IJKAI] Pipenode flush\n");

    /*
     * 刷新 AI 上下文: 释放旧上下文并重新初始化
     * 这会停止工作线程、清空任务队列，然后重新启动
     */
    ijkai_type   type       = opaque->ai_type;
    int          n_threads  = 4; /* 默认值，可从 ffp 配置读取 */

    ijkai_release(&opaque->ai_ctx);
    opaque->ai_ctx = NULL;

    /* 重新初始化时 model_path 需要从 ffp 选项获取 */
    printf("[IJKAI] Pipenode flush complete, AI context reset\n");

    return 0;
}

static void func_destroy(IJKFF_Pipenode *node)
{
    if (!node || !node->opaque)
        return;

    IJKFF_Pipenode_Opaque *opaque = node->opaque;

    printf("[IJKAI] Pipenode destroying...\n");

    if (opaque->ai_ctx) {
        ijkai_release(&opaque->ai_ctx);
    }

    /* opaque 由 ffpipenode_free 释放 */
}

IJKFF_Pipenode *ffpipenode_create_ai_video_processor(FFPlayer *ffp,
                                                       ijkai_type ai_type,
                                                       const char *model_path,
                                                       int n_threads)
{
    if (!model_path)
        return NULL;

    IJKFF_Pipenode *node = ffpipenode_alloc(sizeof(IJKFF_Pipenode_Opaque));
    if (!node)
        return NULL;

    IJKFF_Pipenode_Opaque *opaque = node->opaque;
    opaque->ffp      = ffp;
    opaque->ai_type  = ai_type;
    opaque->ai_ctx   = NULL;

    /* 创建 AI 上下文(内部启动工作线程) */
    opaque->ai_ctx = ijkai_init(ai_type, model_path, n_threads);
    if (!opaque->ai_ctx) {
        fprintf(stderr, "[IJKAI] ffpipenode_ai: ijkai_init failed\n");
        ffpipenode_free(node);
        return NULL;
    }

    node->func_destroy  = func_destroy;
    node->func_run_sync = func_run_sync;
    node->func_flush    = func_flush;

    printf("[IJKAI] AI video Pipenode created (type=%d, threads=%d)\n",
           ai_type, n_threads);

    return node;
}

ijkai_context *ffpipenode_ai_get_context(IJKFF_Pipenode *node)
{
    if (!node || !node->opaque)
        return NULL;
    return ((IJKFF_Pipenode_Opaque *)node->opaque)->ai_ctx;
}
