/*
 * ijkai.h
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * This file is part of ijkPlayer AI Framework.
 *
 * ijkPlayer AI Framework is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * ijkPlayer AI Framework is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with ijkPlayer; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#ifndef IJKAI_H
#define IJKAI_H

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// ============ 类型定义 ============

/**
 * AI类型枚举
 */
typedef enum {
    IJKAI_TYPE_LLM,           /**< LLM推理 */
    IJKAI_TYPE_CV_SR,         /**< CV超分辨率 */
    IJKAI_TYPE_CV_DETECT,     /**< CV目标检测 */
    IJKAI_TYPE_MULTIMODAL     /**< 多模态 */
} ijkai_type;

/**
 * 任务类型枚举(内部使用)
 */
typedef enum {
    IJKAI_TASK_LLM,           /**< LLM任务 */
    IJKAI_TASK_CV,            /**< CV任务 */
    IJKAI_TASK_MULTIMODAL     /**< 多模态任务 */
} ijkai_task_type;

/**
 * LLM回调函数(异步)
 * @param text 生成的文本
 * @param is_complete 是否完成
 * @param user_data 用户数据
 */
typedef void (*ijkai_llm_callback)(
    const char *text,
    bool is_complete,
    void *user_data
);

/**
 * CV回调函数(异步)
 * @param output_data 输出数据
 * @param width 输出宽度
 * @param height 输出高度
 * @param success 是否成功
 * @param user_data 用户数据
 */
typedef void (*ijkai_cv_callback)(
    uint8_t *output_data,
    int width,
    int height,
    bool success,
    void *user_data
);

// ============ 核心接口 ============

/**
 * AI上下文(不透明指针,隐藏内部实现)
 */
typedef struct ijkai_context ijkai_context;

/**
 * 初始化AI上下文
 * @param type AI类型
 * @param model_path 模型文件路径
 * @param n_threads 线程数
 * @return AI上下文指针,失败返回NULL
 */
ijkai_context *ijkai_init(ijkai_type type, const char *model_path, int n_threads);

/**
 * 释放AI上下文
 * @param ctx AI上下文指针的指针
 */
void ijkai_release(ijkai_context **ctx);

/**
 * LLM推理(异步)
 * @param ctx AI上下文
 * @param prompt 提示词
 * @param callback 回调函数
 * @param user_data 用户数据
 * @param max_tokens 最大token数
 * @return 0成功, -1失败
 */
int ijkai_llm_prompt(
    ijkai_context *ctx,
    const char *prompt,
    ijkai_llm_callback callback,
    void *user_data,
    int max_tokens
);

/**
 * CV处理(异步)
 * @param ctx AI上下文
 * @param input_data 输入数据
 * @param in_width 输入宽度
 * @param in_height 输入高度
 * @param out_width 输出宽度
 * @param out_height 输出高度
 * @param callback 回调函数
 * @param user_data 用户数据
 * @return 0成功, -1失败
 */
int ijkai_cv_process(
    ijkai_context *ctx,
    uint8_t *input_data, int in_width, int in_height,
    int out_width, int out_height,
    ijkai_cv_callback callback,
    void *user_data
);

/**
 * 多模态推理(异步)
 * @param ctx AI上下文
 * @param image_data 图像数据
 * @param width 图像宽度
 * @param height 图像高度
 * @param question 问题
 * @param callback 回调函数
 * @param user_data 用户数据
 * @return 0成功, -1失败
 */
int ijkai_multimodal(
    ijkai_context *ctx,
    uint8_t *image_data, int width, int height,
    const char *question,
    ijkai_llm_callback callback,
    void *user_data
);

/**
 * 获取推理时间(毫秒)
 * @param ctx AI上下文
 * @return 推理时间(毫秒)
 */
int64_t ijkai_get_eval_time(ijkai_context *ctx);

/**
 * 获取token数量
 * @param ctx AI上下文
 * @return token数量
 */
int ijkai_get_token_count(ijkai_context *ctx);

/**
 * 获取已处理帧数
 * @param ctx AI上下文
 * @return 已处理帧数
 */
int ijkai_get_processed_frames(ijkai_context *ctx);

#ifdef __cplusplus
}
#endif

#endif // IJKAI_H
