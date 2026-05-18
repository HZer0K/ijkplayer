/*
 * test_ijkai_algo.c
 *
 * 算法级单元测试 - 无外部依赖
 * 覆盖: IoU计算, NMS后处理, RGBA↔NCHW转换, 检测结果序列化
 *
 * 编译: gcc -std=c99 -lm test_ijkai_algo.c -o test_ijkai_algo && ./test_ijkai_algo
 */

#define _POSIX_C_SOURCE 199309L

#include "test_runner.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>

/* ========== 被测试函数 (从ijkai_cv_detect.c复制) ========== */

static float iou(float x1, float y1, float w1, float h1,
                  float x2, float y2, float w2, float h2) {
    float x_left   = (x1 > x2) ? x1 : x2;
    float y_top    = (y1 > y2) ? y1 : y2;
    float x_right  = ((x1 + w1) < (x2 + w2)) ? (x1 + w1) : (x2 + w2);
    float y_bottom = ((y1 + h1) < (y2 + h2)) ? (y1 + h1) : (y2 + h2);

    if (x_left >= x_right || y_top >= y_bottom) {
        return 0.0f;
    }

    float intersect_area = (x_right - x_left) * (y_bottom - y_top);
    float union_area = w1 * h1 + w2 * h2 - intersect_area;
    return (union_area > 0.0f) ? (intersect_area / union_area) : 0.0f;
}

typedef struct {
    float x, y, w, h;
    float confidence;
    int   class_id;
} detection_result;

#define NMS_THRESHOLD 0.45f
#define CONFIDENCE_THRESHOLD 0.25f
#define MAX_DETECTIONS 100

static int nms(detection_result *dets, int count,
               detection_result *out, int max_out) {
    for (int i = 0; i < count - 1; i++) {
        for (int j = i + 1; j < count; j++) {
            if (dets[j].confidence > dets[i].confidence) {
                detection_result tmp = dets[i];
                dets[i] = dets[j];
                dets[j] = tmp;
            }
        }
    }

    int out_count = 0;
    int *suppressed = (int *)calloc((size_t)count, sizeof(int));
    if (!suppressed) return 0;

    for (int i = 0; i < count && out_count < max_out; i++) {
        if (suppressed[i]) continue;
        out[out_count++] = dets[i];
        for (int j = i + 1; j < count; j++) {
            if (suppressed[j]) continue;
            if (dets[j].class_id != dets[i].class_id) continue;
            float iou_val = iou(
                dets[i].x, dets[i].y, dets[i].w, dets[i].h,
                dets[j].x, dets[j].y, dets[j].w, dets[j].h);
            if (iou_val > NMS_THRESHOLD) {
                suppressed[j] = 1;
            }
        }
    }
    free(suppressed);
    return out_count;
}

/* rgba_to_nchw_float from ijkai_cv_detect.c */
static void rgba_to_nchw_float(const uint8_t *rgba, int w, int h,
                                float *output) {
    int size = w * h;
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int src_idx = (y * w + x) * 4;
            int dst_base = y * w + x;
            output[0 * size + dst_base] = rgba[src_idx + 0] / 255.0f;
            output[1 * size + dst_base] = rgba[src_idx + 1] / 255.0f;
            output[2 * size + dst_base] = rgba[src_idx + 2] / 255.0f;
        }
    }
}

/* nchw_float_to_rgba from ijkai_cv_sr.c */
static void nchw_float_to_rgba(const float *input, int w, int h,
                                uint8_t *rgba) {
    int size = w * h;
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int dst_idx = (y * w + x) * 4;
            int src_base = y * w + x;
            float r = input[0 * size + src_base];
            float g = input[1 * size + src_base];
            float b = input[2 * size + src_base];
            rgba[dst_idx + 0] = (uint8_t)(r * 255.0f + 0.5f);
            rgba[dst_idx + 1] = (uint8_t)(g * 255.0f + 0.5f);
            rgba[dst_idx + 2] = (uint8_t)(b * 255.0f + 0.5f);
            rgba[dst_idx + 3] = 255;
        }
    }
}

/* ========== IoU 测试 ========== */

