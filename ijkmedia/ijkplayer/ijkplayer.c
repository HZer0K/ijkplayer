/*
 * ijkplayer.c
 *
 * IjkMediaPlayer 核心实现。
 *
 * 本文件实现播放器的公共 API，作为上层 (JNI/Java) 和底层 FFPlayer 之间的
 * 桥接层。主要职责：
 *
 * 1. 状态机管理:
 *    通过 MP_STATE_* 宏定义 9 种播放状态，每个 API 调用前通过
 *    ikjmp_chkst_*_l() 检查当前状态是否允许该操作。
 *
 * 2. 线程安全:
 *    所有公共 API 均通过 pthread_mutex 保护，内部函数带 _l 后缀
 *    表示调用者已持有锁。
 *
 * 3. 消息循环:
 *    ijkmp_get_msg() 在独立线程 (ff_msg_loop) 中运行，处理来自
 *    FFPlayer 的事件消息 (FFP_MSG_*) 和控制请求 (FFP_REQ_*)。
 *    请求类消息在消息循环内部直接处理，不返回给上层。
 *
 * 4. 引用计数:
 *    通过 __sync 原子内建函数管理生命周期，dec_ref 归零时自动销毁。
 */
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

#include "ijkplayer.h"
#include "ijkplayer_internal.h"
#include "ijkversion.h"
#include "../ijkai/ijkai.h"
#include <stdio.h>
#include <string.h>
#include <inttypes.h>

/* 错误返回宏：若 ret 返回非零值，则立即返回该错误码 */
#define MP_RET_IF_FAILED(ret) \
    do { \
        int retval = ret; \
        if (retval != 0) return (retval); \
    } while(0)

/* 状态检查宏：若 real == expected，则返回 errcode (默认 EIJK_INVALID_STATE) */
#define MPST_RET_IF_EQ_INT(real, expected, errcode) \
    do { \
        if ((real) == (expected)) return (errcode); \
    } while(0)

#define MPST_RET_IF_EQ(real, expected) \
    MPST_RET_IF_EQ_INT(real, expected, EIJK_INVALID_STATE)

/* ==================== 内部析构 ==================== */

/* 析构播放器实例：等待消息线程结束、销毁互斥锁、释放内存 */
inline static void ijkmp_destroy(IjkMediaPlayer *mp)
{
    if (!mp)
        return;

    ffp_destroy_p(&mp->ffplayer);
    if (mp->msg_thread) {
        SDL_WaitThread(mp->msg_thread, NULL);
        mp->msg_thread = NULL;
    }

    pthread_mutex_destroy(&mp->mutex);

    freep((void**)&mp->data_source);
    memset(mp, 0, sizeof(IjkMediaPlayer));
    freep((void**)&mp);
}

inline static void ijkmp_destroy_p(IjkMediaPlayer **pmp)
{
    if (!pmp)
        return;

    ijkmp_destroy(*pmp);
    *pmp = NULL;
}

/* ==================== 全局初始化/配置 ==================== */

void ijkmp_global_init()
{
    ffp_global_init();
}

void ijkmp_global_uninit()
{
    ffp_global_uninit();
}

void ijkmp_global_set_log_report(int use_report)
{
    ffp_global_set_log_report(use_report);
}

void ijkmp_global_set_log_level(int log_level)
{
    ffp_global_set_log_level(log_level);
}

void ijkmp_global_set_inject_callback(ijk_inject_callback cb)
{
    ffp_global_set_inject_callback(cb);
}

const char *ijkmp_version()
{
    return IJKPLAYER_VERSION;
}

void ijkmp_io_stat_register(void (*cb)(const char *url, int type, int bytes))
{
    ffp_io_stat_register(cb);
}

void ijkmp_io_stat_complete_register(void (*cb)(const char *url,
                                                int64_t read_bytes, int64_t total_size,
                                                int64_t elpased_time, int64_t total_duration))
{
    ffp_io_stat_complete_register(cb);
}

/* 状态变更通知：更新内部状态并发送 FFP_MSG_PLAYBACK_STATE_CHANGED 消息 */
void ijkmp_change_state_l(IjkMediaPlayer *mp, int new_state)
{
    mp->mp_state = new_state;
    ffp_notify_msg1(mp->ffplayer, FFP_MSG_PLAYBACK_STATE_CHANGED);
}

/* ==================== 实例创建与配置 ==================== */

/**
 * 创建播放器实例。
 * @param msg_loop 消息循环回调，由上层 JNI 注入，在独立线程中运行
 * @return 新创建的播放器实例，ref_count=1；失败返回 NULL
 */
