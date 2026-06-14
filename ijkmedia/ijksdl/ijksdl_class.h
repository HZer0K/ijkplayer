/*****************************************************************************
 * ijksdl_class.h
 *****************************************************************************
 *
 * Copyright (c) 2013 Bilibili
 * copyright (c) 2013 Zhang Rui <bbcallen@gmail.com>
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
 * ijksdl_class.h
 *
 * 日志类标识结构。
 * 用于 av_log 输出时标识日志来源，类似于 FFmpeg 的 AVClass。
 * Pipeline/Pipenode/Aout/Vout 等结构均包含 SDL_Class 指针作为首字段。
 */

#ifndef IJKSDL__IJKSDL_CLASS_H
#define IJKSDL__IJKSDL_CLASS_H

typedef struct SDL_Class {
    const char *name;  /**< 组件名称，用于日志输出前缀 */
} SDL_Class;

#endif
