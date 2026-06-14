/*
 * Copyright (c) 2016 Bilibili
 * Copyright (c) 2016 Raymond Zheng <raymondzheng1412@gmail.com>
 *
 * This file is part of ijkplayer.
 *
 * ijkplayer is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * ijkplayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with ijkplayer; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

/*
 * ijkthreadpool.c
 *
 * 通用线程池实现。
 *
 * 工作原理:
 *   工作线程在 cond_wait 中等待，有新任务或关闭信号时被唤醒。
 *   任务队列采用环形缓冲区，队列满时自动扩容 (2倍增长)。
 *   关闭模式:
 *     - IJK_IMMEDIATE_SHUTDOWN: 线程立即退出，丢弃未处理任务
 *     - IJK_LEISURELY_SHUTDOWN: 线程处理完队列中所有任务后退出
 */

#include "ijkthreadpool.h"
#include "libavutil/log.h"

#include <stdlib.h>
#include <unistd.h>

/**
 * 工作线程入口函数。
 * 循环等待任务：cond_wait 阻塞 -> 取出任务 -> 执行任务。
 * 收到关闭信号时退出循环。
 */
static void *ijk_threadpool_thread(void *pool_ctx)
{
    IjkThreadPoolContext *ctx = (IjkThreadPoolContext *)pool_ctx;
    IjkThreadPoolTask task;

    for(;;) {
        pthread_mutex_lock(&(ctx->lock));

        while((ctx->pending_count == 0) && (!ctx->shutdown)) {
            pthread_cond_wait(&(ctx->notify), &(ctx->lock));
        }

        if((ctx->shutdown == IJK_IMMEDIATE_SHUTDOWN) ||
           ((ctx->shutdown == IJK_LEISURELY_SHUTDOWN) &&
            (ctx->pending_count == 0))) {
               break;
           }

        /* Grab our task */
        task.function = ctx->queue[ctx->queue_head].function;
        task.in_arg   = ctx->queue[ctx->queue_head].in_arg;
        task.out_arg  = ctx->queue[ctx->queue_head].out_arg;
        ctx->queue_head = (ctx->queue_head + 1) % ctx->queue_size;
        ctx->pending_count -= 1;

        pthread_mutex_unlock(&(ctx->lock));

        (*(task.function))(task.in_arg, task.out_arg);
    }

    ctx->started_count--;

    pthread_mutex_unlock(&(ctx->lock));
    pthread_exit(NULL);
    return(NULL);
}

/**
 * 释放线程池内存。
 * 必须在所有工作线程退出后调用 (started_count == 0)。
 */
int ijk_threadpool_free(IjkThreadPoolContext *ctx)
{
    if(ctx == NULL || ctx->started_count > 0) {
        return -1;
    }

    /* Did we manage to allocate ? */
    if(ctx->threads) {
        free(ctx->threads);
        free(ctx->queue);

        /* Because we allocate pool->threads after initializing the
         mutex and condition variable, we're sure they're
         initialized. Let's lock the mutex just in case. */
        pthread_mutex_lock(&(ctx->lock));
        pthread_mutex_destroy(&(ctx->lock));
        pthread_cond_destroy(&(ctx->notify));
    }
    free(ctx);
    return 0;
}

/**
 * 创建线程池。
 * @param thread_count  工作线程数 (1 ~ MAX_THREADS)
 * @param queue_size    任务队列初始容量 (1 ~ MAX_QUEUE)
 * @param flags         保留参数
 * @return 线程池实例，失败返回 NULL
 */
IjkThreadPoolContext *ijk_threadpool_create(int thread_count, int queue_size, int flags)
{
    IjkThreadPoolContext *ctx;
    int i;

    if(thread_count <= 0 || thread_count > MAX_THREADS || queue_size <= 0 || queue_size > MAX_QUEUE) {
        return NULL;
    }

    if((ctx = (IjkThreadPoolContext *)calloc(1, sizeof(IjkThreadPoolContext))) == NULL) {
        goto err;
    }

    ctx->queue_size = queue_size;

    /* Allocate thread and task queue */
    ctx->threads = (pthread_t *)calloc(1, sizeof(pthread_t) * thread_count);
    ctx->queue = (IjkThreadPoolTask *)calloc
        (queue_size, sizeof(IjkThreadPoolTask));

    /* Initialize mutex and conditional variable first */
    if((pthread_mutex_init(&(ctx->lock), NULL) != 0) ||
       (pthread_cond_init(&(ctx->notify), NULL) != 0) ||
       (ctx->threads == NULL) ||
       (ctx->queue == NULL)) {
        goto err;
    }

    /* Start worker threads */
    for(i = 0; i < thread_count; i++) {
        if(pthread_create(&(ctx->threads[i]), NULL,
                          ijk_threadpool_thread, (void*)ctx) != 0) {
            ijk_threadpool_destroy(ctx, 0);
            return NULL;
        }
        ctx->thread_count++;
        ctx->started_count++;
    }

    return ctx;

 err:
    if(ctx) {
        ijk_threadpool_free(ctx);
    }
    return NULL;
}

