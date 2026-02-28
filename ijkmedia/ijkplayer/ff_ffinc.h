/*
 * ff_ffinc.h
 *      ffmpeg headers
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

#ifndef FFPLAY__FF_FFINC_H
#define FFPLAY__FF_FFINC_H

#include <stdbool.h>
#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include "libavutil/avstring.h"
#include "libavutil/time.h"
#include "libavformat/avformat.h"
#include "libavcodec/avfft.h"
#include "libswscale/swscale.h"
#include "libavutil/base64.h"
#include "libavutil/error.h"
#include "libavutil/opt.h"
#include "libavutil/version.h"
#include "libswresample/swresample.h"

#include "ijksdl/ijksdl.h"

#if __has_include(<libavutil/application.h>)
#include <libavutil/application.h>
#else
#include "compat/libavutil/application.h"
#endif

typedef int (*ijk_inject_callback)(void *opaque, int type, void *data, size_t data_size);

#define FFP_OPT_CATEGORY_FORMAT 1
#define FFP_OPT_CATEGORY_CODEC  2
#define FFP_OPT_CATEGORY_SWS    3
#define FFP_OPT_CATEGORY_PLAYER 4
#define FFP_OPT_CATEGORY_SWR    5

static inline int av_dict_set_intptr(AVDictionary **pm, const char *key, intptr_t value, int flags) {
    return av_dict_set_int(pm, key, (int64_t)value, flags);
}
static inline void *av_dict_strtoptr(const char *str) {
    if (!str) return NULL;
    char *end = NULL;
    long long v = strtoll(str, &end, 10);
    (void)end;
    return (void*)(intptr_t)v;
}

/* FFmpeg 5+ removed av_lockmgr_register; provide minimal compatibility */
#ifndef AV_LOCK_CREATE
enum AVLockOp {
    AV_LOCK_CREATE = 0,
    AV_LOCK_OBTAIN,
    AV_LOCK_RELEASE,
    AV_LOCK_DESTROY
};
#endif
static inline int av_lockmgr_register(int (*cb)(void **mutex, enum AVLockOp op)) {
    (void)cb;
    return 0;
}

static inline void avcodec_register_all(void) {}
static inline void av_register_all(void) {}
#if !defined(AV_REGISTER_INPUT_FORMAT_STUB)
static inline void av_register_input_format(AVInputFormat *iformat) { (void)iformat; }
#define AV_REGISTER_INPUT_FORMAT_STUB 1
#endif

/* FFmpeg 5+ compatibility wrappers for removed codec helpers */
static inline void av_codec_set_pkt_timebase(AVCodecContext *avctx, AVRational tb) {
    if (avctx) avctx->pkt_timebase = tb;
}
static inline AVRational av_codec_get_pkt_timebase(const AVCodecContext *avctx) {
    return avctx ? avctx->pkt_timebase : (AVRational){0,1};
}
static inline int av_codec_get_max_lowres(const AVCodec *codec) {
    return codec ? codec->max_lowres : 0;
}
static inline void av_codec_set_lowres(AVCodecContext *avctx, int lowres) {
    if (avctx) avctx->lowres = lowres;
}

/* Removed API stubs */
static inline void av_packet_split_side_data(AVPacket *pkt) { (void)pkt; }
static inline int64_t av_frame_get_pkt_pos(const AVFrame *frame) { (void)frame; return -1; }

/* Cast helper for side-data size to match new API */
#ifndef IJK_AV_PACKET_GET_SIDE_DATA_COMPAT
#define IJK_AV_PACKET_GET_SIDE_DATA_COMPAT 1
#define av_packet_get_side_data(pkt, type, size_ptr) av_packet_get_side_data((pkt), (type), (size_t*)(size_ptr))
#endif

/* Decode wrapper on top of send/receive */
static inline int avcodec_decode_video2(AVCodecContext *avctx, AVFrame *picture, int *got_picture_ptr, const AVPacket *avpkt) {
    int ret = avcodec_send_packet(avctx, avpkt);
    if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) {
        return ret;
    }
    ret = avcodec_receive_frame(avctx, picture);
    if (ret == 0) {
        if (got_picture_ptr) *got_picture_ptr = 1;
        return 0;
    }
    if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
        if (got_picture_ptr) *got_picture_ptr = 0;
        return 0;
    }
    return ret;
}

static inline int avcodec_encode_video2(AVCodecContext *avctx, AVPacket *avpkt, const AVFrame *frame, int *got_packet_ptr) {
    int ret = avcodec_send_frame(avctx, frame);
    if (ret < 0 && ret != AVERROR(EAGAIN) && ret != AVERROR_EOF)
        return ret;
    ret = avcodec_receive_packet(avctx, avpkt);
    if (ret == 0) {
        if (got_packet_ptr) *got_packet_ptr = 1;
        return 0;
    }
    if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
        if (got_packet_ptr) *got_packet_ptr = 0;
        return 0;
    }
    return ret;
}

#endif