IjkMediaPlayer *ijkmp_create(int (*msg_loop)(void*))
{
    IjkMediaPlayer *mp = (IjkMediaPlayer *) mallocz(sizeof(IjkMediaPlayer));
    if (!mp)
        goto fail;

    mp->ffplayer = ffp_create();
    if (!mp->ffplayer)
        goto fail;

    mp->msg_loop = msg_loop;

    ijkmp_inc_ref(mp);
    pthread_mutex_init(&mp->mutex, NULL);

    return mp;

    fail:
    ijkmp_destroy_p(&mp);
    return NULL;
}

void *ijkmp_set_inject_opaque(IjkMediaPlayer *mp, void *opaque)
{
    assert(mp);

    MPTRACE("%s(%p)\n", __func__, opaque);
    void *prev_weak_thiz = ffp_set_inject_opaque(mp->ffplayer, opaque);
    MPTRACE("%s()=void\n", __func__);
    return prev_weak_thiz;
}

void ijkmp_set_frame_at_time(IjkMediaPlayer *mp, const char *path, int64_t start_time, int64_t end_time, int num, int definition)
{
    assert(mp);

    MPTRACE("%s(%s,%lld,%lld,%d,%d)\n", __func__, path, start_time, end_time, num, definition);
    ffp_set_frame_at_time(mp->ffplayer, path, start_time, end_time, num, definition);
    MPTRACE("%s()=void\n", __func__);
}


void *ijkmp_set_ijkio_inject_opaque(IjkMediaPlayer *mp, void *opaque)
{
    assert(mp);

    MPTRACE("%s(%p)\n", __func__, opaque);
    void *prev_weak_thiz = ffp_set_ijkio_inject_opaque(mp->ffplayer, opaque);
    MPTRACE("%s()=void\n", __func__);
    return prev_weak_thiz;
}

void ijkmp_set_option(IjkMediaPlayer *mp, int opt_category, const char *name, const char *value)
{
    assert(mp);

    // MPTRACE("%s(%s, %s)\n", __func__, name, value);
    pthread_mutex_lock(&mp->mutex);
    ffp_set_option(mp->ffplayer, opt_category, name, value);
    pthread_mutex_unlock(&mp->mutex);
    // MPTRACE("%s()=void\n", __func__);
}

void ijkmp_set_option_int(IjkMediaPlayer *mp, int opt_category, const char *name, int64_t value)
{
    assert(mp);

    // MPTRACE("%s(%s, %"PRId64")\n", __func__, name, value);
    pthread_mutex_lock(&mp->mutex);
    ffp_set_option_int(mp->ffplayer, opt_category, name, value);
    pthread_mutex_unlock(&mp->mutex);
    // MPTRACE("%s()=void\n", __func__);
}

void ijkmp_set_video_filter(IjkMediaPlayer *mp, const char *vfilter)
{
    assert(mp);
    pthread_mutex_lock(&mp->mutex);
    ffp_set_video_filter(mp->ffplayer, vfilter);
    pthread_mutex_unlock(&mp->mutex);
}

void ijkmp_set_scene_detect(IjkMediaPlayer *mp, const char *model_path)
{
    assert(mp);
    pthread_mutex_lock(&mp->mutex);

    FFPlayer *ffp = mp->ffplayer;
    if (!ffp) {
        pthread_mutex_unlock(&mp->mutex);
        return;
    }

    if (!model_path || model_path[0] == '\0') {
        /* Disable scene detection */
        ffp->scene_detect_enable = 0;
        if (ffp->scene_ctx) {
            ijkai_context *ai_ctx = (ijkai_context *)ffp->scene_ctx;
            ijkai_release(&ai_ctx);
            ffp->scene_ctx = NULL;
        }
    } else {
        /* Enable scene detection: initialize AI context with model */
        if (!ffp->scene_ctx) {
            ijkai_context *ai_ctx = ijkai_init(IJKAI_TYPE_CV_SCENE, model_path, 2);
            if (ai_ctx) {
                ffp->scene_ctx = ai_ctx;
                ffp->scene_detect_enable = 1;
                ffp->scene_last_time_ms = 0;
                printf("[IJKMP] Scene detection enabled: %s\n", model_path);
            } else {
                fprintf(stderr, "[IJKMP] Scene detection init failed: %s\n", model_path);
                ffp->scene_detect_enable = 0;
            }
        }
    }

    pthread_mutex_unlock(&mp->mutex);
}

int ijkmp_get_video_codec_info(IjkMediaPlayer *mp, char **codec_info)
{
    assert(mp);

    MPTRACE("%s\n", __func__);
    pthread_mutex_lock(&mp->mutex);
    int ret = ffp_get_video_codec_info(mp->ffplayer, codec_info);
    pthread_mutex_unlock(&mp->mutex);
    MPTRACE("%s()=void\n", __func__);
    return ret;
}