TEST(IoU, identical_boxes) {
    float val = iou(10, 10, 20, 20, 10, 10, 20, 20);
    ASSERT_NEAR(val, 1.0f, 0.001f);
}

TEST(IoU, no_overlap) {
    float val = iou(0, 0, 10, 10, 100, 100, 10, 10);
    ASSERT_NEAR(val, 0.0f, 0.001f);
}

TEST(IoU, half_overlap) {
    float val = iou(0, 0, 100, 100, 50, 0, 100, 100);
    // overlap: x=[50,100], y=[0,100] => 50*100=5000
    // union: 10000+10000-5000=15000
    // IoU: 5000/15000 = 0.333...
    ASSERT_NEAR(val, 5000.0f/15000.0f, 0.001f);
}

TEST(IoU, touching_boxes) {
    float val = iou(0, 0, 10, 10, 10, 0, 10, 10);
    ASSERT_NEAR(val, 0.0f, 0.001f);
}

TEST(IoU, one_inside_other) {
    float val = iou(0, 0, 100, 100, 25, 25, 50, 50);
    ASSERT_NEAR(val, 0.25f, 0.001f); // 2500/10000
}

TEST(IoU, zero_area_box) {
    float val = iou(0, 0, 0, 0, 10, 10, 10, 10);
    ASSERT_NEAR(val, 0.0f, 0.001f);
}

TEST(IoU, negative_coordinates) {
    float val = iou(-10, -10, 20, 20, -5, -5, 10, 10);
    ASSERT_TRUE(val > 0.0f);
}

TEST(IoU, floating_point_precision) {
    float val = iou(0.1f, 0.2f, 0.5f, 0.3f, 0.4f, 0.1f, 0.5f, 0.5f);
    ASSERT_TRUE(val >= 0.0f && val <= 1.0f);
}

/* ========== NMS 测试 ========== */

TEST(NMS, no_overlapping_detections) {
    detection_result dets[3] = {
        {10, 10, 20, 20, 0.9f, 0},
        {100, 100, 30, 30, 0.8f, 0},
        {200, 200, 10, 10, 0.7f, 1}
    };
    detection_result out[3];
    int count = nms(dets, 3, out, 3);
    ASSERT_EQ(count, 3);
}

TEST(NMS, redundant_detections_same_class) {
    detection_result dets[3] = {
        {10, 10, 100, 100, 0.9f, 1},
        {15, 15, 100, 100, 0.8f, 1},  // same class, high IoU with #0
        {200, 200, 20, 20, 0.7f, 1}
    };
    detection_result out[3];
    int count = nms(dets, 3, out, 3);
    ASSERT_EQ(count, 2); // #0 and #2, #1 suppressed
}

TEST(NMS, different_classes_not_suppressed) {
    detection_result dets[2] = {
        {10, 10, 100, 100, 0.9f, 0},
        {15, 15, 100, 100, 0.8f, 1}  // different class, NOT suppressed
    };
    detection_result out[2];
    int count = nms(dets, 2, out, 2);
    ASSERT_EQ(count, 2);
}

TEST(NMS, confidence_ordering) {
    detection_result dets[3] = {
        {10, 10, 100, 100, 0.5f, 0},
        {15, 15, 100, 100, 0.9f, 0},  // higher conf but should be suppressed
        {200, 200, 20, 20, 0.3f, 0}
    };
    detection_result out[3];
    int count = nms(dets, 3, out, 3);
    ASSERT_EQ(count, 2);
    ASSERT_EQ(out[0].class_id, 0);
    ASSERT_NEAR(out[0].confidence, 0.9f, 0.001f); // highest conf first
}

TEST(NMS, empty_input) {
    detection_result out[10];
    int count = nms(NULL, 0, out, 10);
    ASSERT_EQ(count, 0);
}

TEST(NMS, single_detection) {
    detection_result dets[1] = {{50, 50, 100, 100, 0.95f, 0}};
    detection_result out[1];
    int count = nms(dets, 1, out, 1);
    ASSERT_EQ(count, 1);
    ASSERT_NEAR(out[0].x, 50.0f, 0.001f);
    ASSERT_NEAR(out[0].confidence, 0.95f, 0.001f);
}

