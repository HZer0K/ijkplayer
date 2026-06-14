/*****************************************************************************
 * ijksdl.h
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
 * ijksdl.h
 *
 * IJK SDL 抽象层主头文件。
 *
 * 本模块提供平台无关的系统抽象层，类似于 SDL 库的角色，
 * 但针对移动平台做了精简和定制。封装了以下平台相关功能:
 *
 *   - 互斥锁/条件变量 (ijksdl_mutex.h) — pthread 封装
 *   - 线程管理 (ijksdl_thread.h) — pthread 封装
 *   - 音频输出 (ijksdl_aout.h) — AudioTrack/OpenSL ES
 *   - 视频输出 (ijksdl_vout.h) — ANativeWindow/EGL
 *   - 计时器 (ijksdl_timer.h) — 高精度时钟
 *
 * 各平台 (Android/IOS) 提供各自的实现，上层 FFPlayer 通过统一接口调用。
 */

#ifndef IJKSDL__IJKSDL_H
#define IJKSDL__IJKSDL_H

#include "ijksdl_audio.h"
#include "ijksdl_aout.h"
#include "ijksdl_class.h"
#include "ijksdl_error.h"
#include "ijksdl_log.h"
#include "ijksdl_misc.h"
#include "ijksdl_mutex.h"
#include "ijksdl_thread.h"
#include "ijksdl_timer.h"
#include "ijksdl_video.h"
#include "ijksdl_vout.h"

#include "ffmpeg/ijksdl_vout_overlay_ffmpeg.h"

#endif