int ijkmp_get_audio_codec_info(IjkMediaPlayer *mp, char **codec_info)
{
    assert(mp);

    MPTRACE("%s\n", __func__);
    pthread_mutex_lock(&mp->mutex);
    int ret = ffp_get_audio_codec_info(mp->ffplayer, codec_info);
    pthread_mutex_unlock(&mp->mutex);
    MPTRACE("%s()=void\n", __func__);
    return ret;
}

int ijkmp_get_last_error_detail(IjkMediaPlayer *mp, char **detail)
{
    assert(mp);
    if (!detail)
        return EIJK_NULL_IS_PTR;

    *detail = NULL;
    pthread_mutex_lock(&mp->mutex);
    FFPlayer *ffp = mp->ffplayer;
    if (!ffp) {
        pthread_mutex_unlock(&mp->mutex);
        return EIJK_NULL_IS_PTR;
    }

    int err = ffp->last_error;
    int http_code = ffp->last_http_code;
    const char *url = ffp->last_error_url[0] ? ffp->last_error_url : NULL;
    const char *msg = ffp->last_error_detail[0] ? ffp->last_error_detail : NULL;
    const char *final_url = ffp->last_final_url[0] ? ffp->last_final_url : NULL;

    char buf[2300];
    buf[0] = '\0';
    if (http_code > 0) {
        snprintf(buf, sizeof(buf),
                 "http_code=%d\nffmpeg_err=%d\nurl=%s\nfinal_url=%s\nresp_http_code=%d\nresp_http_version=%s\nresp_mime_type=%s\nresp_content_length=%"PRId64"\nresp_set_cookie=%s\nresp_icy_headers=%s\ndetail=%s",
                 http_code,
                 err,
                 url ? url : "",
                 final_url ? final_url : "",
                 ffp->last_resp_http_code,
                 ffp->last_resp_http_version,
                 ffp->last_resp_mime_type,
                 ffp->last_resp_content_length,
                 ffp->last_resp_set_cookie,
                 ffp->last_resp_icy_headers,
                 msg ? msg : "");
    } else {
        snprintf(buf, sizeof(buf),
                 "ffmpeg_err=%d\nurl=%s\nfinal_url=%s\nresp_http_code=%d\nresp_http_version=%s\nresp_mime_type=%s\nresp_content_length=%"PRId64"\nresp_set_cookie=%s\nresp_icy_headers=%s\ndetail=%s",
                 err,
                 url ? url : "",
                 final_url ? final_url : "",
                 ffp->last_resp_http_code,
                 ffp->last_resp_http_version,
                 ffp->last_resp_mime_type,
                 ffp->last_resp_content_length,
                 ffp->last_resp_set_cookie,
                 ffp->last_resp_icy_headers,
                 msg ? msg : "");
    }

    *detail = strdup(buf);
    pthread_mutex_unlock(&mp->mutex);
    if (!*detail)
        return AVERROR(ENOMEM);
    return 0;
}

void ijkmp_set_playback_rate(IjkMediaPlayer *mp, float rate)
{
    assert(mp);

    MPTRACE("%s(%f)\n", __func__, rate);
    pthread_mutex_lock(&mp->mutex);
    ffp_set_playback_rate(mp->ffplayer, rate);
    pthread_mutex_unlock(&mp->mutex);
    MPTRACE("%s()=void\n", __func__);
}

void ijkmp_set_playback_volume(IjkMediaPlayer *mp, float volume)
{
    assert(mp);

    MPTRACE("%s(%f)\n", __func__, volume);
    pthread_mutex_lock(&mp->mutex);
    ffp_set_playback_volume(mp->ffplayer, volume);
    pthread_mutex_unlock(&mp->mutex);
    MPTRACE("%s()=void\n", __func__);
}

int ijkmp_set_stream_selected(IjkMediaPlayer *mp, int stream, int selected)
{
    assert(mp);

    MPTRACE("%s(%d, %d)\n", __func__, stream, selected);
    pthread_mutex_lock(&mp->mutex);
    int ret = ffp_set_stream_selected(mp->ffplayer, stream, selected);
    pthread_mutex_unlock(&mp->mutex);
    MPTRACE("%s(%d, %d)=%d\n", __func__, stream, selected, ret);
    return ret;
}

float ijkmp_get_property_float(IjkMediaPlayer *mp, int id, float default_value)
{
    assert(mp);

    pthread_mutex_lock(&mp->mutex);
    float ret = ffp_get_property_float(mp->ffplayer, id, default_value);
    pthread_mutex_unlock(&mp->mutex);
    return ret;
}

void ijkmp_set_property_float(IjkMediaPlayer *mp, int id, float value)
{
    assert(mp);

    pthread_mutex_lock(&mp->mutex);
    ffp_set_property_float(mp->ffplayer, id, value);
    pthread_mutex_unlock(&mp->mutex);
}

