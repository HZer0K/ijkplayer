/*
 * ff_ffpipeline_vdec.c
 *
 * Copyright (c) 2003 Bilibili
 * Copyright (c) 2003 Fabrice Bellard
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
 * ff_ffpipenode.c
 *
 * Pipenode 管线节点基类实现。
 * 提供节点分配/释放和统一接口分发，具体解码逻辑由子类实现。
 */

#include "ff_ffpipenode.h"
#include <stdlib.h>
#include <string.h>

/**
 * 分配节点实例。
 * @param opaque_size  子类私有数据大小 (calloc 分配)
 * @return 新分配的节点实例 (含 mutex)，失败返回 NULL
 */
IJKFF_Pipenode *ffpipenode_alloc(size_t opaque_size)
{
    IJKFF_Pipenode *node = (IJKFF_Pipenode*) calloc(1, sizeof(IJKFF_Pipenode));
    if (!node)
        return NULL;

    node->opaque = calloc(1, opaque_size);
    if (!node->opaque) {
        free(node);
        return NULL;
    }

    node->mutex = SDL_CreateMutex();
    if (node->mutex == NULL) {
        free(node->opaque);
        free(node);
        return NULL;
    }

    return node;
}

/**
 * 释放节点实例。
 * 流程: func_destroy(子类析构) -> SDL_DestroyMutex -> free(opaque) -> free(node)
 */
void ffpipenode_free(IJKFF_Pipenode *node)
{
    if (!node)
        return;

    if (node->func_destroy) {
        node->func_destroy(node);
    }

    SDL_DestroyMutexP(&node->mutex);

    free(node->opaque);
    memset(node, 0, sizeof(IJKFF_Pipenode));
    free(node);
}

void ffpipenode_free_p(IJKFF_Pipenode **node)
{
    if (!node)
        return;

    ffpipenode_free(*node);
    *node = NULL;
}

/* 同步执行节点任务 (通常是解码循环)，通过函数指针分发 */
int ffpipenode_run_sync(IJKFF_Pipenode *node)
{
    return node->func_run_sync(node);
}

/* 清空节点内部缓冲，用于 seek 时丢弃旧数据。func_flush 为 NULL 时安全返回 0 */
int ffpipenode_flush(IJKFF_Pipenode *node)
{
    if (!node || !node->func_flush)
        return 0;

    return node->func_flush(node);
}
