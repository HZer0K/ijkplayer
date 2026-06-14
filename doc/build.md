# 构建指南

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

## 构建 Android arm64

### 1. 初始化子模块

```bash
./init-android.sh
```

### 2. 选择 FFmpeg 模块配置（可选）

默认使用 `config/module-lite.sh`（精简编解码器集合）。
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

### 5. 构建 llama.cpp（可选，AI 推理需要）

如果需要 LLM 对话功能，需先编译 llama.cpp：

```bash
# 初始化 llama.cpp 源码（首次）
cd ../..   # 回到 ijkplayer/ 根目录
./init-android-llama.sh

# 编译 llama.cpp
cd android/contrib
./compile-llama.sh
```

编译产物：`android/contrib/build/llama/output/`

> **关键选项**：编译脚本已默认设置 `-DGGML_OPENMP=OFF`，禁用 OpenMP 以避免运行时缺少 `libomp.so` 导致崩溃。
>
> 如需手动调整，可编辑 `compile-llama.sh` 中的 CMake 参数。

### 6. 构建 ijkplayer so（arm64）

```bash
cd android
./compile-ijk.sh arm64
```

> **注意**：`compile-ijk.sh` 要求同时设置 `ANDROID_NDK` 和 `ANDROID_SDK` 环境变量：
> ```bash
> export ANDROID_NDK=/path/to/android-ndk-r27
> export ANDROID_SDK=/path/to/Android/Sdk
> ```

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

### 7. 构建 Demo APK

```bash
cd android/ijkplayer
./gradlew :ijkplayer-example:assembleDebug -x lint
```

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