int64_t ijkmp_get_property_int64(IjkMediaPlayer *mp, int id, int64_t default_value)
{
    assert(mp);

    pthread_mutex_lock(&mp->mutex);
    int64_t ret = ffp_get_property_int64(mp->ffplayer, id, default_value);
    pthread_mutex_unlock(&mp->mutex);
    return ret;
}

void ijkmp_set_property_int64(IjkMediaPlayer *mp, int id, int64_t value)
{
    assert(mp);

    pthread_mutex_lock(&mp->mutex);
    ffp_set_property_int64(mp->ffplayer, id, value);
    pthread_mutex_unlock(&mp->mutex);
}

IjkMediaMeta *ijkmp_get_meta_l(IjkMediaPlayer *mp)
{
    assert(mp);

    MPTRACE("%s\n", __func__);
    IjkMediaMeta *ret = ffp_get_meta_l(mp->ffplayer);
    MPTRACE("%s()=void\n", __func__);
    return ret;
}

/* ==================== 生命周期管理 ==================== */

/**
 * 停止播放并等待内部线程结束。可安全多次调用。
 * 注意: 此函数可能阻塞当前线程。
 */
void ijkmp_shutdown_l(IjkMediaPlayer *mp)
{
    assert(mp);

    MPTRACE("ijkmp_shutdown_l()\n");
    if (mp->ffplayer) {
        ffp_stop_l(mp->ffplayer);
        ffp_wait_stop_l(mp->ffplayer);
    }
    MPTRACE("ijkmp_shutdown_l()=void\n");
}

void ijkmp_shutdown(IjkMediaPlayer *mp)
{
    return ijkmp_shutdown_l(mp);
}

/**
 * 增加引用计数。使用 __sync_fetch_and_add 原子操作，线程安全。
 */
void ijkmp_inc_ref(IjkMediaPlayer *mp)
{
    assert(mp);
    __sync_fetch_and_add(&mp->ref_count, 1);
}

/**
 * 减少引用计数。归零时自动执行 shutdown + destroy。
 * 使用 __sync_sub_and_fetch 原子操作，线程安全。
 * 注意: 可能阻塞 (内部调用 ijkmp_shutdown)。
 */
void ijkmp_dec_ref(IjkMediaPlayer *mp)
{
    if (!mp)
        return;

    int ref_count = __sync_sub_and_fetch(&mp->ref_count, 1);
    if (ref_count == 0) {
        MPTRACE("ijkmp_dec_ref(): ref=0\n");
        ijkmp_shutdown(mp);
        ijkmp_destroy_p(&mp);
    }
}

void ijkmp_dec_ref_p(IjkMediaPlayer **pmp)
{
    if (!pmp)
        return;

    ijkmp_dec_ref(*pmp);
    *pmp = NULL;
}

/* ==================== 播放控制 ==================== */

/**
 * 设置数据源 URL。
 * 状态检查: 仅允许从 MP_STATE_IDLE 调用，其他状态返回 EIJK_INVALID_STATE。
 * 成功后状态转换为 MP_STATE_INITIALIZED。
 */
static int ijkmp_set_data_source_l(IjkMediaPlayer *mp, const char *url)
{
    assert(mp);
    assert(url);

    // MPST_RET_IF_EQ(mp->mp_state, MP_STATE_IDLE);  // 仅允许 IDLE 状态
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_INITIALIZED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_ASYNC_PREPARING);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_PREPARED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_STARTED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_PAUSED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_COMPLETED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_STOPPED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_ERROR);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_END);

    freep((void**)&mp->data_source);
    mp->data_source = strdup(url);
    if (!mp->data_source)
        return EIJK_OUT_OF_MEMORY;

    ijkmp_change_state_l(mp, MP_STATE_INITIALIZED);
    return 0;
}

int ijkmp_set_data_source(IjkMediaPlayer *mp, const char *url)
{
    assert(mp);
    assert(url);
    MPTRACE("ijkmp_set_data_source(url=\"%s\")\n", url);
    pthread_mutex_lock(&mp->mutex);
    int retval = ijkmp_set_data_source_l(mp, url);
    pthread_mutex_unlock(&mp->mutex);
    MPTRACE("ijkmp_set_data_source(url=\"%s\")=%d\n", url, retval);
    return retval;
}

/* 消息循环线程入口函数，包装上层注入的 msg_loop 回调 */
static int ijkmp_msg_loop(void *arg)
{
    IjkMediaPlayer *mp = arg;
    int ret = mp->msg_loop(arg);
    return ret;
}

/**
 * 异步准备播放。
 * 状态检查: 仅允许从 MP_STATE_INITIALIZED 或 MP_STATE_STOPPED 调用。
 * 流程:
 *   1. 状态 -> ASYNC_PREPARING
 *   2. 启动消息队列和消息处理线程
 *   3. 调用 ffp_prepare_async_l 开始解封装
 *   4. 失败时状态 -> ERROR
 */
