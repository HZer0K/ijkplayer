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
- **现代 Android UI**：Material3 + BottomSheet + Preference 统一 UI 体系
- **Prefab 集成**：AAR 开启 Prefab publishing，方便下游 CMake 项目直接链接

---

## 架构概览

```
ijkplayer
├── ijkmedia/          # Native 核心（C/C++）
│   ├── ijkplayer/     # 播放器主逻辑（ff_ffplay 等）
│   ├── ijksdl/        # SDL 抽象层（OpenGL ES 渲染、音频输出）
│   ├── ijkyuv/        # libyuv 封装
│   └── ijksoundtouch/ # SoundTouch 封装
├── android/
│   ├── contrib/       # 第三方库构建脚本（FFmpeg、OpenSSL、glslang）
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
./compile-ffmpeg.sh clean
./compile-ffmpeg.sh arm64
```

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
./compile-ijk.sh all
```

脚本使用 **CMake + Ninja** 构建；若系统无 Ninja，自动回退到 Make。

**构建日志**：输出到 `android/build/logs/`，可通过环境变量覆盖：
- `IJK_LOG_DIR=/path/to/logs` — 指定日志目录
- `IJK_LOG_FILE=/path/to/file.log` — 指定具体日志文件

### 5. 构建 Demo APK

```bash
cd android/ijkplayer
./gradlew :ijkplayer-example:assembleDebug -x lint
```

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
- **Open URL**：顶部菜单手动输入任意 URL 播放

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

---

## UI / 主题说明

- 全局主题：`Theme.Material3.DayNight.NoActionBar` + `MaterialToolbar`
- 设置页：`PreferenceFragmentCompat` + `SwitchPreferenceCompat`，使用 `PreferenceThemeOverlay` 与 Material3 主题协调
- 若需要完整 Material3 一致性，可选：
  - **方案 A**：为 Preference 自定义 layout，使用 Material3 组件实现
  - **方案 B**：迁移设置页到 Jetpack Compose（Material3）

---

## 常见问题

**Q: FFmpeg 编译报 `spirv_compiler not found`？**  
A: 这是 Android NDK 下 `libglslang` 检测失败的已知问题（NDK libc 内置 pthread/stdc++，configure 无法独立链接）。已通过默认禁用 Vulkan filters（`IJK_ENABLE_VULKAN_FILTERS=0`）解决，Vulkan 设备支持不受影响。

**Q: 链接阶段报 `ff_vk_* duplicate symbol`？**  
A: FFmpeg 8 构建系统将 `libavutil/vulkan.c` 同时编译进 `libavcodec.a` 和 `libavutil.a`。已在链接命令加入 `-Wl,--allow-multiple-definition` 解决，行为安全（两份来源相同）。

**Q: `./compile-glslang.sh: 权限不够`？**  
A: 执行一次 `chmod +x android/contrib/*.sh` 给所有构建脚本添加执行权限。

**Q: `libavcodec/avfft.h` 找不到？**  
A: `avfft.h` 在 FFmpeg 6.0 中已删除。ijkplayer 代码已改为条件包含，FFmpeg 6+ 时自动使用内联 stub，无需手动修改。

---

## License

```text
Copyright (c) 2013-2017 Bilibili
Licensed under LGPLv2.1 or later
```

ijkplayer 依赖的各第三方库（FFmpeg、OpenSSL、libyuv、SoundTouch 等）保留各自的原始 License，详见各库目录下的 LICENSE 文件。

---

> 下方历史信息已移除，如需参考上游原版说明，请访问 [bilibili/ijkplayer](https://github.com/bilibili/ijkplayer)。
