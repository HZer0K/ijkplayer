# ijkplayer

> Video player based on [ffplay](http://ffmpeg.org) — Android arm64 本地构建增强版

## 项目简介

本仓库基于 [bilibili/ijkplayer](https://github.com/bilibili/ijkplayer) 深度改造，主要面向 **Android arm64** 平台，重点增强了以下能力：

- **播放内核**：IjkMediaPlayer（FFmpeg 8）与 ExoPlayer 双内核，运行时无缝切换
- **Vulkan 支持**：FFmpeg 编译时开启 `--enable-vulkan`，保留 Vulkan 设备渲染能力
- **HTTPS/TLS**：集成 OpenSSL 3.3.x，支持 HTTPS 直播与点播
- **离线 ASR 字幕**：集成 Whisper.cpp，支持实时语音识别生成字幕（可选编译）
- **音频处理**：集成 SoundTouch 变速、libsoxr 高质量重采样
- **图像处理**：集成 libyuv 色彩空间转换
- **AI 推理框架**：集成 llama.cpp（LLM 多模态对话）+ MNN（CV 任务），完全异步化不阻塞播放
- **现代 Android UI**：Material3 + BottomSheet + Preference 统一 UI体系
- **Prefab 集成**：AAR 开启 Prefab publishing，方便下游 CMake 项目直接链接

---

## 架构概览

```
ijkplayer
├── ijkmedia/          # Native 核心（C/C++）
│   ├── ijkplayer/     # 播放器主逻辑
│   ├── ijksdl/        # SDL 抽象层（渲染、音频输出）
│   ├── ijkyuv/        # libyuv 封装
│   ├── ijksoundtouch/ # SoundTouch 封装
│   └── ijkai/         # AI 推理框架 👉 [文档](doc/ai.md)
├── extra/             # 第三方库源码（FFmpeg、llama.cpp、MNN 等）
├── android/
│   ├── contrib/       # 第三方库构建脚本
│   └── ijkplayer/     # Android 工程
│       ├── ijkplayer-java/    # Java 接口层
│       ├── ijkplayer-arm64/   # arm64 预编译 so + AAR
│       ├── ijkplayer-exo/     # ExoPlayer 适配层
│       └── ijkplayer-example/ # Demo App
└── config/            # FFmpeg 模块配置
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

> 详细构建步骤、环境配置、Whisper 编译、Prefab 集成请见 [doc/build.md](doc/build.md)

---

## AI 推理框架

支持 LLM 推理 + CV 超分辨率/目标检测，**完全异步**不阻塞播放。

| 后端 | 用途 | 状态 |
|------|------|------|
| llama.cpp | LLM 对话、多模态理解 | ⏸️ 待集成 |
| MNN | 超分辨率、目标检测 | ✅ 已完成 |

- [AI 框架架构与测试文档](doc/ai.md)

---

## Demo App 功能

Demo App（`android/ijkplayer/ijkplayer-example`）提供以下功能：

- **播放器**：IjkMediaPlayer / ExoPlayer 双内核切换
- **视频滤镜**：渲染层滤镜 + FFmpeg 软件滤镜，运行时实时切换
- **离线 ASR 字幕**：Whisper 语音识别实时生成字幕
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
