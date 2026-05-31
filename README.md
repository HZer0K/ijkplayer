# ijkplayer

> Video player based on [ffplay](http://ffmpeg.org) — Android arm64 本地构建增强版

## 项目简介

本仓库基于 [bilibili/ijkplayer](https://github.com/bilibili/ijkplayer) 深度改造，主要面向 **Android arm64** 平台，重点增强了以下能力：

- **播放内核**：IjkMediaPlayer（FFmpeg 8）与 ExoPlayer 双内核，运行时无缝切换
- **Vulkan 支持**：FFmpeg 编译时开启 `--enable-vulkan`，保留 Vulkan 设备渲染能力
- **HTTPS/TLS**：集成 OpenSSL 3.3.x，支持 HTTPS 直播与点播
- **音频处理**：集成 SoundTouch 变速、libsoxr 高质量重采样
- **图像处理**：集成 libyuv 色彩空间转换
- **AI 推理框架**：集成 llama.cpp（LLM 多模态对话）+ MNN（CV 任务），完全异步化不阻塞播放
- **现代 Android UI**：Material3 + BottomSheet + Preference 统一 UI体系
- **Prefab 集成**：AAR 开启 Prefab publishing，方便下游 CMake 项目直接链接

---

## 架构概览

```
ijkplayer/
├── README.md                    # 项目概览
├── doc/                         # 文档
│   ├── build.md                 #   构建指南
│   ├── faq.md                   #   常见问题
│   └── ai.md                    #   AI 框架文档
├── ijkmedia/                    # Native 核心（C/C++）
│   ├── ijkplayer/               #   播放器主逻辑
│   ├── ijksdl/                  #   SDL 抽象层（渲染、音频输出）
│   │   ├── audio/               #     音频输出后端
│   │   ├── video/               #     视频渲染后端（Vulkan/OpenGL）
│   │   └── gles2/               #     GLES2 辅助
│   ├── ijkyuv/                  #   libyuv 色彩空间转换
│   │   ├── convert/             #     色彩空间转换
│   │   └── rotate/              #     旋转/缩放
│   ├── ijksoundtouch/           #   SoundTouch 变速变调
│   ├── ijkai/                   #   AI 推理框架 👉 [doc/ai.md](doc/ai.md)
│   │   ├── async/               #     异步任务队列
│   │   ├── llm/                 #     LLM 后端（llama.cpp）
│   │   ├── cv/                  #     CV 后端（MNN）
│   │   ├── ijkai.c              #     调度层
│   │   └── ijkai_pipenode.c     #     Pipenode 封装
│   └── compat/                  #   兼容层
├── extra/                       # 第三方库源码
│   ├── ffmpeg/                  #   FFmpeg 8（含 Vulkan 支持）
│   ├── libyuv/                  #   libyuv
│   ├── soundtouch/              #   SoundTouch
│   ├── llama.cpp/               #   LLM 推理引擎（可选）
│   └── MNN/                     #   CV 推理引擎（可选）
├── android/
│   ├── contrib/                 #   第三方库构建脚本
│   │   ├── compile-ffmpeg.sh    #     FFmpeg 交叉编译
│   │   ├── compile-llama.sh     #     llama.cpp 编译（可选）
│   │   ├── compile-openssl.sh   #     OpenSSL 编译
│   │   └── compile-glslang.sh   #     glslang 编译（Vulkan 滤镜）
│   └── ijkplayer/               #   Android 工程
│       ├── ijkplayer-java/      #     Java 接口层
│       ├── ijkplayer-arm64/     #     arm64 预编译 so + AAR
│       │   └── src/main/cpp/    #       CMake 构建入口
│       ├── ijkplayer-exo/       #     ExoPlayer 适配层
│       └── ijkplayer-example/   #     Demo App
├── config/                      # FFmpeg 模块选择
│   ├── module-default.sh        #   完整解码器集合
│   ├── module-lite.sh           #   精简集合（默认）
│   └── module-lite-hevc.sh      #   精简 + HEVC
└── 脚本文件
    ├── init-android.sh          #   子模块初始化
    ├── init-android-openssl.sh  #   OpenSSL 初始化
    ├── init-android-llama.sh    #   llama.cpp 初始化（可选）
    ├── init-android-mnn.sh      #   MNN 初始化（可选）
    ├── init-android-soundtouch.sh
    └── init-android-libsoxr.sh
```

---

## 快速开始

```bash
# 1. 初始化子模块
./init-android.sh

# 2. 构建 FFmpeg（arm64）
cd android/contrib
./compile-ffmpeg.sh clean
./compile-ffmpeg.sh arm64

# 3. 构建 ijkplayer so
cd ..
./compile-ijk.sh arm64

# 4. 构建 Demo APK
cd ../android/ijkplayer
./gradlew :ijkplayer-example:assembleDebug -x lint
```

> 详细构建步骤、环境配置、Prefab 集成请见 [doc/build.md](doc/build.md)

---

## AI 推理框架

支持 LLM 推理 + CV 超分辨率/目标检测，**完全异步**不阻塞播放。

| 后端 | 用途 | 状态 |
|------|------|------|
| llama.cpp | LLM 对话、多模态理解 | ✅ 已完成 |
| MNN | 超分辨率、目标检测 | ✅ 已完成 |

- [AI 框架架构与测试文档](doc/ai.md)

---

## Demo App 功能

Demo App（`android/ijkplayer/ijkplayer-example`）提供以下功能：

- **播放器**：IjkMediaPlayer / ExoPlayer 双内核切换
- **视频滤镜**：渲染层滤镜 + FFmpeg 软件滤镜，运行时实时切换
- **功能测试**：Vulkan 能力检测、滤镜链路自检
- **调试诊断**：错误弹窗、日志复制、Track 切换

---

## 常见问题

遇到问题请查阅 [doc/faq.md](doc/faq.md)，涵盖：
- 滤镜相关（avgblur 未找到、Vulkan 编译报错）
- 运行时崩溃（libc++_shared.so 找不到）
- AI 推理相关问题

---

## License

```
Copyright (c) 2013-2017 Bilibili
Licensed under LGPLv2.1 or later
```

ijkplayer 依赖的各第三方库（FFmpeg、OpenSSL、libyuv、SoundTouch 等）保留各自的原始 License，详见各库目录下的 LICENSE 文件。
