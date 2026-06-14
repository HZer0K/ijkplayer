/*
 * ijkplayer_internal.h
 *
 * Copyright (c) 2013 Bilibili
 * Copyright (c) 2013 Zhang Rui <bbcallen@gmail.com>
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
 * ijkplayer_internal.h
 *
 * IjkMediaPlayer 内部结构定义。
 * 该结构体仅在 ijkplayer.c 内部使用，对外通过 ijkplayer.h 的
 * 不透明指针 (opaque pointer) 暴露，实现封装隔离。
 *
 * 线程安全: 所有公共 API 调用均通过 mutex 保护；ref_count 使用
 * __sync 原子内建函数进行无锁操作。
 */

#ifndef IJKPLAYER_ANDROID__IJKPLAYER_INTERNAL_H
#define IJKPLAYER_ANDROID__IJKPLAYER_INTERNAL_H

#include <assert.h>
#include "ijksdl/ijksdl.h"
#include "ff_fferror.h"
#include "ff_ffplay.h"
#include "ijkplayer.h"

/**
 * @struct IjkMediaPlayer
 * @brief  播放器实例的内部数据结构
 *
 * 生命周期由 ref_count 引用计数管理：
 *   ijkmp_create()  → ref_count = 1
 *   ijkmp_inc_ref() → ref_count++
 *   ijkmp_dec_ref() → ref_count--, 归零时调用 ijkmp_shutdown + ijkmp_destroy
 */
struct IjkMediaPlayer {
    volatile int ref_count;         /**< 原子引用计数，控制对象生命周期 */
    pthread_mutex_t mutex;          /**< 保护所有公共 API 调用的互斥锁 */
    FFPlayer *ffplayer;             /**< 底层 FFmpeg 播放器核心 (ff_ffplay) */

    int (*msg_loop)(void*);        /**< 消息循环回调函数 (由上层 JNI 注入) */
    SDL_Thread *msg_thread;        /**< 消息处理线程指针 (指向 _msg_thread) */
    SDL_Thread _msg_thread;        /**< 消息处理线程的实际存储空间 */

    int mp_state;                  /**< 当前播放器状态 (MP_STATE_* 宏定义) */
    char *data_source;             /**< 当前数据源 URL，由 ijkmp_set_data_source 设置 */
    void *weak_thiz;               /**< 弱引用回调对象 (通常为 Java 层 JNI 实例) */

    int restart;                   /**< 是否需要重启播放 (FFP_MSG_COMPLETED 后置 1) */
    int restart_from_beginning;    /**< 是否从头开始播放 (区别于暂停后恢复) */
    int seek_req;                  /**< 是否有未完成的 seek 请求 */
    long seek_msec;                /**< seek 目标位置 (毫秒) */
};

#endif
