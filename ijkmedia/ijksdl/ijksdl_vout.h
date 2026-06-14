/*****************************************************************************
 * ijksdl_vout.h
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
 * ijksdl_vout.h
 *
 * 视频输出抽象层。
 * 包含两个核心结构:
 *   - SDL_Vout:         视频输出设备抽象 (ANativeWindow/EGL/UIView)
 *   - SDL_VoutOverlay: 视频帧缓冲抽象 (用于 YUV/RGB 渲染)
 *
 * 典型用法:
 *   FFPlayer 创建 VoutOverlay -> 填充解码后的视频帧 -> 调用 display_overlay 显示。
 */

#ifndef IJKSDL__IJKSDL_VOUT_H
#define IJKSDL__IJKSDL_VOUT_H

#include "ijksdl_stdinc.h"
#include "ijksdl_class.h"
#include "ijksdl_mutex.h"
#include "ijksdl_video.h"
#include "ffmpeg/ijksdl_inc_ffmpeg.h"

typedef struct SDL_VoutOverlay_Opaque SDL_VoutOverlay_Opaque;
typedef struct SDL_VoutOverlay SDL_VoutOverlay;

/**
 * @struct SDL_VoutOverlay
 * @brief  视频帧缓冲抽象，封装解码后的视频帧数据
 */
struct SDL_VoutOverlay {
    int w;            /**< 视频宽度 (只读) */
    int h;            /**< 视频高度 (只读) */
    Uint32 format;    /**< 像素格式 (只读) */
    int planes;       /**< 平面数 (只读) */
    Uint16 *pitches;  /**< 每行字节数 (只读) */
    Uint8 **pixels;   /**< 像素数据指针 (读写) */

    int is_private;

    int sar_num;      /**< 样本宽高比分子 */
    int sar_den;      /**< 样本宽高比分母 */

    SDL_Class               *opaque_class;
    SDL_VoutOverlay_Opaque  *opaque;

    void    (*free_l)(SDL_VoutOverlay *overlay);           /**< 析构回调 */
    int     (*lock)(SDL_VoutOverlay *overlay);             /**< 锁定像素数据 */
    int     (*unlock)(SDL_VoutOverlay *overlay);           /**< 解锁像素数据 */
    void    (*unref)(SDL_VoutOverlay *overlay);            /**< 释放引用 */

    int     (*func_fill_frame)(SDL_VoutOverlay *overlay, const AVFrame *frame);  /**< 从 AVFrame 填充帧数据 */
};

typedef struct SDL_Vout_Opaque SDL_Vout_Opaque;
typedef struct SDL_Vout SDL_Vout;

/**
 * @struct SDL_Vout
 * @brief  视频输出设备抽象，通过函数指针实现平台多态
 */
struct SDL_Vout {
    SDL_mutex *mutex;

    SDL_Class       *opaque_class;
    SDL_Vout_Opaque *opaque;
    SDL_VoutOverlay *(*create_overlay)(int width, int height, int frame_format, SDL_Vout *vout);  /**< 创建视频帧缓冲 */
    void (*free_l)(SDL_Vout *vout);                   /**< 析构回调 */
    int (*display_overlay)(SDL_Vout *vout, SDL_VoutOverlay *overlay);  /**< 显示视频帧 */

    Uint32 overlay_format;  /**< 覆盖层像素格式 */
};

void SDL_VoutFree(SDL_Vout *vout);                  /**< 释放视频输出实例 */
void SDL_VoutFreeP(SDL_Vout **pvout);
int  SDL_VoutDisplayYUVOverlay(SDL_Vout *vout, SDL_VoutOverlay *overlay);  /**< 显示视频帧到屏幕 */
int  SDL_VoutSetOverlayFormat(SDL_Vout *vout, Uint32 overlay_format);       /**< 设置覆盖层像素格式 */

SDL_VoutOverlay *SDL_Vout_CreateOverlay(int width, int height, int frame_format, SDL_Vout *vout);  /**< 创建视频帧缓冲 */
int     SDL_VoutLockYUVOverlay(SDL_VoutOverlay *overlay);    /**< 锁定像素数据 */
int     SDL_VoutUnlockYUVOverlay(SDL_VoutOverlay *overlay);  /**< 解锁像素数据 */
void    SDL_VoutFreeYUVOverlay(SDL_VoutOverlay *overlay);    /**< 释放视频帧缓冲 */
void    SDL_VoutUnrefYUVOverlay(SDL_VoutOverlay *overlay);   /**< 释放引用 */
int     SDL_VoutFillFrameYUVOverlay(SDL_VoutOverlay *overlay, const AVFrame *frame);  /**< 从 AVFrame 填充帧数据 */

#endif
