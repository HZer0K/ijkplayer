/*****************************************************************************
 * ijksdl_mutex.h
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
 * ijksdl_mutex.h
 *
 * 互斥锁和条件变量的 pthread 封装。
 * 提供平台无关的同步原语，接口风格类似 SDL。
 */

#ifndef IJKSDL__IJKSDL_MUTEX_H
#define IJKSDL__IJKSDL_MUTEX_H

#include <stdint.h>
#include <pthread.h>

#define SDL_MUTEX_TIMEDOUT  1
#define SDL_MUTEX_MAXWAIT   (~(uint32_t)0)

/** @struct SDL_mutex
 *  @brief  pthread_mutex_t 的封装 */
typedef struct SDL_mutex {
    pthread_mutex_t id;
} SDL_mutex;

SDL_mutex  *SDL_CreateMutex(void);        /**< 创建互斥锁 */
void        SDL_DestroyMutex(SDL_mutex *mutex);  /**< 销毁互斥锁 */
void        SDL_DestroyMutexP(SDL_mutex **mutex);  /**< 销毁互斥锁并置空指针 */
int         SDL_LockMutex(SDL_mutex *mutex);      /**< 加锁 */
int         SDL_UnlockMutex(SDL_mutex *mutex);    /**< 解锁 */

/** @struct SDL_cond
 *  @brief  pthread_cond_t 的封装 */
typedef struct SDL_cond {
    pthread_cond_t id;
} SDL_cond;

SDL_cond   *SDL_CreateCond(void);         /**< 创建条件变量 */
void        SDL_DestroyCond(SDL_cond *cond);  /**< 销毁条件变量 */
void        SDL_DestroyCondP(SDL_cond **mutex);  /**< 销毁条件变量并置空指针 */
int         SDL_CondSignal(SDL_cond *cond);     /**< 唤醒一个等待线程 */
int         SDL_CondBroadcast(SDL_cond *cond);  /**< 唤醒所有等待线程 */
int         SDL_CondWaitTimeout(SDL_cond *cond, SDL_mutex *mutex, uint32_t ms);  /**< 带超时的条件等待 */
int         SDL_CondWait(SDL_cond *cond, SDL_mutex *mutex);  /**< 无限等待条件变量 */

#endif

