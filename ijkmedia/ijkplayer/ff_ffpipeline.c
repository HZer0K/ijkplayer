/*
 * ff_ffpipeline.c
 *
 * Copyright (c) 2014 Bilibili
 * Copyright (c) 2014 Zhang Rui <bbcallen@gmail.com>
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
 */

/*
 * ff_ffpipeline.c
 *
 * Pipeline 管线基类实现。
 * 提供管线分配/释放和统一接口分发，具体实现由子类 (如 ffpipeline_ai) 填充。
 */

#include "ff_ffpipeline.h"
#include <stdlib.h>
#include <string.h>

/**
 * 分配管线实例。
 * @param opaque_class  日志类标识，用于 av_log 输出
 * @param opaque_size   子类私有数据大小 (calloc 分配)
 * @return 新分配的管线实例，失败返回 NULL
 */
IJKFF_Pipeline *ffpipeline_alloc(SDL_Class *opaque_class, size_t opaque_size)
{
    IJKFF_Pipeline *pipeline = (IJKFF_Pipeline*) calloc(1, sizeof(IJKFF_Pipeline));
    if (!pipeline)
        return NULL;

    pipeline->opaque_class = opaque_class;
    pipeline->opaque       = calloc(1, opaque_size);
    if (!pipeline->opaque) {
        free(pipeline);
        return NULL;
    }

    return pipeline;
}

/**
 * 释放管线实例。
 * 流程: func_destroy(子类析构) -> free(opaque) -> free(pipeline)
 */
void ffpipeline_free(IJKFF_Pipeline *pipeline)
{
    if (!pipeline)
        return;

    if (pipeline->func_destroy) {
        pipeline->func_destroy(pipeline);
    }

    free(pipeline->opaque);
    memset(pipeline, 0, sizeof(IJKFF_Pipeline));
    free(pipeline);
}

void ffpipeline_free_p(IJKFF_Pipeline **pipeline)
{
    if (!pipeline)
        return;

    ffpipeline_free(*pipeline);
}

/* 通过函数指针分发: 创建并打开视频解码器节点 */
IJKFF_Pipenode* ffpipeline_open_video_decoder(IJKFF_Pipeline *pipeline, FFPlayer *ffp)
{
    return pipeline->func_open_video_decoder(pipeline, ffp);
}

/* 通过函数指针分发: 初始化视频解码器 (两步式: init + config) */
IJKFF_Pipenode* ffpipeline_init_video_decoder(IJKFF_Pipeline *pipeline, FFPlayer *ffp)
{
    return pipeline->func_init_video_decoder(pipeline, ffp);
}

/* 通过函数指针分发: 配置视频解码器参数 */
int ffpipeline_config_video_decoder(IJKFF_Pipeline *pipeline, FFPlayer *ffp)
{
    return pipeline->func_config_video_decoder(pipeline, ffp);
}

/* 通过函数指针分发: 创建音频输出设备 */
SDL_Aout *ffpipeline_open_audio_output(IJKFF_Pipeline *pipeline, FFPlayer *ffp)
{
    return pipeline->func_open_audio_output(pipeline, ffp);
}
