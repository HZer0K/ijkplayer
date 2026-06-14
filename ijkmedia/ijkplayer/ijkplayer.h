/*
 * ijkplayer.h
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
 * ijkplayer.h
 *
 * IjkMediaPlayer 公共 API 头文件。
 * 提供播放器的不透明指针接口，上层通过 JNI 调用这些函数来控制播放。
 *
 * 状态机:
 *   IDLE -> INITIALIZED -> ASYNC_PREPARING -> PREPARED -> STARTED
 *                                                     -> PAUSED
 *                                                     -> COMPLETED
 *                                                     -> STOPPED
 *                                                     -> ERROR
 *   任意状态 -> END (通过 ijkmp_release)
 *
 * 线程安全:
 *   所有公共函数均为线程安全，内部通过 pthread_mutex 保护。
 *   ref_count 使用原子操作 (__sync_fetch_and_add) 实现无锁引用计数。
 *
 * 消息机制:
 *   上层通过 ijkmp_get_msg() 从消息队列获取事件 (PREPARED/COMPLETED/SEEK_COMPLETE)，
 *   并在消息循环中处理播放控制请求 (FFP_REQ_START/PAUSE/SEEK)。
 */

#ifndef IJKPLAYER_ANDROID__IJKPLAYER_H
#define IJKPLAYER_ANDROID__IJKPLAYER_H

#include <stdbool.h>
#include "ff_ffmsg_queue.h"

#include "ijkmeta.h"

#ifndef MPTRACE
#define MPTRACE ALOGD
#endif

typedef struct IjkMediaPlayer IjkMediaPlayer;
struct FFPlayer;
struct SDL_Vout;

/* ==================== 播放器状态定义 ====================
 *
 * 状态转换规则:
 *   - 带注释的箭头表示由对应 API 触发的状态转换
 *   - "self" 表示状态不变
 *   - "..." 表示由内部事件触发的转换
 *
 * 状态检查宏:
 *   MPST_CHECK_NOT_RET(state, MP_STATE_xxx) — 检查并跳过不允许的状态
 *   MPST_RET_IF_EQ(state, MP_STATE_xxx)     — 若状态匹配则返回 EIJK_INVALID_STATE
 */

/*-
 * ijkmp_set_data_source()  -> MP_STATE_INITIALIZED
 *
 * ijkmp_reset              -> self
 * ijkmp_release            -> MP_STATE_END
 */
#define MP_STATE_IDLE               0

/*-
 * ijkmp_prepare_async()    -> MP_STATE_ASYNC_PREPARING
 *
 * ijkmp_reset              -> MP_STATE_IDLE
 * ijkmp_release            -> MP_STATE_END
 */
#define MP_STATE_INITIALIZED        1

/*-
 *                   ...    -> MP_STATE_PREPARED
 *                   ...    -> MP_STATE_ERROR
 *
 * ijkmp_reset              -> MP_STATE_IDLE
 * ijkmp_release            -> MP_STATE_END
 */
#define MP_STATE_ASYNC_PREPARING    2

/*-
 * ijkmp_seek_to()          -> self
 * ijkmp_start()            -> MP_STATE_STARTED
 *
 * ijkmp_reset              -> MP_STATE_IDLE
 * ijkmp_release            -> MP_STATE_END
 */
#define MP_STATE_PREPARED           3

/*-
 * ijkmp_seek_to()          -> self
 * ijkmp_start()            -> self
 * ijkmp_pause()            -> MP_STATE_PAUSED
 * ijkmp_stop()             -> MP_STATE_STOPPED
 *                   ...    -> MP_STATE_COMPLETED
 *                   ...    -> MP_STATE_ERROR
 *
 * ijkmp_reset              -> MP_STATE_IDLE
 * ijkmp_release            -> MP_STATE_END
 */
#define MP_STATE_STARTED            4

/*-
 * ijkmp_seek_to()          -> self
 * ijkmp_start()            -> MP_STATE_STARTED
 * ijkmp_pause()            -> self
 * ijkmp_stop()             -> MP_STATE_STOPPED
 *
 * ijkmp_reset              -> MP_STATE_IDLE
 * ijkmp_release            -> MP_STATE_END
 */
#define MP_STATE_PAUSED             5

/*-
 * ijkmp_seek_to()          -> self
 * ijkmp_start()            -> MP_STATE_STARTED (from beginning)
 * ijkmp_pause()            -> self
 * ijkmp_stop()             -> MP_STATE_STOPPED
 *
 * ijkmp_reset              -> MP_STATE_IDLE
 * ijkmp_release            -> MP_STATE_END
 */
#define MP_STATE_COMPLETED          6

/*-
 * ijkmp_stop()             -> self
 * ijkmp_prepare_async()    -> MP_STATE_ASYNC_PREPARING
 *
 * ijkmp_reset              -> MP_STATE_IDLE
 * ijkmp_release            -> MP_STATE_END
 */
#define MP_STATE_STOPPED            7

/*-
 * ijkmp_reset              -> MP_STATE_IDLE
 * ijkmp_release            -> MP_STATE_END
 */
#define MP_STATE_ERROR              8

/*-
 * ijkmp_release            -> self
 */
#define MP_STATE_END                9



#define IJKMP_IO_STAT_READ 1


