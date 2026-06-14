/*
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
 * You should have ijkPlayer a copy of the GNU Lesser General Public
 * License along with FFmpeg; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

/*
 * ijkthreadpool.h
 *
 * 通用线程池接口。
 * 提供工作线程池的创建、任务添加和销毁功能。
 * 任务队列采用环形缓冲区实现，支持动态扩容 (2倍增长，上限 MAX_QUEUE)。
 * 线程安全: 内部通过 pthread_mutex + pthread_cond 保护。
 */

#ifndef _IJK_THREADPOOL_H_
#define _IJK_THREADPOOL_H_

#include <pthread.h>

#define MAX_THREADS 100    /**< 线程池最大线程数 */
#define MAX_QUEUE 1024     /**< 任务队列最大容量 */

typedef enum {
    IJK_THREADPOOL_INVALID        = -1,  /**< 无效参数 (ctx 或 function 为 NULL) */
    IJK_THREADPOOL_LOCK_FAILURE   = -2,  /**< 互斥锁操作失败 */
    IJK_THREADPOOL_QUEUE_FULL     = -3,  /**< 任务队列已满 */
    IJK_THREADPOOL_SHUTDOWN       = -4,  /**< 线程池正在关闭 */
    IJK_THREADPOOL_THREAD_FAILURE = -5   /**< 线程 join 失败 */
} IjkThreadPoolErrorType;

typedef enum {
    IJK_IMMEDIATE_SHUTDOWN = 1,  /**< 立即关闭：丢弃队列中未处理的任务 */
    IJK_LEISURELY_SHUTDOWN = 2   /**< 优雅关闭：等待队列中所有任务处理完毕 */
} IjkThreadPoolShutdownType;

typedef void (*Runable)(void *, void *);  /**< 任务函数指针: function(in_arg, out_arg) */
/**
 *  @struct ThreadPoolTask
 *  @brief the work struct
 *
 *  @var function Pointer to the function that will perform the task.
 *  @var in_arg Argument to be passed to the function.
 *  @var out_arg Argument to be passed to the call function.
 */

typedef struct IjkThreadPoolTask {
    Runable function;  /**< 任务执行函数 */
    void *in_arg;      /**< 传入参数 (通常由调用者设置) */
    void *out_arg;     /**< 传出参数 (通常由任务填充) */
} IjkThreadPoolTask;

/**
 *  @struct ThreadPoolContext
 *  @brief The threadpool context struct
 *
 *  @var notify        Condition variable to notify worker threads.
 *  @var threads       Array containing worker threads ID.
 *  @var thread_count  Number of threads
 *  @var queue         Array containing the task queue.
 *  @var queue_size    Size of the task queue.
 *  @var queue_head    Index of the first element.
 *  @var queue_tail    Index of the next element.
 *  @var pending_count Number of pending tasks
 *  @var shutdown      Flag indicating if the pool is shutting down
 *  @var started       Number of started threads
 */
typedef struct IjkThreadPoolContext {
    pthread_mutex_t lock;       /**< 保护所有共享数据的互斥锁 */
    pthread_cond_t notify;      /**< 通知工作线程有新任务或关闭信号 */
    pthread_t *threads;         /**< 工作线程 ID 数组 */
    IjkThreadPoolTask *queue;   /**< 任务队列 (环形缓冲区) */
    int thread_count;           /**< 当前线程数 */
    int queue_size;             /**< 队列当前容量 (可动态扩容) */
    int queue_head;             /**< 队列头索引 (消费者取任务位置) */
    int queue_tail;             /**< 队列尾索引 (生产者添加任务位置) */
    int pending_count;          /**< 队列中待处理任务数 */
    int shutdown;               /**< 关闭标志 (0=运行中, 1=立即关闭, 2=优雅关闭) */
    int started_count;          /**< 已启动且未退出的线程数 */
} IjkThreadPoolContext;

IjkThreadPoolContext *ijk_threadpool_create(int thread_count, int queue_size, int flags);  /**< 创建线程池 */

int ijk_threadpool_add(IjkThreadPoolContext *ctx, Runable function,
                   void *in_arg, void *out_arg, int flags);  /**< 添加任务到队列 */

int ijk_threadpool_destroy(IjkThreadPoolContext *ctx, int flags);  /**< 关闭并销毁线程池 */

#endif /* _IJK_THREADPOOL_H_ */
