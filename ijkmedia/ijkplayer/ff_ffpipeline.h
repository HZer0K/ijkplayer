/*
 * ff_ffpipeline.h
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
 * ff_ffpipeline.h
 *
 * Pipeline 管线架构头文件。
 *
 * Pipeline 设计模式:
 *   IJKFF_Pipeline 是“管线”抽象，通过函数指针实现多态。
 *   不同平台 (Android/IOS/AI) 提供各自的 Pipeline 实现，
 *   负责创建视频解码器 (Pipenode) 和音频输出 (SDL_Aout)。
 *
 * 典型用法:
 *   1. 子类调用 ffpipeline_alloc() 分配管线和私有数据
 *   2. 填充 func_* 函数指针
 *   3. FFPlayer 通过统一接口调用，实现平台无关的解码器/音频输出创建
 */

#ifndef FFPLAY__FF_FFPIPELINE_H
#define FFPLAY__FF_FFPIPELINE_H

#include "ijksdl/ijksdl_class.h"
#include "ijksdl/ijksdl_mutex.h"
#include "ijksdl/ijksdl_aout.h"
#include "ff_ffpipenode.h"
#include "ff_ffplay_def.h"

typedef struct IJKFF_Pipeline_Opaque IJKFF_Pipeline_Opaque;
typedef struct IJKFF_Pipeline IJKFF_Pipeline;

/**
 * @struct IJKFF_Pipeline
 * @brief  管线抽象结构，通过函数指针实现平台多态
 *
 * 子类在 opaque 中存储平台私有数据，并设置 func_* 函数指针。
 */
struct IJKFF_Pipeline {
    SDL_Class             *opaque_class;    /**< 日志类标识 (用于 av_log) */
    IJKFF_Pipeline_Opaque *opaque;          /**< 平台私有数据 */

    void            (*func_destroy)             (IJKFF_Pipeline *pipeline);  /**< 子类析构回调 */
    IJKFF_Pipenode *(*func_open_video_decoder)  (IJKFF_Pipeline *pipeline, FFPlayer *ffp);  /**< 创建并打开视频解码器节点 */
    SDL_Aout       *(*func_open_audio_output)   (IJKFF_Pipeline *pipeline, FFPlayer *ffp);  /**< 创建音频输出设备 */
    IJKFF_Pipenode *(*func_init_video_decoder)  (IJKFF_Pipeline *pipeline, FFPlayer *ffp);  /**< 初始化视频解码器 (两步式创建: init + config) */
    int           (*func_config_video_decoder)  (IJKFF_Pipeline *pipeline, FFPlayer *ffp);  /**< 配置视频解码器参数 */
};

IJKFF_Pipeline *ffpipeline_alloc(SDL_Class *opaque_class, size_t opaque_size);  /**< 分配管线，子类调用后填充 func_* */
void ffpipeline_free(IJKFF_Pipeline *pipeline);   /**< 释放管线 (先调用 func_destroy，再释放 opaque) */
void ffpipeline_free_p(IJKFF_Pipeline **pipeline);

IJKFF_Pipenode *ffpipeline_open_video_decoder(IJKFF_Pipeline *pipeline, FFPlayer *ffp);
SDL_Aout       *ffpipeline_open_audio_output(IJKFF_Pipeline *pipeline, FFPlayer *ffp);

IJKFF_Pipenode* ffpipeline_init_video_decoder(IJKFF_Pipeline *pipeline, FFPlayer *ffp);
int ffpipeline_config_video_decoder(IJKFF_Pipeline *pipeline, FFPlayer *ffp);

#endif
