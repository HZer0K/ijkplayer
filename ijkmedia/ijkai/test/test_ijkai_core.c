/*
 * test_ijkai_core.c
 *
 * AI 框架核心测试
 * 覆盖: 各类型 init/release、LLM prompt、CV process 路由、多模态、
 *       backend 设置、错误处理、统计查询
 *
 * 编译: gcc -std=c99 -lpthread -I.. \
 *           test_ijkai_core.c test_stubs.c \
 *           ../async/ijkai_queue.c \
 *           -o test_ijkai_core && ./test_ijkai_core
 *
 * NOTE: 该测试 #include 了 ijkai.c 源代码,
 *       并使用 test_stubs.h 来替代其内部 LLM/CV 依赖
 */

/* 先定义 include guard, 防止 ijkai.c 包含真正的内部头文件 */
#define IJKAI_LLM_IMPL_H
#define IJKAI_CV_H
#define FF_FFPIPENODE_H

#include "test_stubs.h"
#include "test_runner.h"
#include "../ijkai.h"

#include <time.h>

/* 便携 sleep (usleep 在 POSIX 2008 已废弃) */
static void msleep(unsigned int ms) {
    struct timespec ts;
    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (long)(ms % 1000) * 1000000L;
    nanosleep(&ts, NULL);
}

/* 现在包含 ijkai.c 源代码 (使用桩实现代替 LLM/CV) */
#include "../ijkai.c"

/* ========== 辅助函数 ========== */

/* 回调跟踪结构 */
typedef struct {
    int called;           /* 是否被调用 */
    char text[256];       /* 收到的文本 */
    int is_complete;      /* 是否完成 */
    uint8_t *output_data; /* CV 输出 */
    int out_w, out_h;     /* CV 输出尺寸 */
    bool success;         /* CV 是否成功 */
} callback_tracker;

static void llm_callback_tracker(const char *text, bool is_complete, void *user_data) {
    callback_tracker *ct = (callback_tracker*)user_data;
    ct->called++;
    if (text) {
        strncpy(ct->text, text, sizeof(ct->text) - 1);
    }
    ct->is_complete = is_complete;
}

static void cv_callback_tracker(uint8_t *output_data, int width, int height, bool success, void *user_data) {
    callback_tracker *ct = (callback_tracker*)user_data;
    ct->called++;
    ct->output_data = output_data;
    ct->out_w = width;
    ct->out_h = height;
    ct->success = success;
}

/* ========== Init/Release 测试 ========== */

TEST(CoreInit, llm_success) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_LLM, "/fake/model.gguf", 4);
    ASSERT_NOT_NULL(ctx);
    ASSERT_EQ(ctx->type, IJKAI_TYPE_LLM);
    ASSERT_NOT_NULL(ctx->queue);
    ASSERT_NOT_NULL(ctx->llm_ctx);
    ijkai_release(&ctx);
    ASSERT_NULL(ctx);
}

TEST(CoreInit, cv_sr_success) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    ASSERT_NOT_NULL(ctx);
    ASSERT_EQ(ctx->type, IJKAI_TYPE_CV_SR);
    ASSERT_NOT_NULL(ctx->cv_specific_ctx);
    ijkai_release(&ctx);
    ASSERT_NULL(ctx);
}

TEST(CoreInit, cv_detect_success) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_CV_DETECT, "/fake/yolo.mnn", 2);
    ASSERT_NOT_NULL(ctx);
    ASSERT_EQ(ctx->type, IJKAI_TYPE_CV_DETECT);
    ASSERT_NOT_NULL(ctx->cv_specific_ctx);
    ijkai_release(&ctx);
    ASSERT_NULL(ctx);
}

TEST(CoreInit, multimodal_success) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_MULTIMODAL, "/fake/mmproj.gguf", 4);
    ASSERT_NOT_NULL(ctx);
    ASSERT_EQ(ctx->type, IJKAI_TYPE_MULTIMODAL);
    ASSERT_NOT_NULL(ctx->llm_ctx);
    ijkai_release(&ctx);
    ASSERT_NULL(ctx);
}

TEST(CoreInit, null_model_path) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_LLM, NULL, 4);
    ASSERT_NULL(ctx);
}

TEST(CoreInit, release_null) {
    ijkai_release(NULL);
    ijkai_release(NULL); // double release should be safe
    ASSERT_TRUE(1);
}