static int ijkmp_prepare_async_l(IjkMediaPlayer *mp)
{
    assert(mp);

    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_IDLE);
    // MPST_RET_IF_EQ(mp->mp_state, MP_STATE_INITIALIZED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_ASYNC_PREPARING);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_PREPARED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_STARTED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_PAUSED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_COMPLETED);
    // MPST_RET_IF_EQ(mp->mp_state, MP_STATE_STOPPED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_ERROR);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_END);

    assert(mp->data_source);

    ijkmp_change_state_l(mp, MP_STATE_ASYNC_PREPARING);

    msg_queue_start(&mp->ffplayer->msg_queue);

    // released in msg_loop
    ijkmp_inc_ref(mp);
    mp->msg_thread = SDL_CreateThreadEx(&mp->_msg_thread, ijkmp_msg_loop, mp, "ff_msg_loop");
    // msg_thread is detached inside msg_loop
    // TODO: 9 release weak_thiz if pthread_create() failed;

    int retval = ffp_prepare_async_l(mp->ffplayer, mp->data_source);
    if (retval < 0) {
        ijkmp_change_state_l(mp, MP_STATE_ERROR);
        return retval;
    }

    return 0;
}

int ijkmp_prepare_async(IjkMediaPlayer *mp)
{
    assert(mp);
    MPTRACE("ijkmp_prepare_async()\n");
    pthread_mutex_lock(&mp->mutex);
    int retval = ijkmp_prepare_async_l(mp);
    pthread_mutex_unlock(&mp->mutex);
    MPTRACE("ijkmp_prepare_async()=%d\n", retval);
    return retval;
}

/**
 * 检查当前状态是否允许 start 操作。
 * 允许: PREPARED / STARTED / PAUSED / COMPLETED
 * 拒绝: IDLE / INITIALIZED / ASYNC_PREPARING / STOPPED / ERROR / END
 */
static int ikjmp_chkst_start_l(int mp_state)
{
    MPST_RET_IF_EQ(mp_state, MP_STATE_IDLE);
    MPST_RET_IF_EQ(mp_state, MP_STATE_INITIALIZED);
    MPST_RET_IF_EQ(mp_state, MP_STATE_ASYNC_PREPARING);
    // MPST_RET_IF_EQ(mp_state, MP_STATE_PREPARED);     // 允许
    // MPST_RET_IF_EQ(mp_state, MP_STATE_STARTED);      // 允许
    // MPST_RET_IF_EQ(mp_state, MP_STATE_PAUSED);       // 允许
    // MPST_RET_IF_EQ(mp_state, MP_STATE_COMPLETED);    // 允许
    MPST_RET_IF_EQ(mp_state, MP_STATE_STOPPED);
    MPST_RET_IF_EQ(mp_state, MP_STATE_ERROR);
    MPST_RET_IF_EQ(mp_state, MP_STATE_END);

    return 0;
}

/* start 实现: 清除旧的 START/PAUSE 请求，发送 FFP_REQ_START 消息 */
static int ijkmp_start_l(IjkMediaPlayer *mp)
{
    assert(mp);

    MP_RET_IF_FAILED(ikjmp_chkst_start_l(mp->mp_state));

    ffp_remove_msg(mp->ffplayer, FFP_REQ_START);
    ffp_remove_msg(mp->ffplayer, FFP_REQ_PAUSE);
    ffp_notify_msg1(mp->ffplayer, FFP_REQ_START);

    return 0;
}

int ijkmp_start(IjkMediaPlayer *mp)
{
    assert(mp);
    MPTRACE("ijkmp_start()\n");
    pthread_mutex_lock(&mp->mutex);
    int retval = ijkmp_start_l(mp);
    pthread_mutex_unlock(&mp->mutex);
    MPTRACE("ijkmp_start()=%d\n", retval);
    return retval;
}

/**
 * 检查当前状态是否允许 pause 操作。
 * 允许: PREPARED / STARTED / PAUSED / COMPLETED
 */
static int ikjmp_chkst_pause_l(int mp_state)
{
    MPST_RET_IF_EQ(mp_state, MP_STATE_IDLE);
    MPST_RET_IF_EQ(mp_state, MP_STATE_INITIALIZED);
    MPST_RET_IF_EQ(mp_state, MP_STATE_ASYNC_PREPARING);
    // MPST_RET_IF_EQ(mp_state, MP_STATE_PREPARED);     // 允许
    // MPST_RET_IF_EQ(mp_state, MP_STATE_STARTED);      // 允许
    // MPST_RET_IF_EQ(mp_state, MP_STATE_PAUSED);       // 允许
    // MPST_RET_IF_EQ(mp_state, MP_STATE_COMPLETED);    // 允许
    MPST_RET_IF_EQ(mp_state, MP_STATE_STOPPED);
    MPST_RET_IF_EQ(mp_state, MP_STATE_ERROR);
    MPST_RET_IF_EQ(mp_state, MP_STATE_END);

    return 0;
}

