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
│   ├── ijkplayer/     # 播放器主逻辑（ff_ffplay 等）
│   ├── ijksdl/        # SDL 抽象层（OpenGL ES 渲染、音频输出）
│   ├── ijkyuv/        # libyuv 封装
│   ├── ijksoundtouch/ # SoundTouch 封装
│   └── ijkai/         # AI 推理框架（llama.cpp + MNN）
├── extra/             # 第三方库源码
│   ├── llama.cpp/     # LLM 推理引擎（多模态对话、内容理解）
│   ├── ffmpeg/        # FFmpeg 8 源码
│   ├── openssl-3.3.2/ # OpenSSL 源码
│   └── ...            # 其他第三方库
├── android/
│   ├── contrib/       # 第三方库构建脚本（FFmpeg、OpenSSL、llama.cpp、MNN）
│   └── ijkplayer/     # Android 工程
│       ├── ijkplayer-java/    # Java 接口层（IMediaPlayer）
│       ├── ijkplayer-arm64/   # arm64 预编译 so + AAR
│       ├── ijkplayer-exo/     # ExoPlayer 适配层
│       └── ijkplayer-example/ # Demo App
└── config/            # FFmpeg 模块配置（module-lite.sh 等）
```

---

## 环境依赖

| 依赖 | 版本要求 | 备注 |
|------|---------|------|
| Android NDK | r27（推荐） | r23+ 均可，使用 clang 工具链 |
| Android SDK | 含 CMake 3.22+ / Ninja | Gradle 构建需要 |
| bash / make / git | 系统自带 | Linux / WSL2 环境 |
| perl | 5.x | OpenSSL Configure 需要，缺少模块时脚本自动注入兼容实现 |
| curl 或 wget | 任意版本 | 无本地源码时自动下载 |
| C++17 编译器 | 支持 C++17 | llama.cpp 需要 C++17 标准 |

> **WSL2 提示**：请用 `export` 导出环境变量，子进程才能继承：
> ```bash
> export ANDROID_SDK=/path/to/Android/Sdk
> export ANDROID_NDK=/path/to/android-ndk-r27
> ```

---

## 构建指南（Android arm64）

### 1. 初始化子模块

```bash
./init-android.sh
```

### 2. 选择 FFmpeg 模块配置（可选）

默认使用 [`config/module-lite.sh`](config/module-lite.sh)（精简编解码器集合）。
如需调整：

```bash
cd config
rm -f module.sh
ln -s module-default.sh module.sh   # 完整版
# 或
ln -s module-lite-hevc.sh module.sh  # 精简版 + HEVC
```

### 3. 构建 FFmpeg（arm64）

```bash
cd android/contrib
./compile-ffmpeg.sh clean   # 仅清理 build 输出目录，不修改 FFmpeg 源码
./compile-ffmpeg.sh arm64
```

> **注意**：`clean` 只删除 `build/ffmpeg-arm64/` 输出目录，不会执行 `git clean` 重置 FFmpeg 源码树。  
> 若需彻底重置源码（如切换版本），请手动进入 `ffmpeg-arm64/` 目录执行 `git clean -xdf`。

**构建产物**：`android/contrib/build/ffmpeg-arm64/output/`

#### 可选环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `IJK_ENABLE_OPENSSL` | `1` | 设为 `0` 禁用 OpenSSL/HTTPS |
| `OPENSSL_VER` | `3.3.2` | 指定 OpenSSL 版本 |
| `OPENSSL_API_LEVEL` | `24` | OpenSSL 编译的 Android API level |
| `IJK_ENABLE_VULKAN` | `1` | 设为 `0` 禁用 Vulkan 设备支持 |
| `IJK_ENABLE_VULKAN_FILTERS` | `0` | Vulkan GLSL 滤镜（需要 glslang，Android NDK 下默认禁用）|

### 4. 构建 ijkplayer so（arm64）

```bash
cd android
./compile-ijk.sh arm64
```

脚本使用 **CMake + Ninja** 构建；若系统无 Ninja，自动回退到 Make。

**构建产物**：`android/ijkplayer/ijkplayer-arm64/src/main/libs/arm64-v8a/`
- `libijkplayer.so`、`libijksdl.so` — 从 CMake 编译产出复制
- `libc++_shared.so` — 从 NDK 自动复制（运行时 C++ 标准库），APK 必须包含此文件

```bash
# 清理重编（同时清理 libs/ 下的旧 so，避免过期产物被打包进 APK）
./compile-ijk.sh clean
./compile-ijk.sh arm64
```

**构建日志**：输出到 `android/build/logs/`，可通过环境变量覆盖：
- `IJK_LOG_DIR=/path/to/logs` — 指定日志目录
- `IJK_LOG_FILE=/path/to/file.log` — 指定具体日志文件

### 5. 构建 Demo APK

```bash
cd android/ijkplayer
./gradlew :ijkplayer-example:assembleDebug -x lint
```

---

## AI 推理框架（Phase 1 进行中）

### 架构设计

AI 推理框架采用**双后端 + 统一接口**架构：

- **LLM 后端**：llama.cpp（必选），支持 GGUF 格式模型，多模态对话、内容理解
- **CV 后端**：MNN（可选），支持超分辨率、目标检测等视觉任务
- **统一接口**：`ijkai.h` 提供单一 C API，完全解耦播放器与底层推理引擎
- **异步队列**：有界任务队列（最多5个任务），自动丢弃过期帧（>100ms），不阻塞视频播放

### 构建步骤

#### 1. 初始化 llama.cpp

```bash
cd /home/hxk/project/IJKPLAYER/ijkplayer
./init-android-llama.sh
```

这会克隆 llama.cpp 仓库到 `extra/llama.cpp/` 目录。

#### 2. 编译 llama.cpp

```bash
export ANDROID_NDK=/path/to/android-ndk-r27d
cd android/contrib
./compile-llama.sh
```

编译4个架构：arm64-v8a / armeabi-v7a / x86 / x86_64

#### 3. 启用 LLM 编译选项

在 `android/ijkplayer/ijkplayer-arm64/src/main/cpp/CMakeLists.txt` 中添加：

```cmake
option(IJKAI_ENABLE_LLM "Enable LLM (llama.cpp)" ON)
```

#### 4. 重新编译 ijkplayer

```bash
cd android
./compile-ijk.sh arm64
```

### Java API 使用示例

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

### 架构设计文档

完整的架构设计文档请查看：[IJKPLAYER_AI框架架构设计.md](../IJKPLAYER_AI框架架构设计.md)

### Phase 1 进度

- ✅ 统一接口层（ijkai.h / ijkai.c）
- ✅ 异步队列实现（async/ijkai_queue.c）
- ✅ LLM 基础框架（llm/ijkai_llm.c）
- ✅ 初始化/编译脚本（init-android-llama.sh / compile-llama.sh）
- ⏸️ llama.cpp 集成（等待编译完成后替换占位实现）
- ⏸️ JNI 桥接层
- ⏸️ Java API

详细进度请查看：[ijkmedia/ijkai/README_PHASE1.md](ijkmedia/ijkai/README_PHASE1.md)

---

## Whisper 离线 ASR 字幕（可选）

默认**关闭**，开启后构建时需联网下载 whisper.cpp（~100 MB）。

```bash
# 方式一：修改 gradle.properties
echo 'enableWhisper=true' >> android/ijkplayer/gradle.properties