/**
 * 添加任务到线程池。
 * 若队列快满 (pending == queue_size-1)，自动扩容为 2倍 (上限 MAX_QUEUE)。
 * 添加成功后通过 cond_signal 唤醒一个等待中的工作线程。
 */
int ijk_threadpool_add(IjkThreadPoolContext *ctx, Runable function,
                   void *in_arg, void *out_arg, int flags)
{
    int err = 0;
    int next;

    if(ctx == NULL || function == NULL) {
        return IJK_THREADPOOL_INVALID;
    }

    if(pthread_mutex_lock(&(ctx->lock)) != 0) {
        return IJK_THREADPOOL_LOCK_FAILURE;
    }

    if (ctx->pending_count == MAX_QUEUE || ctx->pending_count == ctx->queue_size) {
        pthread_mutex_unlock(&ctx->lock);
        return IJK_THREADPOOL_QUEUE_FULL;
    }

    if (ctx->pending_count == ctx->queue_size - 1) {
        /* 队列快满，自动扩容为 2倍 (上限 MAX_QUEUE) */
        int new_pueue_size = (ctx->queue_size * 2) > MAX_QUEUE ? MAX_QUEUE : (ctx->queue_size * 2);
        IjkThreadPoolTask *new_queue = (IjkThreadPoolTask *)realloc(ctx->queue, sizeof(IjkThreadPoolTask) * new_pueue_size);
        if (new_queue) {
            ctx->queue = new_queue;
            ctx->queue_size = new_pueue_size;
        }
    }

    next = (ctx->queue_tail + 1) % ctx->queue_size;
    do {
        /* Are we shutting down ? */
        if(ctx->shutdown) {
            err = IJK_THREADPOOL_SHUTDOWN;
            break;
        }

        /* Add task to queue */
        ctx->queue[ctx->queue_tail].function = function;
        ctx->queue[ctx->queue_tail].in_arg   = in_arg;
        ctx->queue[ctx->queue_tail].out_arg  = out_arg;
        ctx->queue_tail = next;
        ctx->pending_count += 1;

        /* pthread_cond_broadcast */
        if(pthread_cond_signal(&(ctx->notify)) != 0) {
            err = IJK_THREADPOOL_LOCK_FAILURE;
            break;
        }
    } while(0);

    if(pthread_mutex_unlock(&ctx->lock) != 0) {
        err = IJK_THREADPOOL_LOCK_FAILURE;
    }

    return err;
}

static int ijk_threadpool_freep(IjkThreadPoolContext **ctx)
{
    int ret = 0;

    if (!ctx || !*ctx)
        return -1;

    ret = ijk_threadpool_free(*ctx);
    *ctx = NULL;
    return ret;
}

/**
 * 关闭并销毁线程池。
 * 流程: 设置 shutdown 标志 -> broadcast 唤醒所有线程 -> join 等待退出 -> 释放内存
 */
int ijk_threadpool_destroy(IjkThreadPoolContext *ctx, int flags)
{
    int i, err = 0;

    if(ctx == NULL) {
        return IJK_THREADPOOL_INVALID;
    }

    if(pthread_mutex_lock(&(ctx->lock)) != 0) {
        return IJK_THREADPOOL_LOCK_FAILURE;
    }

    do {
        /* Already shutting down */
        if(ctx->shutdown) {
            err = IJK_THREADPOOL_SHUTDOWN;
            break;
        }

        ctx->shutdown = flags;

        /* Wake up all worker threads */
        if((pthread_cond_broadcast(&(ctx->notify)) != 0) ||
           (pthread_mutex_unlock(&(ctx->lock)) != 0)) {
            err = IJK_THREADPOOL_LOCK_FAILURE;
            break;
        }

        /* Join all worker thread */
        for(i = 0; i < ctx->thread_count; i++) {
            if(pthread_join(ctx->threads[i], NULL) != 0) {
                err = IJK_THREADPOOL_THREAD_FAILURE;
            }
        }
    } while(0);

    /* Only if everything went well do we deallocate the pool */
    if(!err) {
        return ijk_threadpool_freep(&ctx);
    }
    return err;
}