/* pause 实现: 清除旧的 START/PAUSE 请求，发送 FFP_REQ_PAUSE 消息 */
static int ijkmp_pause_l(IjkMediaPlayer *mp)
{
    assert(mp);

    MP_RET_IF_FAILED(ikjmp_chkst_pause_l(mp->mp_state));

    ffp_remove_msg(mp->ffplayer, FFP_REQ_START);
    ffp_remove_msg(mp->ffplayer, FFP_REQ_PAUSE);
    ffp_notify_msg1(mp->ffplayer, FFP_REQ_PAUSE);

    return 0;
}

int ijkmp_pause(IjkMediaPlayer *mp)
{
    assert(mp);
    MPTRACE("ijkmp_pause()\n");
    pthread_mutex_lock(&mp->mutex);
    int retval = ijkmp_pause_l(mp);
    pthread_mutex_unlock(&mp->mutex);
    MPTRACE("ijkmp_pause()=%d\n", retval);
    return retval;
}

/**
 * 停止播放。
 * 允许状态: ASYNC_PREPARING ~ COMPLETED / STOPPED
 * 流程: 清除 START/PAUSE 请求 -> ffp_stop_l -> 状态转换为 STOPPED
 */
static int ijkmp_stop_l(IjkMediaPlayer *mp)
{
    assert(mp);

    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_IDLE);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_INITIALIZED);
    // MPST_RET_IF_EQ(mp->mp_state, MP_STATE_ASYNC_PREPARING);
    // MPST_RET_IF_EQ(mp->mp_state, MP_STATE_PREPARED);
    // MPST_RET_IF_EQ(mp->mp_state, MP_STATE_STARTED);
    // MPST_RET_IF_EQ(mp->mp_state, MP_STATE_PAUSED);
    // MPST_RET_IF_EQ(mp->mp_state, MP_STATE_COMPLETED);
    // MPST_RET_IF_EQ(mp->mp_state, MP_STATE_STOPPED);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_ERROR);
    MPST_RET_IF_EQ(mp->mp_state, MP_STATE_END);

    ffp_remove_msg(mp->ffplayer, FFP_REQ_START);
    ffp_remove_msg(mp->ffplayer, FFP_REQ_PAUSE);
    int retval = ffp_stop_l(mp->ffplayer);
    if (retval < 0) {
        return retval;
    }

    ijkmp_change_state_l(mp, MP_STATE_STOPPED);
    return 0;
}

int ijkmp_stop(IjkMediaPlayer *mp)
{
    assert(mp);
    MPTRACE("ijkmp_stop()\n");
    pthread_mutex_lock(&mp->mutex);
    int retval = ijkmp_stop_l(mp);
    pthread_mutex_unlock(&mp->mutex);
    MPTRACE("ijkmp_stop()=%d\n", retval);
    return retval;
}

bool ijkmp_is_playing(IjkMediaPlayer *mp)
{
    assert(mp);
    if (mp->mp_state == MP_STATE_PREPARED ||
        mp->mp_state == MP_STATE_STARTED) {
        return true;
    }

    return false;
}

/**
 * 检查当前状态是否允许 seek 操作。
 * 允许: PREPARED / STARTED / PAUSED / COMPLETED
 */
static int ikjmp_chkst_seek_l(int mp_state)
{
    MPST_RET_IF_EQ(mp_state, MP_STATE_IDLE);
    MPST_RET_IF_EQ(mp_state, MP_STATE_INITIALIZED);
    MPST_RET_IF_EQ(mp_state, MP_STATE_ASYNC_PREPARING);
    // MPST_RET_IF_EQ(mp_state, MP_STATE_PREPARED);     // 允许
    // MPST_RET_IF_EQ(mp_state, MP_STATE_STARTED);      // 允许
    // MPST_RET_IF_EQ(mp_state, MP_STATE_PAUSED);       // 允许
    // MPST_RET_IF_EQ(mp_state, MP_STATE_COMPLETED);    // 允许
    MPST_RET_IF_EQ(mp_state, MP_STATE_STOPPED);
    MPST_RET_IF_EQ(mp_state, MP_STATE_ERROR);
    MPST_RET_IF_EQ(mp_state, MP_STATE_END);

    return 0;
}

