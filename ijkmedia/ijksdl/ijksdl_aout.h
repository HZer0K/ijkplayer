/*****************************************************************************
 * ijksdl_aout.h
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
 * ijksdl_aout.h
 *
 * 音频输出抽象层。
 * 通过函数指针实现平台多态:
 *   - Android: AudioTrack 或 OpenSL ES 实现
 *   - IOS:     AudioQueue 实现
 *
 * 典型用法:
 *   由 Pipeline 的 func_open_audio_output 创建，
 *   FFPlayer 音频解码线程通过统一接口控制音频播放。
 */

#ifndef IJKSDL__IJKSDL_AOUT_H
#define IJKSDL__IJKSDL_AOUT_H

#include "ijksdl_audio.h"
#include "ijksdl_class.h"
#include "ijksdl_mutex.h"

typedef struct SDL_Aout_Opaque SDL_Aout_Opaque;
typedef struct SDL_Aout SDL_Aout;

/**
 * @struct SDL_Aout
 * @brief  音频输出抽象结构，通过函数指针实现平台多态
 */
struct SDL_Aout {
    SDL_mutex *mutex;
    double     minimal_latency_seconds;  /**< 最小音频延迟 (秒) */

    SDL_Class       *opaque_class;
    SDL_Aout_Opaque *opaque;
    void (*free_l)(SDL_Aout *vout);                                    /**< 析构回调 */
    int (*open_audio)(SDL_Aout *aout, const SDL_AudioSpec *desired, SDL_AudioSpec *obtained);  /**< 打开音频设备 */
    void (*pause_audio)(SDL_Aout *aout, int pause_on);                 /**< 暂停/恢复音频 */
    void (*flush_audio)(SDL_Aout *aout);                               /**< 清空音频缓冲 */
    void (*set_volume)(SDL_Aout *aout, float left, float right);       /**< 设置立体声音量 */
    void (*close_audio)(SDL_Aout *aout);                               /**< 关闭音频设备 */

    double (*func_get_latency_seconds)(SDL_Aout *aout);                /**< 获取当前音频延迟 */
    void   (*func_set_default_latency_seconds)(SDL_Aout *aout, double latency);  /**< 设置默认延迟 */

    /* 可选接口 */
    void   (*func_set_playback_rate)(SDL_Aout *aout, float playbackRate);       /**< 设置播放速率 (变速播放) */
    void   (*func_set_playback_volume)(SDL_Aout *aout, float playbackVolume);   /**< 设置播放音量 */
    int    (*func_get_audio_persecond_callbacks)(SDL_Aout *aout);               /**< 获取每秒音频回调次数 */

    /* Android 专用 */
    int    (*func_get_audio_session_id)(SDL_Aout *aout);                         /**< 获取音频会话 ID (用于音效处理) */
};

int SDL_AoutOpenAudio(SDL_Aout *aout, const SDL_AudioSpec *desired, SDL_AudioSpec *obtained);  /**< 打开音频设备 */
void SDL_AoutPauseAudio(SDL_Aout *aout, int pause_on);   /**< 暂停 (pause_on=1) 或恢复 (pause_on=0) */
void SDL_AoutFlushAudio(SDL_Aout *aout);                 /**< 清空音频缓冲 */
void SDL_AoutSetStereoVolume(SDL_Aout *aout, float left_volume, float right_volume);
void SDL_AoutCloseAudio(SDL_Aout *aout);                 /**< 关闭音频设备 */
void SDL_AoutFree(SDL_Aout *aout);                       /**< 释放音频输出实例 */
void SDL_AoutFreeP(SDL_Aout **paout);

double SDL_AoutGetLatencySeconds(SDL_Aout *aout);                 /**< 获取当前音频缓冲延迟 (秒) */
void   SDL_AoutSetDefaultLatencySeconds(SDL_Aout *aout, double latency);
int    SDL_AoutGetAudioPerSecondCallBacks(SDL_Aout *aout);

/* 可选接口 */
void   SDL_AoutSetPlaybackRate(SDL_Aout *aout, float playbackRate);     /**< 设置变速播放速率 */
void   SDL_AoutSetPlaybackVolume(SDL_Aout *aout, float volume);

/* Android 专用 */
int    SDL_AoutGetAudioSessionId(SDL_Aout *aout);                       /**< 获取 Android 音频会话 ID */

#endif
