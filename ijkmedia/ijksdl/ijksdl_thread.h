/*****************************************************************************
 * ijksdl_thread.h
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
 * ijksdl_thread.h
 *
 * 线程管理的 pthread 封装。
 * 提供平台无关的线程创建、优先级设置和等待/分离操作。
 */

#ifndef IJKSDL__IJKSDL_THREAD_H
#define IJKSDL__IJKSDL_THREAD_H

#include <stdint.h>
#include <pthread.h>

typedef enum {
    SDL_THREAD_PRIORITY_LOW,
    SDL_THREAD_PRIORITY_NORMAL,
    SDL_THREAD_PRIORITY_HIGH
} SDL_ThreadPriority;

/**
 * @struct SDL_Thread
 * @brief  线程封装结构
 */
typedef struct SDL_Thread
{
    pthread_t id;          /**< POSIX 线程 ID */
    int (*func)(void *);   /**< 线程入口函数 */
    void *data;            /**< 传递给入口函数的参数 */
    char name[32];         /**< 线程名称 (用于调试和日志) */
    int retval;            /**< 线程返回值 */
} SDL_Thread;

SDL_Thread *SDL_CreateThreadEx(SDL_Thread *thread, int (*fn)(void *), void *data, const char *name);  /**< 创建线程 */
int         SDL_SetThreadPriority(SDL_ThreadPriority priority);  /**< 设置当前线程优先级 */
void        SDL_WaitThread(SDL_Thread *thread, int *status);     /**< 等待线程结束并获取返回值 */
void        SDL_DetachThread(SDL_Thread *thread);                /**< 分离线程 (线程结束后自动回收) */

#endif
