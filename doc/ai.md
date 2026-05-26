# AI 推理框架

## 架构设计

AI 推理框架采用 **双后端 + 统一接口** 架构：

- **LLM 后端**：llama.cpp（必选），支持 GGUF 格式模型，多模态对话、内容理解
- **CV 后端**：MNN（可选），支持超分辨率、目标检测等视觉任务
- **统一接口**：`ijkai.h` 提供单一 C API，完全解耦播放器与底层推理引擎
- **异步队列**：有界任务队列，自动丢弃过期帧，不阻塞视频播放

```
ijkmedia/ijkai/
├── ijkai.h                     # 统一对外接口
├── ijkai.c                     # 调度层实现
├── ijkai_pipenode.h / .c       # Pipenode 封装（与播放器 pipeline 集成）
├── ijkai_jni.c                 # JNI 桥接层
├── llm/
│   ├── ijkai_llm_impl.h        # LLM 内部头文件
│   └── ijkai_llm.c             # LLM 实现（占位/集成）
├── cv/
│   ├── ijkai_cv.h              # CV 内部头文件
│   ├── ijkai_cv.c              # CV 调度实现
│   ├── ijkai_cv_sr.c           # 超分辨率实现
│   ├── ijkai_cv_detect.c       # 目标检测实现
│   └── ijkai_mnn_wrap.c        # MNN 封装
├── async/
│   ├── ijkai_queue.h           # 异步队列头文件
│   └── ijkai_queue.c           # 异步队列实现
└── test/                       # 单元测试（见下方）
```

## 实现状态

### 已完成

- ✅ 统一接口层（`ijkai.h` / `ijkai.c`）
- ✅ Pipenode 封装（`ijkai_pipenode.c`）
- ✅ JNI 桥接层（`ijkai_jni.c`）
- ✅ Java API（`IjkAIEngine.java`）
- ✅ 异步队列（`async/ijkai_queue.c`）
- ✅ CV 模块（超分辨率 + 目标检测）
- ✅ LLM 基础框架（`llm/ijkai_llm.c`）
- ✅ 编译脚本（`init-android-llama.sh` / `compile-llama.sh`）
- ✅ 91 个桌面端 C 单元测试

### 待完成

- ⏸️ llama.cpp 集成（等待编译完成后替换占位实现）

## Java API 使用示例

```java
// 1. 初始化 LLM 引擎
IjkAIEngine aiEngine = new IjkAIEngine();
aiEngine.initLLM("/sdcard/models/llama-3.2-1b-q4.gguf", 4);

// 2. 异步推理（不阻塞主线程）
aiEngine.promptAsync("你好，请介绍一下自己", new IjkAIEngine.LLMCallback() {
    @Override
    public void onText(String text, boolean isComplete) {
        if (isComplete) {
            Log.d("AI", "推理完成: " + text);
        } else {
            Log.d("AI", "流式输出: " + text);
        }
    }

    @Override
    public void onError(String error) {
        Log.e("AI", "推理失败: " + error);
    }
});

// 3. 释放资源
aiEngine.release();
```

---

## 单元测试

AI 推理框架配套 **91 个桌面端 C 单元测试**，可在 Linux/Windows(WSL2)/macOS 上快速验证核心逻辑，无需 Android 设备。

### 测试套件概览

| 测试文件 | 测试数 | 覆盖内容 |
|---------|-------|---------|
| `test/test_ijkai_algo.c` | 23 | IoU/NMS 算法、RGBA↔NCHW 颜色转换、序列化、边界阈值 |
| `test/test_ijkai_queue.c` | 24 | 异步队列（创建/释放、FIFO、优先级淘汰、多线程、压力测试） |
| `test/test_ijkai_core.c` | 30 | 核心框架（各类型 init/release、LLM Prompt、CV 路由、多模态、Backend 设置、统计查询） |
| `test/test_ijkai_pipenode.c` | 14 | Pipenode 生命周期（创建/销毁、run_sync、flush、多节点并发） |
| **合计** | **91** | |

### 快速运行

```bash
cd ijkmedia/ijkai/test

# 方式一：一键运行所有测试
chmod +x run_tests.sh
./run_tests.sh

# 方式二：单独运行算法测试
gcc -std=c99 -pthread -I.. -I../async -I. test_ijkai_algo.c -lm -o test_ijkai_algo && ./test_ijkai_algo
```

### 测试设计

**桩模块（Stub）**：`test_ijkai_core` 和 `test_ijkai_pipenode` 使用 `test_stubs.h/.c` 替代真实的 LLM（llama.cpp）和 CV（MNN）依赖，通过 `-include` 在编译时注入，无需第三方库即可编译运行。

### 预期输出

```
========================================
  Running 23 test(s)
========================================
[ RUN  ] IoU.identical_boxes
[  OK  ] IoU.identical_boxes
...
========================================
  23 passed, 0 failed out of 23
========================================
```

所有 4 个测试程序均通过后，`run_tests.sh` 输出：

```
  通过: 4
  失败: 0
  🎉 全部测试通过!
```

### 编译 llama.cpp 并集成

```bash
export ANDROID_NDK=/path/to/android-ndk-r27d
cd android/contrib
./compile-llama.sh
```

编译完成后，在 `android/ijkplayer/ijkplayer-arm64/src/main/cpp/CMakeLists.txt` 中设置 `IJKAI_ENABLE_LLM=ON`，重新编译 ijkplayer 即可启用 LLM 功能。
