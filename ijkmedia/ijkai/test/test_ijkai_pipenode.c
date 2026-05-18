/*
 * test_ijkai_pipenode.c
 *
 * AI Pipenode 测试
 * 覆盖: create/destroy、run_sync、flush、context getter、NULL 安全
 *
 * 编译见 run_tests.sh
 */

#include "test_stubs.h"
#include "test_runner.h"
#include "../ijkai.h"
#include "../ijkai_pipenode.h"

/* ========== Create/Destroy 测试 ========== */

TEST(PipenodeCreate, llm) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_LLM, "/fake/model.gguf", 4);
    ASSERT_NOT_NULL(node);
    ASSERT_NOT_NULL(ijkai_pipenode_get_context(node));
    node->func_destroy(node);
    ffpipenode_free(node);
}

TEST(PipenodeCreate, cv_sr) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    ASSERT_NOT_NULL(node);
    ASSERT_NOT_NULL(ijkai_pipenode_get_context(node));
    node->func_destroy(node);
    ffpipenode_free(node);
}

TEST(PipenodeCreate, cv_detect) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_CV_DETECT, "/fake/yolo.mnn", 2);
    ASSERT_NOT_NULL(node);
    ASSERT_NOT_NULL(ijkai_pipenode_get_context(node));
    node->func_destroy(node);
    ffpipenode_free(node);
}

TEST(PipenodeCreate, multimodal) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_MULTIMODAL, "/fake/mmproj.gguf", 4);
    ASSERT_NOT_NULL(node);
    node->func_destroy(node);
    ffpipenode_free(node);
}

TEST(PipenodeCreate, null_model_path) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_LLM, NULL, 4);
    ASSERT_NULL(node);
}

/* ========== RunSync 测试 ========== */

TEST(PipenodeRunSync, llm) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    ASSERT_NOT_NULL(node);
    ASSERT_EQ(node->func_run_sync(node), 0);
    node->func_destroy(node);
    ffpipenode_free(node);
}

TEST(PipenodeRunSync, cv_sr) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    ASSERT_NOT_NULL(node);
    ASSERT_EQ(node->func_run_sync(node), 0);
    node->func_destroy(node);
    ffpipenode_free(node);
}

TEST(PipenodeRunSync, cv_detect) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_CV_DETECT, "/fake/yolo.mnn", 2);
    ASSERT_NOT_NULL(node);
    ASSERT_EQ(node->func_run_sync(node), 0);
    node->func_destroy(node);
    ffpipenode_free(node);
}

/* ========== Flush 测试 ========== */

TEST(PipenodeFlush, llm) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    ASSERT_NOT_NULL(node);
    ASSERT_EQ(node->func_flush(node), 0);
    // After flush, context should be recreated
    ASSERT_NOT_NULL(ijkai_pipenode_get_context(node));
    node->func_destroy(node);
    ffpipenode_free(node);
}

TEST(PipenodeFlush, cv_sr) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    ASSERT_NOT_NULL(node);
    ASSERT_EQ(node->func_flush(node), 0);
    ASSERT_NOT_NULL(ijkai_pipenode_get_context(node));
    node->func_destroy(node);
    ffpipenode_free(node);
}

/* ========== Context Getter 测试 ========== */

TEST(PipenodeGetContext, valid_node) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    ASSERT_NOT_NULL(node);
    ASSERT_NOT_NULL(ijkai_pipenode_get_context(node));
    node->func_destroy(node);
    ffpipenode_free(node);
}

TEST(PipenodeGetContext, null_node) {
    ASSERT_NULL(ijkai_pipenode_get_context(NULL));
}

/* ========== 完整生命周期 ========== */

TEST(PipenodeLifecycle, create_run_flush_destroy) {
    FFPlayer ffp;
    IJKFF_Pipenode *node = ijkai_pipenode_create(&ffp, IJKAI_TYPE_LLM, "/fake/model.gguf", 4);
    ASSERT_NOT_NULL(node);
    ASSERT_EQ(node->func_run_sync(node), 0);
    ASSERT_EQ(node->func_flush(node), 0);
    ASSERT_EQ(node->func_run_sync(node), 0);
    ASSERT_NOT_NULL(ijkai_pipenode_get_context(node));
    node->func_destroy(node);
    ffpipenode_free(node);
}

/* ========== 多节点并发 ========== */

TEST(PipenodeMulti, concurrent_nodes) {
    FFPlayer ffp;
    IJKFF_Pipenode *nodes[3];
    nodes[0] = ijkai_pipenode_create(&ffp, IJKAI_TYPE_LLM, "/fake/model.gguf", 2);
    nodes[1] = ijkai_pipenode_create(&ffp, IJKAI_TYPE_CV_SR, "/fake/sr.mnn", 2);
    nodes[2] = ijkai_pipenode_create(&ffp, IJKAI_TYPE_CV_DETECT, "/fake/yolo.mnn", 2);

    for (int i = 0; i < 3; i++) {
        ASSERT_NOT_NULL(nodes[i]);
        ASSERT_EQ(nodes[i]->func_run_sync(nodes[i]), 0);
        ASSERT_NOT_NULL(ijkai_pipenode_get_context(nodes[i]));
    }

    for (int i = 0; i < 3; i++) {
        nodes[i]->func_destroy(nodes[i]);
        ffpipenode_free(nodes[i]);
    }
}

int main(void) {
    return RUN_ALL_TESTS();
}