/* ==================== 配置选项类别 ==================== */
#define IJKMP_OPT_CATEGORY_FORMAT FFP_OPT_CATEGORY_FORMAT   /**< 封装格式选项 (如 probesize, analyzeduration) */
#define IJKMP_OPT_CATEGORY_CODEC  FFP_OPT_CATEGORY_CODEC    /**< 编解码器选项 (如 threads, skip_loop_filter) */
#define IJKMP_OPT_CATEGORY_SWS    FFP_OPT_CATEGORY_SWS      /**< 视频缩放选项 (libswscale) */
#define IJKMP_OPT_CATEGORY_PLAYER FFP_OPT_CATEGORY_PLAYER   /**< 播放器特有选项 (如 packet-buffering, mediacodec) */
#define IJKMP_OPT_CATEGORY_SWR    FFP_OPT_CATEGORY_SWR      /**< 音频重采样选项 (libswresample) */


/* ==================== 全局初始化/配置 ==================== */
void            ijkmp_global_init();
void            ijkmp_global_uninit();
void            ijkmp_global_set_log_report(int use_report);
void            ijkmp_global_set_log_level(int log_level);   /**< log_level = AV_LOG_xxx */
void            ijkmp_global_set_inject_callback(ijk_inject_callback cb);
const char     *ijkmp_version();
void            ijkmp_io_stat_register(void (*cb)(const char *url, int type, int bytes));
void            ijkmp_io_stat_complete_register(void (*cb)(const char *url,
                                                           int64_t read_bytes, int64_t total_size,
                                                           int64_t elpased_time, int64_t total_duration));

/* ==================== 实例创建与配置 ==================== */
IjkMediaPlayer *ijkmp_create(int (*msg_loop)(void*));   /**< 创建播放器实例，ref_count 初始为 1 */
void*            ijkmp_set_inject_opaque(IjkMediaPlayer *mp, void *opaque);
void*            ijkmp_set_ijkio_inject_opaque(IjkMediaPlayer *mp, void *opaque);

void            ijkmp_set_option(IjkMediaPlayer *mp, int opt_category, const char *name, const char *value);
void            ijkmp_set_option_int(IjkMediaPlayer *mp, int opt_category, const char *name, int64_t value);
void            ijkmp_set_video_filter(IjkMediaPlayer *mp, const char *vfilter);

/* ==================== 媒体信息查询 ==================== */
int             ijkmp_get_video_codec_info(IjkMediaPlayer *mp, char **codec_info);
int             ijkmp_get_audio_codec_info(IjkMediaPlayer *mp, char **codec_info);
int             ijkmp_get_last_error_detail(IjkMediaPlayer *mp, char **detail);
void            ijkmp_set_playback_rate(IjkMediaPlayer *mp, float rate);
void            ijkmp_set_playback_volume(IjkMediaPlayer *mp, float rate);

int             ijkmp_set_stream_selected(IjkMediaPlayer *mp, int stream, int selected);

/* ==================== 属性读写 ==================== */
float           ijkmp_get_property_float(IjkMediaPlayer *mp, int id, float default_value);
void            ijkmp_set_property_float(IjkMediaPlayer *mp, int id, float value);
int64_t         ijkmp_get_property_int64(IjkMediaPlayer *mp, int id, int64_t default_value);
void            ijkmp_set_property_int64(IjkMediaPlayer *mp, int id, int64_t value);

/* ==================== 元数据 ==================== */
IjkMediaMeta   *ijkmp_get_meta_l(IjkMediaPlayer *mp);   /**< 获取媒体元数据，返回值需用 free() 释放 */

/* ==================== 生命周期管理 ==================== */
void            ijkmp_shutdown(IjkMediaPlayer *mp);      /**< 停止播放并等待线程结束，可多次调用，可能阻塞 */

void            ijkmp_inc_ref(IjkMediaPlayer *mp);        /**< 增加引用计数 (原子操作) */
void            ijkmp_dec_ref(IjkMediaPlayer *mp);        /**< 减少引用计数，归零时自动 shutdown + destroy，可能阻塞 */
void            ijkmp_dec_ref_p(IjkMediaPlayer **pmp);    /**< dec_ref 并将指针置空 */

/* ==================== 播放控制 ==================== */
int             ijkmp_set_data_source(IjkMediaPlayer *mp, const char *url);  /**< IDLE -> INITIALIZED */
int             ijkmp_prepare_async(IjkMediaPlayer *mp);  /**< INITIALIZED -> ASYNC_PREPARING */
int             ijkmp_start(IjkMediaPlayer *mp);          /**< PREPARED/PAUSED/COMPLETED/STARTED -> STARTED */
int             ijkmp_pause(IjkMediaPlayer *mp);          /**< STARTED/PAUSED/COMPLETED -> PAUSED */
int             ijkmp_stop(IjkMediaPlayer *mp);           /**< ASYNC_PREPARING~COMPLETED/STOPPED -> STOPPED */
int             ijkmp_seek_to(IjkMediaPlayer *mp, long msec);
int             ijkmp_get_state(IjkMediaPlayer *mp);
bool            ijkmp_is_playing(IjkMediaPlayer *mp);     /**< PREPARED 或 STARTED 时返回 true */
long            ijkmp_get_current_position(IjkMediaPlayer *mp);
long            ijkmp_get_duration(IjkMediaPlayer *mp);
long            ijkmp_get_playable_duration(IjkMediaPlayer *mp);
void            ijkmp_set_loop(IjkMediaPlayer *mp, int loop);
int             ijkmp_get_loop(IjkMediaPlayer *mp);

/* ==================== 弱引用与消息 ==================== */
void           *ijkmp_get_weak_thiz(IjkMediaPlayer *mp);
void           *ijkmp_set_weak_thiz(IjkMediaPlayer *mp, void *weak_thiz);

int             ijkmp_get_msg(IjkMediaPlayer *mp, AVMessage *msg, int block);  /**< 从消息队列取事件，需用 msg_free_res 释放 */
void            ijkmp_set_frame_at_time(IjkMediaPlayer *mp, const char *path, int64_t start_time, int64_t end_time, int num, int definition);

#endif