# 方式二：命令行传参
./gradlew -PenableWhisper=true :ijkplayer-example:assembleWhisper64
```

开启后，Demo 设置页可下载 Whisper 模型并实时生成字幕。推理线程数自动适配设备 CPU 核心数（最多 8 线程）。

---

## Demo App 功能说明

### 播放功能
- **双内核切换**：运行时点击 *Player* 按钮，在 IjkMediaPlayer（FFmpeg）和 ExoPlayer 之间切换
- **样例媒体**：从 `ijkplayer-example/src/main/res/raw/sample_media.json` 加载，按 category 分组展示
- **Open URL**：TestHub 页面手动输入任意 URL 播放

### 视频滤镜
播放页右上角菜单提供两类滤镜，均可运行时实时切换，无需重启播放：

**渲染层滤镜**（TextureView ColorMatrix / View 变换，不消耗 FFmpeg 解码线程）：
- 无滤镜 / 灰度 / 水平翻转 / 垂直翻转 / 提亮 / 压暗 / 旋转90°

**FFmpeg 软件滤镜**（通过 vf0 传入 FFmpeg filter graph，需 `CONFIG_AVFILTER=1` 和对应 filter 编译进 so）：
- 水平翻转（hflip）/ 垂直翻转（vflip）/ 高斯模糊（gblur=sigma=5）/ FFmpeg 提亮 / FFmpeg 压暗

> 注意：FFmpeg 滤镜调用 `setVideoFilter()` 运行时更新 filter graph（设置 `vf_changed=1`），**不是** `setOption("vf0", ...)`（后者仅写 AVDictionary，不触发重建）。

### 功能测试（TestHub）
主页 → TestHub 页面包含：
- **功能测试**（TestCaseList）：按 category 分组的测试用例，JSON 驱动（`res/raw/test_feature_cases.json`），点击用例自动打开播放页并预设 vf0 滤镜
  - 软件滤镜：hflip 链路自检
  - Vulkan 滤镜：scale / hflip / vflip / transpose / gblur / avgblur / chromaber（需 Vulkan 设备）
  - 软件模糊：gblur=sigma=8:steps=3 / avgblur=sizeX=16:sizeY=16（无需 Vulkan，任意设备可用）
- **Vulkan 能力检查**：检测设备 Vulkan 硬件支持、build-time Vulkan/filter 开关
- **样例列表 / 格式样例**：网络视频样例（HLS / MP4 / LAS 等）

> Vulkan 专属效果（chromaber_vulkan 色差、blend_vulkan、overlay_vulkan）无 CPU 等效实现，设备不支持 Vulkan 时自动 passthrough 并弹出提示。

### 调试与诊断
- **失败弹窗**：显示错误码、分类提示、HTTP 状态码/最终 URL，以及最近 20 行关键日志（可一键复制）
- **Track 切换**：BottomSheet 弹出，避免 DrawerLayout 抢占边缘返回手势

### 设置页
- Whisper 模型下载与 ASR 开关
- 下载失败自动清理残留文件，支持干净重试

---

## Prefab 集成（面向接入方）

`ijkplayer-arm64` AAR 已开启 Prefab publishing，下游 CMake 项目可直接链接：

```groovy
// app/build.gradle
android {
    buildFeatures { prefab true }
}
dependencies {
    implementation project(':ijkplayer-arm64')
}
```

```cmake
# CMakeLists.txt
find_package(ijkplayer CONFIG REQUIRED)
target_link_libraries(myapp ijkplayer::ijkplayer ijkplayer::ijkffmpeg)
```

| Prefab 包名 | 对应 so |
|-------------|--------|
| `ijkplayer` | `libijkplayer.so` |
| `ijkffmpeg` | `libijkffmpeg.so` |
| `ijksdl` | `libijksdl.so` |
| `ijkai` | `libijkai.so`（Phase 2 提供） |

> 注意：`ijkai` 包需要启用 `IJKAI_ENABLE_LLM` 或 `IJKAI_ENABLE_CV` 编译选项后才会生成。

---

## UI / 主题说明

- 全局主题：`Theme.Material3.DayNight.NoActionBar` + `MaterialToolbar`
- 设置页：`PreferenceFragmentCompat` + `SwitchPreferenceCompat`，使用 `PreferenceThemeOverlay` 与 Material3 主题协调
- 若需要完整 Material3 一致性，可选：
  - **方案 A**：为 Preference 自定义 layout，使用 Material3 组件实现
  - **方案 B**：迁移设置页到 Jetpack Compose（Material3）

---

## 常见问题

**Q: 均值模糊（avgblur）或其他软件 filter 报 `Filter not found`？**  
A: `module-lite.sh` 精确控制了哪些 FFmpeg filter 被编译进 so。`avgblur`、`pad` 等与 Vulkan 无关的 CPU filter 需要在 `IJK_ENABLE_FILTERS=1` 段显式 `--enable-filter=avgblur`。当前 `module-lite.sh` 已包含 `avgblur` 和 `pad`；若使用自定义配置，请确保所用 filter 均已显式启用，然后重新编译 FFmpeg so。

**Q: FFmpeg 编译报 `spirv_compiler not found`？**  
A: 这是 Android NDK 下 `libglslang` 检测失败的已知问题（NDK libc 内置 pthread/stdc++，configure 无法独立链接）。已通过默认禁用 Vulkan filters（`IJK_ENABLE_VULKAN_FILTERS=0`）解决，Vulkan 设备支持不受影响。

**Q: 链接阶段报 `ff_vk_* duplicate symbol`？**  
A: FFmpeg 8 构建系统将 `libavutil/vulkan.c` 同时编译进 `libavcodec.a` 和 `libavutil.a`。已在链接命令加入 `-Wl,--allow-multiple-definition` 解决，行为安全（两份来源相同）。

**Q: 运行时崩溃 `UnsatisfiedLinkError: library "libc++_shared.so" not found`？**  
A: `libijkplayer.so` 依赖 C++ 共享运行时 `libc++_shared.so`，APK 中必须包含该文件。`compile-ijk.sh` 会自动将 NDK 中的 `libc++_shared.so` 复制到 `libs/arm64-v8a/`，Gradle 打包时会自动包含。若仍报错，请先执行 `./compile-ijk.sh clean && ./compile-ijk.sh arm64` 重新构建。

**Q: `./compile-glslang.sh: 权限不够`？**  
A: 执行一次 `chmod +x android/contrib/*.sh` 给所有构建脚本添加执行权限。

**Q: `libavcodec/avfft.h` 找不到？**  
A: `avfft.h` 在 FFmpeg 6.0 中已删除。ijkplayer 代码已改为条件包含，FFmpeg 6+ 时自动使用内联 stub，无需手动修改。

**Q: AI 推理会阻塞视频播放吗？**  
A: 不会。AI 框架采用完全异步架构，推理任务在有界队列中执行，独立工作线程处理，不阻塞视频解码与渲染主线程。队列满时自动丢弃过期任务（>100ms），确保实时性。

**Q: 如何启用 LLM 功能？**  
A: 需要先初始化并编译 llama.cpp，然后在 CMakeLists.txt 中设置 `IJKAI_ENABLE_LLM=ON`，重新编译 ijkplayer。详细步骤见上方「AI 推理框架」章节。

**Q: 支持哪些 LLM 模型？**  
A: 支持 GGUF 格式的模型，推荐使用 llama-3.2-1b/3b（轻量级，适合移动端），或 phi-3-mini、qwen-2.5-1.5b 等。模型需要从 HuggingFace 下载并放到设备存储中。

---

## License

```text
Copyright (c) 2013-2017 Bilibili
Licensed under LGPLv2.1 or later
```

ijkplayer 依赖的各第三方库（FFmpeg、OpenSSL、libyuv、SoundTouch 等）保留各自的原始 License，详见各库目录下的 LICENSE 文件。

---

> 下方历史信息已移除，如需参考上游原版说明，请访问 [bilibili/ijkplayer](https://github.com/bilibili/ijkplayer)。
