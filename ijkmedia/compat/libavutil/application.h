/* compatibility stub for upstream FFmpeg where libavutil/application.h is absent */
#ifndef IJK_COMPAT_LIBAVUTIL_APPLICATION_H
#define IJK_COMPAT_LIBAVUTIL_APPLICATION_H

#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct AVApplicationContext {
    void *opaque;
    int (*func_on_app_event)(struct AVApplicationContext *h, int message, void *data, size_t size);
} AVApplicationContext;

/* Event codes (placeholders to keep ijkplayer logic intact) */
#define AVAPP_CTRL_WILL_HTTP_OPEN           0x2002
#define AVAPP_CTRL_WILL_LIVE_OPEN           0x2003
#define AVAPP_CTRL_WILL_CONCAT_SEGMENT_OPEN 0x2001
#define AVAPP_CTRL_WILL_TCP_OPEN            0x2010
#define AVAPP_CTRL_DID_TCP_OPEN             0x2011

#define AVAPP_EVENT_WILL_HTTP_OPEN          0x3001
#define AVAPP_EVENT_DID_HTTP_OPEN           0x3002
#define AVAPP_EVENT_WILL_HTTP_SEEK          0x3003
#define AVAPP_EVENT_DID_HTTP_SEEK           0x3004
#define AVAPP_EVENT_IO_TRAFFIC              0x3005
#define AVAPP_EVENT_ASYNC_STATISTIC         0x3006

typedef struct AVAppIOTraffic {
    int64_t bytes;
} AVAppIOTraffic;

typedef struct AVAppAsyncStatistic {
    int64_t buf_backwards;
    int64_t buf_forwards;
    int64_t buf_capacity;
} AVAppAsyncStatistic;

typedef struct AVAppAsyncReadSpeed {
    int64_t speed;
} AVAppAsyncReadSpeed;

typedef struct AVAppIOControl {
    char    url[4096];
    int     segment_index;
    int     retry_counter;
    int     is_handled;
    int     is_url_changed;
    int     size;
} AVAppIOControl;

typedef struct AVAppHttpEvent {
    char    url[4096];
    int64_t offset;
    int     error;
    int     http_code;
    int64_t filesize;
} AVAppHttpEvent;

typedef struct AVAppTcpIOControl {
    int     error;
    int     family;
    char    ip[64];
    int     port;
    int     fd;
} AVAppTcpIOControl;

static inline int av_application_open(AVApplicationContext **app_ctx, void *opaque) {
    if (!app_ctx) return -1;
    AVApplicationContext *ctx = (AVApplicationContext *)calloc(1, sizeof(AVApplicationContext));
    if (!ctx) return -1;
    ctx->opaque = opaque;
    *app_ctx = ctx;
    return 0;
}

static inline void av_application_closep(AVApplicationContext **app_ctx) {
    if (app_ctx && *app_ctx) {
        free(*app_ctx);
        *app_ctx = NULL;
    }
}

static inline int av_application_on_io_control(AVApplicationContext *app_ctx, int ctrl, void *data) {
    (void)app_ctx; (void)ctrl; (void)data;
    return 0;
}

static inline void av_application_on_async_statistic(AVApplicationContext *app_ctx, void *statistic) {
    (void)app_ctx; (void)statistic;
}

static inline void av_application_on_async_read_speed(AVApplicationContext *app_ctx, void *speed) {
    (void)app_ctx; (void)speed;
}

#ifdef __cplusplus
}
#endif

#endif /* IJK_COMPAT_LIBAVUTIL_APPLICATION_H */
