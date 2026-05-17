/*
 * ijkai_queue.h
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * This file is part of ijkPlayer AI Framework.
 */

#ifndef IJKAI_QUEUE_H
#define IJKAI_QUEUE_H

#include "../ijkai.h"
#include <pthread.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 任务优先级
 */
typedef enum {
    IJKAI_PRIORITY_LOW    = 0,  /**< 低优先级(如后台批处理) */
    IJKAI_PRIORITY_NORMAL = 1,  /**< 普通优先级(默认) */
    IJKAI_PRIORITY_HIGH   = 2,  /**< 高优先级(如实时字幕) */
    IJKAI_PRIORITY_CRITICAL = 3 /**< 关键优先级(立即处理) */
} ijkai_priority;

/**
 * 任务数据结构
 */
typedef struct {
    ijkai_task_type type;     /**< 任务类型 */
    void *task_data;          /**< 任务数据 */
    int64_t timestamp;        /**< 时间戳(用于丢弃过期帧) */
    ijkai_priority priority;  /**< 任务优先级 */
} ijkai_task;

/**
 * 任务队列(不透明指针)
 */
typedef struct ijkai_task_queue ijkai_task_queue;

/**
 * 创建任务队列
 * @param max_size 队列最大大小
 * @return 队列指针,失败返回NULL
 */
ijkai_task_queue *ijkai_queue_create(int max_size);

/**
 * 释放任务队列
 * @param queue 队列指针
 */
void ijkai_queue_release(ijkai_task_queue *queue);

/**
 * 推入任务(非阻塞,队列满时丢弃最旧任务)
 * @param queue 队列
 * @param task 任务
 * @return 0成功, -1失败
 */
int ijkai_queue_push(ijkai_task_queue *queue, ijkai_task *task);

/**
 * 弹出任务(阻塞)
 * @param queue 队列
 * @param task 任务输出
 * @param timeout_ms 超时时间(毫秒)
 * @return 0成功, -1超时
 */
int ijkai_queue_pop(ijkai_task_queue *queue, ijkai_task *task, int timeout_ms);

/**
 * 获取队列大小
 * @param queue 队列
 * @return 队列中任务数量
 */
int ijkai_queue_size(ijkai_task_queue *queue);

/**
 * 检查队列是否已满
 * @param queue 队列
 * @return true已满, false未满
 */
bool ijkai_queue_is_full(ijkai_task_queue *queue);

/**
 * 关闭队列(唤醒所有等待线程,后续pop立即返回-1)
 * @param queue 队列
 */
void ijkai_queue_shutdown(ijkai_task_queue *queue);

#ifdef __cplusplus
}
#endif

#endif // IJKAI_QUEUE_H