TEST(NMS, max_output_limit) {
    detection_result dets[5] = {
        {10, 10, 20, 20, 0.9f, 0},
        {50, 50, 20, 20, 0.8f, 0},
        {90, 90, 20, 20, 0.7f, 0},
        {130, 130, 20, 20, 0.6f, 0},
        {170, 170, 20, 20, 0.5f, 0}
    };
    detection_result out[2];
    int count = nms(dets, 5, out, 2);
    ASSERT_EQ(count, 2);
}

TEST(NMS, all_suppressed_by_top_one) {
    detection_result dets[3] = {
        {50, 50, 100, 100, 0.99f, 0},
        {55, 55, 90, 90, 0.5f, 0},   // high IoU with #0
        {60, 60, 80, 80, 0.3f, 0}    // high IoU with #0
    };
    detection_result out[3];
    int count = nms(dets, 3, out, 3);
    ASSERT_EQ(count, 1);
    ASSERT_NEAR(out[0].confidence, 0.99f, 0.001f);
}

/* ========== RGBA↔NCHW 转换测试 ========== */

TEST(RGBA_NCHW, identity_checkerboard) {
    // 2x2 RGBA checkerboard pattern
    uint8_t rgba[16] = {
        255, 0,   0,   255,
        0,   255, 0,   255,
        0,   0,   255, 255,
        255, 255, 255, 255
    };
    float nchw[12]; // 3 ch * 4 pixels
    memset(nchw, 0, sizeof(nchw));

    rgba_to_nchw_float(rgba, 2, 2, nchw);

    // R channel: pixel(0,0)=1.0, pixel(1,0)=0.0, pixel(0,1)=0.0, pixel(1,1)=1.0
    ASSERT_NEAR(nchw[0], 1.0f, 0.001f);
    ASSERT_NEAR(nchw[1], 0.0f, 0.001f);
    ASSERT_NEAR(nchw[2], 0.0f, 0.001f);
    ASSERT_NEAR(nchw[3], 1.0f, 0.001f);

    // G channel: pixel(0,0)=0.0, pixel(1,0)=1.0, pixel(0,1)=0.0, pixel(1,1)=1.0
    ASSERT_NEAR(nchw[4], 0.0f, 0.001f);
    ASSERT_NEAR(nchw[5], 1.0f, 0.001f);
    ASSERT_NEAR(nchw[6], 0.0f, 0.001f);
    ASSERT_NEAR(nchw[7], 1.0f, 0.001f);

    // B channel: pixel(0,0)=0.0, pixel(1,0)=0.0, pixel(0,1)=1.0, pixel(1,1)=1.0
    ASSERT_NEAR(nchw[8], 0.0f, 0.001f);
    ASSERT_NEAR(nchw[9], 0.0f, 0.001f);
    ASSERT_NEAR(nchw[10], 1.0f, 0.001f);
    ASSERT_NEAR(nchw[11], 1.0f, 0.001f);
}

TEST(RGBA_NCHW, roundtrip) {
    uint8_t rgba_in[16] = {
        128, 64,  32,  255,
        200, 100, 50,  255,
        10,  20,  30,  255,
        240, 250, 10,  255
    };
    float nchw[12];
    uint8_t rgba_out[16];
    memset(nchw, 0, sizeof(nchw));
    memset(rgba_out, 0, sizeof(rgba_out));

    rgba_to_nchw_float(rgba_in, 2, 2, nchw);
    nchw_float_to_rgba(nchw, 2, 2, rgba_out);

    // Alpha should be preserved at 255
    for (int i = 0; i < 4; i++) {
        ASSERT_EQ(rgba_out[i * 4 + 3], 255);
    }

    // RGB channels should roundtrip (tolerate ±1 due to quantization)
    for (int i = 0; i < 12; i++) {
        int px = i / 3;
        int ch = i % 3;
        ASSERT_NEAR((int)rgba_out[px * 4 + ch], (int)rgba_in[px * 4 + ch], 1);
    }
}