/* seek 实现: 记录 seek 目标，清除旧 SEEK 请求，发送 FFP_REQ_SEEK 消息 */
int ijkmp_seek_to_l(IjkMediaPlayer *mp, long msec)
{
    assert(mp);

    MP_RET_IF_FAILED(ikjmp_chkst_seek_l(mp->mp_state));

    mp->seek_req = 1;
    mp->seek_msec = msec;
    ffp_remove_msg(mp->ffplayer, FFP_REQ_SEEK);
    ffp_notify_msg2(mp->ffplayer, FFP_REQ_SEEK, (int)msec);
    // TODO: 9 64-bit long?

    return 0;
}

int ijkmp_seek_to(IjkMediaPlayer *mp, long msec)
{
    assert(mp);
    MPTRACE("ijkmp_seek_to(%ld)\n", msec);
    pthread_mutex_lock(&mp->mutex);
    int retval = ijkmp_seek_to_l(mp, msec);
    pthread_mutex_unlock(&mp->mutex);
    MPTRACE("ijkmp_seek_to(%ld)=%d\n", msec, retval);

    return retval;
}

int ijkmp_get_state(IjkMediaPlayer *mp)
{
    return mp->mp_state;
}

/* ==================== 位置/时长查询 ==================== */

static long ijkmp_get_current_position_l(IjkMediaPlayer *mp)
{
    /* 若有未完成的 seek 请求，返回 seek 目标位置而非实际播放位置 */
    if (mp->seek_req)
        return mp->seek_msec;
    return ffp_get_current_position_l(mp->ffplayer);
}

long ijkmp_get_current_position(IjkMediaPlayer *mp)
{
    assert(mp);
    pthread_mutex_lock(&mp->mutex);
    long retval;
    if (mp->seek_req)
        retval = mp->seek_msec;
    else
        retval = ijkmp_get_current_position_l(mp);
    pthread_mutex_unlock(&mp->mutex);
    return retval;
}

static long ijkmp_get_duration_l(IjkMediaPlayer *mp)
{
    return ffp_get_duration_l(mp->ffplayer);
}

long ijkmp_get_duration(IjkMediaPlayer *mp)
{
    assert(mp);
    pthread_mutex_lock(&mp->mutex);
    long retval = ijkmp_get_duration_l(mp);
    pthread_mutex_unlock(&mp->mutex);
    return retval;
}

static long ijkmp_get_playable_duration_l(IjkMediaPlayer *mp)
{
    return ffp_get_playable_duration_l(mp->ffplayer);
}

long ijkmp_get_playable_duration(IjkMediaPlayer *mp)
{
    assert(mp);
    pthread_mutex_lock(&mp->mutex);
    long retval = ijkmp_get_playable_duration_l(mp);
    pthread_mutex_unlock(&mp->mutex);
    return retval;
}

void ijkmp_set_loop(IjkMediaPlayer *mp, int loop)
{
    assert(mp);
    pthread_mutex_lock(&mp->mutex);
    ffp_set_loop(mp->ffplayer, loop);
    pthread_mutex_unlock(&mp->mutex);
}

int ijkmp_get_loop(IjkMediaPlayer *mp)
{
    assert(mp);
    pthread_mutex_lock(&mp->mutex);
    int loop = ffp_get_loop(mp->ffplayer);
    pthread_mutex_unlock(&mp->mutex);
    return loop;
}

void *ijkmp_get_weak_thiz(IjkMediaPlayer *mp)
{
    return mp->weak_thiz;
}

void *ijkmp_set_weak_thiz(IjkMediaPlayer *mp, void *weak_thiz)
{
    void *prev_weak_thiz = mp->weak_thiz;

    mp->weak_thiz = weak_thiz;

    return prev_weak_thiz;
}

/* ==================== 消息处理 ==================== */

/**
 * 从消息队列获取事件。
 *
 * 消息分为两类:
 *   - 事件消息 (FFP_MSG_*): 返回给上层处理
 *     - FFP_MSG_PREPARED:     异步准备完成，状态 -> PREPARED
 *     - FFP_MSG_COMPLETED:    播放完成，状态 -> COMPLETED，设置 restart 标志
 *     - FFP_MSG_SEEK_COMPLETE: seek 完成，清除 seek_req/seek_msec
 *
 *   - 请求消息 (FFP_REQ_*): 在消息循环内部直接处理，不返回给上层
 *     - FFP_REQ_START:  根据 restart 标志从头播放或恢复播放，状态 -> STARTED
 *     - FFP_REQ_PAUSE:  暂停播放，状态 -> PAUSED
 *     - FFP_REQ_SEEK:   执行实际 seek 操作
 *
 * 请求消息处理后设置 continue_wait_next_msg=1，继续循环等待下一个事件消息。
 *
 * @param mp    播放器实例
 * @param msg   输出消息结构
 * @param block 1=阻塞等待，0=非阻塞
 * @return <0 中止，0 无消息，>0 有消息
 */
