/*
 * ffpipenode_ai_video.h
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
 */

#ifndef FFPLAY__FF_FFPIPENODE_AI_VIDEO_H
#define FFPLAY__FF_FFPIPENODE_AI_VIDEO_H

#include "../ff_ffpipenode.h"
#include "../../ijkai/ijkai.h"

struct FFPlayer;

/*
 * 创建 AI 视频处理 Pipenode
 * 包装 ijkai_pipenode_create()，接入播放器 pipeline
 * - LLM 类型: 异步管理 worker 线程，接收外部 prompt
 * - CV 类型:  处理解码后的视频帧（超分/检测）
 */
IJKFF_Pipenode *ffpipenode_create_ai_video_processor(struct FFPlayer *ffp,
                                                       ijkai_type ai_type,
                                                       const char *model_path,
                                                       int n_threads);

/*
 * 从 AI Pipenode 获取 AI 上下文，用于外部提交任务
 */
struct ijkai_context *ffpipenode_ai_get_context(IJKFF_Pipenode *node);

#endif