TEST(RGBA_NCHW, solid_color) {
    uint8_t rgba[1600]; // 20x20
    for (int i = 0; i < 400; i++) {
        rgba[i * 4 + 0] = 128;
        rgba[i * 4 + 1] = 64;
        rgba[i * 4 + 2] = 32;
        rgba[i * 4 + 3] = 255;
    }
    float nchw[1200];
    rgba_to_nchw_float(rgba, 20, 20, nchw);

    for (int i = 0; i < 400; i++) {
        ASSERT_NEAR(nchw[0 * 400 + i], 128.0f / 255.0f, 0.001f);
        ASSERT_NEAR(nchw[1 * 400 + i], 64.0f / 255.0f, 0.001f);
        ASSERT_NEAR(nchw[2 * 400 + i], 32.0f / 255.0f, 0.001f);
    }
}

TEST(RGBA_NCHW, zero_input) {
    uint8_t rgba[8] = {0, 0, 0, 255, 0, 0, 0, 255};
    float nchw[6];
    rgba_to_nchw_float(rgba, 2, 1, nchw);
    ASSERT_NEAR(nchw[0], 0.0f, 0.001f);
    ASSERT_NEAR(nchw[1], 0.0f, 0.001f);
    ASSERT_NEAR(nchw[2], 0.0f, 0.001f);
    ASSERT_NEAR(nchw[3], 0.0f, 0.001f);
    ASSERT_NEAR(nchw[4], 0.0f, 0.001f);
    ASSERT_NEAR(nchw[5], 0.0f, 0.001f);
}

/* ========== 检测结果序列化测试 ========== */

TEST(Serialize, detection_output_format) {
    // 模拟检测内部处理后的序列化格式
    detection_result dets[2] = {
        {0.1f, 0.2f, 0.5f, 0.4f, 0.95f, 0},
        {0.3f, 0.1f, 0.2f, 0.3f, 0.85f, 1}
    };

    // 模拟 serialization (copy from final loop in ijkai_cv_detect_process_internal)
    uint8_t buf[48];
    memset(buf, 0, sizeof(buf));
    uint8_t *ptr = buf;
    for (int i = 0; i < 2; i++) {
        memcpy(ptr, &dets[i], sizeof(float) * 5);
        ptr += 20;
        memcpy(ptr, &dets[i].class_id, sizeof(int));
        ptr += 4;
    }

    // 验证: 每个检测 = 5 floats(20B) + 1 int(4B) = 24B
    float x, y, conf;
    int cls;

    memcpy(&x, buf, 4);
    ASSERT_NEAR(x, 0.1f, 0.001f);
    memcpy(&y, buf + 4, 4);
    ASSERT_NEAR(y, 0.2f, 0.001f);
    memcpy(&conf, buf + 16, 4);
    ASSERT_NEAR(conf, 0.95f, 0.001f);
    memcpy(&cls, buf + 20, 4);
    ASSERT_EQ(cls, 0);

    memcpy(&x, buf + 24, 4);
    ASSERT_NEAR(x, 0.3f, 0.001f);
    memcpy(&cls, buf + 44, 4);
    ASSERT_EQ(cls, 1);
}

/* ========== 边界值测试 ========== */

TEST(Boundary, confidence_threshold) {
    detection_result dets[2] = {
        {10, 10, 20, 20, 0.25f, 0},  // exactly threshold
        {50, 50, 20, 20, 0.24f, 0}   // below threshold
    };
    // The process function filters by CONFIDENCE_THRESHOLD = 0.25f
    // We test the NMS separately from threshold filtering here
    detection_result out[2];
    int count = nms(dets, 2, out, 2);
    ASSERT_EQ(count, 2); // NMS doesn't filter by confidence, just sorting
}

TEST(Boundary, nms_threshold) {
    // Two boxes at exactly NMS_THRESHOLD IoU
    detection_result dets[2] = {
        {0, 0, 100, 100, 0.9f, 0},
        {55, 0, 100, 100, 0.8f, 0}
    };
    // IoU = overlap(55*100=5500) / union(10000+10000-5500=14500) = 0.379
    // 0.379 < 0.45, so NOT suppressed
    detection_result out[2];
    int count = nms(dets, 2, out, 2);
    ASSERT_EQ(count, 2);
}

int main(void) {
    return RUN_ALL_TESTS();
}