int ijkmp_get_msg(IjkMediaPlayer *mp, AVMessage *msg, int block)
{
    assert(mp);
    while (1) {
        int continue_wait_next_msg = 0;
        int retval = msg_queue_get(&mp->ffplayer->msg_queue, msg, block);
        if (retval <= 0)
            return retval;

        switch (msg->what) {
        case FFP_MSG_PREPARED:
            MPTRACE("ijkmp_get_msg: FFP_MSG_PREPARED\n");
            pthread_mutex_lock(&mp->mutex);
            if (mp->mp_state == MP_STATE_ASYNC_PREPARING) {
                ijkmp_change_state_l(mp, MP_STATE_PREPARED);
            } else {
                // FIXME: 1: onError() ?
                av_log(mp->ffplayer, AV_LOG_DEBUG, "FFP_MSG_PREPARED: expecting mp_state==MP_STATE_ASYNC_PREPARING\n");
            }
            if (!mp->ffplayer->start_on_prepared) {
                ijkmp_change_state_l(mp, MP_STATE_PAUSED);
            }
            pthread_mutex_unlock(&mp->mutex);
            break;

        case FFP_MSG_COMPLETED:
            MPTRACE("ijkmp_get_msg: FFP_MSG_COMPLETED\n");

            pthread_mutex_lock(&mp->mutex);
            mp->restart = 1;
            mp->restart_from_beginning = 1;
            ijkmp_change_state_l(mp, MP_STATE_COMPLETED);
            pthread_mutex_unlock(&mp->mutex);
            break;

        case FFP_MSG_SEEK_COMPLETE:
            MPTRACE("ijkmp_get_msg: FFP_MSG_SEEK_COMPLETE\n");

            pthread_mutex_lock(&mp->mutex);
            mp->seek_req = 0;
            mp->seek_msec = 0;
            pthread_mutex_unlock(&mp->mutex);
            break;

        case FFP_REQ_START:
            MPTRACE("ijkmp_get_msg: FFP_REQ_START\n");
            continue_wait_next_msg = 1;
            pthread_mutex_lock(&mp->mutex);
            if (0 == ikjmp_chkst_start_l(mp->mp_state)) {
                // FIXME: 8 check seekable
                if (mp->restart) {
                    if (mp->restart_from_beginning) {
                        av_log(mp->ffplayer, AV_LOG_DEBUG, "ijkmp_get_msg: FFP_REQ_START: restart from beginning\n");
                        retval = ffp_start_from_l(mp->ffplayer, 0);
                        if (retval == 0)
                            ijkmp_change_state_l(mp, MP_STATE_STARTED);
                    } else {
                        av_log(mp->ffplayer, AV_LOG_DEBUG, "ijkmp_get_msg: FFP_REQ_START: restart from seek pos\n");
                        retval = ffp_start_l(mp->ffplayer);
                        if (retval == 0)
                            ijkmp_change_state_l(mp, MP_STATE_STARTED);
                    }
                    mp->restart = 0;
                    mp->restart_from_beginning = 0;
                } else {
                    av_log(mp->ffplayer, AV_LOG_DEBUG, "ijkmp_get_msg: FFP_REQ_START: start on fly\n");
                    retval = ffp_start_l(mp->ffplayer);
                    if (retval == 0)
                        ijkmp_change_state_l(mp, MP_STATE_STARTED);
                }
            }
            pthread_mutex_unlock(&mp->mutex);
            break;

        case FFP_REQ_PAUSE:
            MPTRACE("ijkmp_get_msg: FFP_REQ_PAUSE\n");
            continue_wait_next_msg = 1;
            pthread_mutex_lock(&mp->mutex);
            if (0 == ikjmp_chkst_pause_l(mp->mp_state)) {
                int pause_ret = ffp_pause_l(mp->ffplayer);
                if (pause_ret == 0)
                    ijkmp_change_state_l(mp, MP_STATE_PAUSED);
            }
            pthread_mutex_unlock(&mp->mutex);
            break;

        case FFP_REQ_SEEK:
            MPTRACE("ijkmp_get_msg: FFP_REQ_SEEK\n");
            continue_wait_next_msg = 1;

            pthread_mutex_lock(&mp->mutex);
            if (0 == ikjmp_chkst_seek_l(mp->mp_state)) {
                mp->restart_from_beginning = 0;
                if (0 == ffp_seek_to_l(mp->ffplayer, msg->arg1)) {
                    av_log(mp->ffplayer, AV_LOG_DEBUG, "ijkmp_get_msg: FFP_REQ_SEEK: seek to %d\n", (int)msg->arg1);
                }
            }
            pthread_mutex_unlock(&mp->mutex);
            break;
        }
        if (continue_wait_next_msg) {
            msg_free_res(msg);
            continue;
        }

        return retval;
    }

    return -1;
}
