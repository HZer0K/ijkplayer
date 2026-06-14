/*
 * ff_ffpipenode.h
 *
 * Copyright (c) 2014 Bilibili
 * Copyright (c) 2014 Zhang Rui <bbcallen@gmail.com>
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
 * ff_ffpipenode.h
 *
 * Pipenode 管线节点架构头文件。
 *
 * Pipenode 是 Pipeline 中的处理节点，主要用于视频解码。
 * 通过函数指针实现多态:
 *   - func_run_sync: 同步执行节点任务 (如解码循环)
 *   - func_flush:    清空节点内部缓冲 (可选)
 *   - func_destroy:  节点析构回调
 *
 * 典型用法:
 *   由 Pipeline 的 func_open_video_decoder / func_init_video_decoder 创建，
 *   FFPlayer 调用 ffpipenode_run_sync() 启动解码循环。
 */

#ifndef FFPLAY__FF_FFPIPENODE_H
#define FFPLAY__FF_FFPIPENODE_H

#include "ijksdl/ijksdl_mutex.h"

typedef struct IJKFF_Pipenode_Opaque IJKFF_Pipenode_Opaque;
typedef struct IJKFF_Pipenode IJKFF_Pipenode;

/**
 * @struct IJKFF_Pipenode
 * @brief  管线处理节点抽象结构
 */
struct IJKFF_Pipenode {
    SDL_mutex *mutex;           /**< 节点互斥锁，保护并发访问 */
    void *opaque;               /**< 节点私有数据 (由子类分配) */

    void (*func_destroy) (IJKFF_Pipenode *node);   /**< 子类析构回调 */
    int  (*func_run_sync)(IJKFF_Pipenode *node);   /**< 同步执行节点任务 (解码循环，阻塞直到完成) */
    int  (*func_flush)   (IJKFF_Pipenode *node);   /**< 清空内部缓冲 (可选，用于 seek 时丢弃旧数据) */
};

IJKFF_Pipenode *ffpipenode_alloc(size_t opaque_size);   /**< 分配节点并初始化 mutex */
void ffpipenode_free(IJKFF_Pipenode *node);              /**< 释放节点 (func_destroy -> mutex -> opaque) */
void ffpipenode_free_p(IJKFF_Pipenode **node);

int  ffpipenode_run_sync(IJKFF_Pipenode *node);          /**< 启动同步执行 (通常在线程中调用) */
int  ffpipenode_flush(IJKFF_Pipenode *node);             /**< 清空缓冲，func_flush 为 NULL 时返回 0 */

#endif
