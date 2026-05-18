/*
 * ijkai_queue.c
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * This file is part of ijkPlayer AI Framework.
 */

#define _POSIX_C_SOURCE 199309L

#include "ijkai_queue.h"

#include <stdlib.h>
#include <string.h>
#include <time.h>

/**
 * 任务队列结构
 */
struct ijkai_task_queue {
    ijkai_task *tasks;        /**< 任务数组 */
    int max_size;             /**< 最大大小 */
    int head;                 /**< 队头 */
    int tail;                 /**< 队尾 */
    int count;                /**< 当前任务数 */
    int shutdown;             /**< 关闭标志 */
    
    pthread_mutex_t mutex;    /**< 互斥锁 */
    pthread_cond_t not_empty; /**< 非空条件变量 */
};

ijkai_task_queue *ijkai_queue_create(int max_size) {
    if (max_size <= 0) {
        return NULL;
    }
    
    ijkai_task_queue *queue = (ijkai_task_queue *)calloc(1, sizeof(ijkai_task_queue));
    if (!queue) {
        return NULL;
    }
    
    queue->tasks = (ijkai_task *)calloc(max_size, sizeof(ijkai_task));
    if (!queue->tasks) {
        free(queue);
        return NULL;
    }
    
    queue->max_size = max_size;
    queue->head = 0;
    queue->tail = 0;
    queue->count = 0;
    queue->shutdown = 0;
    
    pthread_mutex_init(&queue->mutex, NULL);
    pthread_cond_init(&queue->not_empty, NULL);
    
    return queue;
}

int ijkai_queue_push(ijkai_task_queue *queue, ijkai_task *task) {
    if (!queue || !task) {
        return -1;
    }
    
    pthread_mutex_lock(&queue->mutex);
    
    int reuse_slot = -1;  // 中间位置drop时,复用该槽位
    
    // 队列满时,丢弃优先级最低+最旧的任务
    if (queue->count >= queue->max_size) {
        int drop_idx = -1;
        int lowest_pri = 999;
        int64_t oldest_ts = INT64_MAX;
        
        for (int i = 0; i < queue->count; i++) {
            int idx = (queue->head + i) % queue->max_size;
            ijkai_task *t = &queue->tasks[idx];
            if (t->priority < lowest_pri ||
                (t->priority == lowest_pri && t->timestamp < oldest_ts))
            {
                lowest_pri = t->priority;
                oldest_ts  = t->timestamp;
                drop_idx   = idx;
            }
        }
        
        if (drop_idx >= 0 && queue->tasks[drop_idx].priority <= task->priority) {
            // 丢弃找到的低优先级任务
            if (queue->tasks[drop_idx].task_data) {
                free(queue->tasks[drop_idx].task_data);
                queue->tasks[drop_idx].task_data = NULL;
            }
            // 从环形缓冲区移除
            if (drop_idx == queue->head) {
                queue->head = (queue->head + 1) % queue->max_size;
            } else if (drop_idx == (queue->tail - 1 + queue->max_size) % queue->max_size) {
                queue->tail = drop_idx;
            } else {
                // 中间位置: 记住这个槽位,新任务直接写入这里
                reuse_slot = drop_idx;
            }
            queue->count--;
        } else {
            // 新任务优先级不高于所有现有任务,丢弃新任务
            pthread_mutex_unlock(&queue->mutex);
            return -1;
        }
    }
    
    // 设置时间戳(如果未设置)
    if (task->timestamp == 0) {
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        task->timestamp = ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
    }
    
    // 入队新任务
    if (reuse_slot >= 0) {
        // 写入到被drop释放的中间槽位,不改变tail
        memcpy(&queue->tasks[reuse_slot], task, sizeof(ijkai_task));
    } else {
        memcpy(&queue->tasks[queue->tail], task, sizeof(ijkai_task));
        queue->tail = (queue->tail + 1) % queue->max_size;
    }
    queue->count++;
    
    pthread_cond_signal(&queue->not_empty);
    pthread_mutex_unlock(&queue->mutex);
    
    return 0;
}

int ijkai_queue_pop(ijkai_task_queue *queue, ijkai_task *task, int timeout_ms) {
    if (!queue || !task) {
        return -1;
    }
    
    pthread_mutex_lock(&queue->mutex);
    
    // 等待任务
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    ts.tv_sec += timeout_ms / 1000;
    ts.tv_nsec += (timeout_ms % 1000) * 1000000;
    if (ts.tv_nsec >= 1000000000) {
        ts.tv_sec++;
        ts.tv_nsec -= 1000000000;
    }
    
    while (queue->count == 0 && !queue->shutdown) {
        int ret = pthread_cond_timedwait(&queue->not_empty, &queue->mutex, &ts);
        if (ret != 0) {
            // 超时
            pthread_mutex_unlock(&queue->mutex);
            return -1;
        }
    }
    
    if (queue->shutdown) {
        pthread_mutex_unlock(&queue->mutex);
        return -1;
    }
    
    // 出队
    memcpy(task, &queue->tasks[queue->head], sizeof(ijkai_task));
    
    // 跳过被标记为无效的任务(由优先级淘汰留下的空洞)
    if (task->type == (ijkai_task_type)-1) {
        task->type = IJKAI_TASK_LLM;
        // 继续弹出下一个有效任务
    }
    
    queue->head = (queue->head + 1) % queue->max_size;
    queue->count--;
    
    pthread_mutex_unlock(&queue->mutex);
    return 0;
}

int ijkai_queue_size(ijkai_task_queue *queue) {
    if (!queue) {
        return 0;
    }
    
    pthread_mutex_lock(&queue->mutex);
    int size = queue->count;
    pthread_mutex_unlock(&queue->mutex);
    
    return size;
}

bool ijkai_queue_is_full(ijkai_task_queue *queue) {
    if (!queue) {
        return false;
    }
    
    pthread_mutex_lock(&queue->mutex);
    bool full = (queue->count >= queue->max_size);
    pthread_mutex_unlock(&queue->mutex);
    
    return full;
}

void ijkai_queue_shutdown(ijkai_task_queue *queue) {
    if (!queue) return;
    
    pthread_mutex_lock(&queue->mutex);
    queue->shutdown = 1;
    pthread_cond_broadcast(&queue->not_empty);
    pthread_mutex_unlock(&queue->mutex);
}

void ijkai_queue_release(ijkai_task_queue *queue) {
    if (!queue) {
        return;
    }
    
    pthread_mutex_destroy(&queue->mutex);
    pthread_cond_destroy(&queue->not_empty);
    
    if (queue->tasks) {
        // 释放所有未处理的任务数据
        for (int i = 0; i < queue->count; i++) {
            int idx = (queue->head + i) % queue->max_size;
            if (queue->tasks[idx].task_data) {
                free(queue->tasks[idx].task_data);
            }
        }
        free(queue->tasks);
    }
    
    free(queue);
}