TEST(CoreInit, release_double) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    ASSERT_NOT_NULL(ctx);
    ijkai_release(&ctx);
    ASSERT_NULL(ctx);
    // 释放后再次 release (ctx is NULL, should be safe)
    ijkai_release(&ctx);
    ASSERT_NULL(ctx);
}

/* ========== LLM Prompt 测试 ========== */

TEST(CoreLLM, prompt_async) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_LLM, "/fake/model.gguf", 4);
    ASSERT_NOT_NULL(ctx);

    callback_tracker ct;
    memset(&ct, 0, sizeof(ct));

    int ret = ijkai_llm_prompt(ctx, "Hello, AI!", llm_callback_tracker, &ct, 100);
    ASSERT_EQ(ret, 0);

    // 等待工作线程完成
    msleep(200);

    ASSERT_TRUE(ct.called >= 1);
    ijkai_release(&ctx);
}

TEST(CoreLLM, prompt_null_context) {
    int ret = ijkai_llm_prompt(NULL, "hi", NULL, NULL, 100);
    ASSERT_EQ(ret, -1);
}

TEST(CoreLLM, prompt_null_prompt) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    int ret = ijkai_llm_prompt(ctx, NULL, NULL, NULL, 100);
    ASSERT_EQ(ret, -1);
    ijkai_release(&ctx);
}

TEST(CoreLLM, prompt_wrong_type) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    int ret = ijkai_llm_prompt(ctx, "hi", NULL, NULL, 100);
    ASSERT_EQ(ret, -1); // wrong type
    ijkai_release(&ctx);
}

TEST(CoreLLM, prompt_max_tokens) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    callback_tracker ct;
    memset(&ct, 0, sizeof(ct));

    // 不同的 max_tokens 值
    ASSERT_EQ(ijkai_llm_prompt(ctx, "short", llm_callback_tracker, &ct, 10), 0);
    msleep(200);
    ASSERT_TRUE(ct.called >= 1);

    ijkai_release(&ctx);
}

/* ========== CV Process 测试 ========== */

TEST(CoreCV, sr_process_routing) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    ASSERT_NOT_NULL(ctx);

    uint8_t input[64] = {0}; // 4x4 RGBA
    callback_tracker ct;
    memset(&ct, 0, sizeof(ct));

    int ret = ijkai_cv_process(ctx, input, 4, 4, 8, 8, cv_callback_tracker, &ct);
    ASSERT_EQ(ret, 0);

    msleep(100);
    ASSERT_TRUE(ct.called >= 1);

    ijkai_release(&ctx);
}

TEST(CoreCV, detect_process_routing) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_CV_DETECT, "/fake/yolo.mnn", 2);
    ASSERT_NOT_NULL(ctx);

    uint8_t input[640 * 640 * 4] = {0}; // dummy frame
    callback_tracker ct;
    memset(&ct, 0, sizeof(ct));

    int ret = ijkai_cv_process(ctx, input, 640, 640, 0, 0, cv_callback_tracker, &ct);
    ASSERT_EQ(ret, 0);

    msleep(100);
    ASSERT_TRUE(ct.called >= 1);

    ijkai_release(&ctx);
}

TEST(CoreCV, process_null_context) {
    int ret = ijkai_cv_process(NULL, NULL, 0, 0, 0, 0, NULL, NULL);
    ASSERT_EQ(ret, -1);
}

TEST(CoreCV, process_null_input) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    int ret = ijkai_cv_process(ctx, NULL, 4, 4, 8, 8, NULL, NULL);
    ASSERT_EQ(ret, -1);
    ijkai_release(&ctx);
}

TEST(CoreCV, process_null_callback) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    uint8_t input[64] = {0};
    int ret = ijkai_cv_process(ctx, input, 4, 4, 8, 8, NULL, NULL);
    ASSERT_EQ(ret, -1);
    ijkai_release(&ctx);
}

TEST(CoreCV, process_wrong_type) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    uint8_t input[64] = {0};
    int ret = ijkai_cv_process(ctx, input, 4, 4, 8, 8, NULL, NULL);
    ASSERT_EQ(ret, -1); // LLM context can't do CV
    ijkai_release(&ctx);
}

/* ========== CV Backend 设置测试 ========== */

