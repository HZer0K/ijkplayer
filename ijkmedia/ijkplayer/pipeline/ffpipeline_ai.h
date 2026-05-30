/*
 * ffpipeline_ai.h
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

#ifndef FFPLAY__FF_FFPIPELINE_AI_H
#define FFPLAY__FF_FFPIPELINE_AI_H

#include "../ff_ffpipeline.h"
#include "../../ijkai/ijkai.h"

struct FFPlayer;

/*
 * 创建 AI 管线
 * 通过 ijkai_type 指定 AI 处理类型
 * func_open_video_decoder 将返回 AI Pipenode
 */
IJKFF_Pipeline *ffpipeline_create_from_ai(struct FFPlayer *ffp,
                                           ijkai_type ai_type,
                                           const char *model_path,
                                           int n_threads);

#endif
