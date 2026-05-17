/*
 * ijkai_pipenode.h
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * This file is part of ijkPlayer AI Framework.
 *
 * AI Pipenode - 将AI推理封装为IJKFF_Pipenode接口,
 * 可与IJKPlayer的Pipeline系统无缝集成。
 */

#ifndef IJKAI_PIPENODE_H
#define IJKAI_PIPENODE_H

#include "ijkai.h"
#include "../ijkplayer/ff_ffpipenode.h"

/* FFPlayer前向声明(避免引入ff_ffplay_def.h的FFmpeg依赖) */
typedef struct FFPlayer FFPlayer;

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 创建AI Pipenode
 * @param ffp FFPlayer实例
 * @param type AI类型(LLM/CV/多模态)
 * @param model_path 模型路径
 * @param n_threads 线程数
 * @return Pipenode指针,失败返回NULL
 */
IJKFF_Pipenode *ijkai_pipenode_create(FFPlayer *ffp, ijkai_type type,
                                       const char *model_path, int n_threads);

/**
 * 获取AI Pipenode内部的AI上下文
 * @param node Pipenode
 * @return AI上下文,失败返回NULL
 */
ijkai_context *ijkai_pipenode_get_context(IJKFF_Pipenode *node);

#ifdef __cplusplus
}
#endif

#endif // IJKAI_PIPENODE_H