TEST(CoreCV, set_backend_valid) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    ASSERT_NOT_NULL(ctx);

    ASSERT_EQ(ijkai_cv_set_backend(ctx, IJKAI_CV_BACKEND_OPENCL), 0);
    ASSERT_EQ(ijkai_cv_set_backend(ctx, IJKAI_CV_BACKEND_VULKAN), 0);
    ASSERT_EQ(ijkai_cv_set_backend(ctx, IJKAI_CV_BACKEND_CPU), 0);
    ASSERT_EQ(ijkai_cv_set_backend(ctx, IJKAI_CV_BACKEND_AUTO), 0);

    ijkai_release(&ctx);
}

TEST(CoreCV, set_backend_null_context) {
    ASSERT_EQ(ijkai_cv_set_backend(NULL, IJKAI_CV_BACKEND_CPU), -1);
}

TEST(CoreCV, set_backend_wrong_type) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    ASSERT_EQ(ijkai_cv_set_backend(ctx, IJKAI_CV_BACKEND_CPU), -1);
    ijkai_release(&ctx);
}

/* ========== 多模态测试 ========== */

TEST(CoreMultimodal, process) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_MULTIMODAL, "/fake/mmproj.gguf", 4);
    ASSERT_NOT_NULL(ctx);

    uint8_t image[16] = {128, 64, 32, 255, 200, 100, 50, 255}; // 2x1 RGBA
    callback_tracker ct;
    memset(&ct, 0, sizeof(ct));

    int ret = ijkai_multimodal(ctx, image, 2, 1, "What is this?", llm_callback_tracker, &ct);
    ASSERT_EQ(ret, 0);

    msleep(200);
    ASSERT_TRUE(ct.called >= 1);

    ijkai_release(&ctx);
}

TEST(CoreMultimodal, null_image) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_MULTIMODAL, "/fake/mmproj.gguf", 2);
    int ret = ijkai_multimodal(ctx, NULL, 0, 0, "test", NULL, NULL);
    ASSERT_EQ(ret, -1);
    ijkai_release(&ctx);
}

TEST(CoreMultimodal, null_question) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_MULTIMODAL, "/fake/mmproj.gguf", 2);
    uint8_t image[16] = {0};
    int ret = ijkai_multimodal(ctx, image, 2, 2, NULL, NULL, NULL);
    ASSERT_EQ(ret, -1);
    ijkai_release(&ctx);
}

TEST(CoreMultimodal, wrong_type_rejected) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    uint8_t image[16] = {0};
    int ret = ijkai_multimodal(ctx, image, 2, 2, "test?", NULL, NULL);
    ASSERT_EQ(ret, -1); // CV_SR type can't do multimodal
    ijkai_release(&ctx);
}

/* ========== 统计测试 ========== */

TEST(CoreStats, eval_time) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    int64_t t = ijkai_get_eval_time(ctx);
    ASSERT_EQ(t, 42); // stub returns 42
    ijkai_release(&ctx);
}

TEST(CoreStats, token_count) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    int c = ijkai_get_token_count(ctx);
    ASSERT_EQ(c, 128); // stub returns 128
    ijkai_release(&ctx);
}

TEST(CoreStats, processed_frames) {
    ijkai_context *ctx = ijkai_init(IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    int f = ijkai_get_processed_frames(ctx);
    ASSERT_EQ(f, 0); // no frames processed yet
    ijkai_release(&ctx);
}

TEST(CoreStats, null_context) {
    ASSERT_EQ(ijkai_get_eval_time(NULL), 0);
    ASSERT_EQ(ijkai_get_token_count(NULL), 0);
    ASSERT_EQ(ijkai_get_processed_frames(NULL), 0);
}

/* ========== 多类型并发测试 ========== */

TEST(CoreMultiType, multiple_contexts) {
    ijkai_context *llm_ctx = ijkai_init(IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    ijkai_context *sr_ctx = ijkai_init(IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    ijkai_context *detect_ctx = ijkai_init(IJKAI_TYPE_CV_DETECT, "/fake/yolo.mnn", 2);

    ASSERT_NOT_NULL(llm_ctx);
    ASSERT_NOT_NULL(sr_ctx);
    ASSERT_NOT_NULL(detect_ctx);

    ijkai_release(&llm_ctx);
    ijkai_release(&sr_ctx);
    ijkai_release(&detect_ctx);

    ASSERT_NULL(llm_ctx);
    ASSERT_NULL(sr_ctx);
    ASSERT_NULL(detect_ctx);
}

int main(void) {
    return RUN_ALL_TESTS();
}
