# ijkplayer

Video player based on [ffplay](http://ffmpeg.org)

## 说明（本仓库已做大量调整）

这是基于 bilibili/ijkplayer 的改造版，重点面向 **Android arm64** 的本地构建与 Demo 可用性（调试信息、内核切换、样例数据源等）。

## 主要变化（相对上游）

- Android 仅保留/优先支持 arm64（简化构建与调试）
- FFmpeg 构建链路支持 OpenSSL（用于 Ijk 播放 https/hls），并在构建脚本中自动触发 OpenSSL 编译
- Demo 播放页 UI 调整：顶部工具栏菜单整合入口（Open URL / HLS / MP4 / 重建播放 / 同源重开 / Player 切内核等）
- 播放控制栏默认显示更久（避免一闪而过）
- 错误弹窗增强：统一展示 errorCode + 分类提示 + 最近关键日志；并补全 IJK(FFmpeg) 网络错误细节（HTTP 状态码/最终 URL/部分响应字段）支持一键复制
- Demo 边缘返回手势冲突修复：禁用右侧 Drawer 边缘滑动打开，避免抢占系统返回手势
- 样例列表从 JSON 数据源加载并按分类展示（`res/raw/sample_media.json`）
- 增加设置项：Prefer Exo for HTTP/HTTPS、Vulkan filter 等（用于功能验证与对比）

## Android 构建（推荐 Linux / WSL2）

### 环境依赖
- Android SDK + NDK（建议 NDK r27 系列）
- bash、make、git
- perl（OpenSSL Configure 需要）
- curl 或 wget（若本地没有 OpenSSL 源码时会自动下载）

### 选择 FFmpeg 模块配置（可选）
默认使用 [config/module-lite.sh](config/module-lite.sh)。

如果需要调整：
```bash
cd config
rm -f module.sh
ln -s module-lite.sh module.sh
```

### 构建 FFmpeg（arm64）
```bash
export ANDROID_SDK=/path/to/Android/Sdk
export ANDROID_NDK=/path/to/android-ndk-r27

./init-android.sh

cd android/contrib
./compile-ffmpeg.sh clean
./compile-ffmpeg.sh arm64
```

#### OpenSSL/HTTPS 开关（可选）
- 默认会在编译 FFmpeg 前自动编译 OpenSSL（用于 https 协议）
- 可用环境变量控制：
  - `IJK_ENABLE_OPENSSL=0`：禁用 OpenSSL 编译与 https 支持
  - `OPENSSL_VER=3.3.2`：指定 OpenSSL 版本（需要存在对应源码目录，或允许脚本下载）
  - `OPENSSL_API_LEVEL=24`：指定 OpenSSL 编译的 Android API level

### 构建 ijkplayer so（arm64）
```bash
cd android
./compile-ijk.sh all
```

## Demo 构建与运行

### Gradle 命令行
```bash
cd android/ijkplayer
./gradlew :ijkplayer-example:assembleDebug -x lint
```

### Demo 样例数据
样例列表从 `android/ijkplayer/ijkplayer-example/src/main/res/raw/sample_media.json` 加载并按 category 分组展示。

## Demo 播放页功能速览
- 顶部工具栏菜单：Open URL / HLS 样例 / MP4 样例 / 清空并播放（重建）/ 同源重开(Seek=0)
- Player 按钮：Ijk ↔ Exo 内核切换并重建播放
- 失败弹窗：错误码 + 分类提示 + 网络细节（HTTP 状态码/最终 URL/部分响应字段）+ 最近 20 行关键日志（可复制）
- 返回手势：右侧 Drawer 不再抢占边缘返回（Tracks 仅通过菜单打开）

## License
```text
Copyright (c) 2017 Bilibili
Licensed under LGPLv2.1 or later
```

---

下方为上游 ijkplayer 的历史信息与依赖声明（如需对照可从 GitHub 上游仓库查看）。
