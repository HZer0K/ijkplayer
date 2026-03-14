# ijkplayer

Video player based on [ffplay](http://ffmpeg.org)

## 说明（本仓库已做大量调整）

这是基于 bilibili/ijkplayer 的改造版，重点面向 **Android arm64** 的本地构建与 Demo 可用性（调试信息、内核切换、样例数据源等）。

## Android 构建（推荐 Linux / WSL2）

### 环境依赖
- Android SDK + NDK（建议 NDK r27 系列）
- bash、make、git
- perl（OpenSSL Configure 需要；若缺少 `Locale::Maketext::Simple` 模块，脚本会自动注入兼容实现）
- 若系统 Perl 过于精简导致缺少 `ExtUtils::MakeMaker`，脚本会自动注入兼容实现（避免 OpenSSL Configure 失败）
- curl 或 wget（若本地没有 OpenSSL 源码时会自动下载）
- NDK r23+ 不再提供 gcc，OpenSSL/FFmpeg 构建使用 clang（脚本会自动指定对应 clang wrapper）

在 Linux / WSL 中请确保用 export 导出环境变量（否则脚本子进程拿不到）：
```bash
export ANDROID_SDK=/path/to/Android/Sdk
export ANDROID_NDK=/path/to/android-ndk-r27
```

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
`compile-ijk.sh` 已迁移为 CMake + Ninja 构建（需要 SDK 里安装 CMake/Ninja，或系统 PATH 可用）。
若未安装 Ninja，脚本会自动回退到 Makefile 生成器（需要系统可用的 make/mingw32-make）。

#### 构建日志（本地文件）
- 默认输出到 `android/build/logs/` 与 `android/contrib/build/logs/`
- 可用环境变量覆盖：
  - `IJK_LOG_DIR=/path/to/logs`：指定日志目录
  - `IJK_LOG_FILE=/path/to/file.log`：指定日志文件（覆盖默认命名）

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
- 返回手势：Tracks 使用 BottomSheet，避免 DrawerLayout 抢占边缘返回

## UI/Theme（Material3 + Preference 方案）
- Demo UI 使用 `Theme.Material3.DayNight.NoActionBar` 作为全局主题，并替换 Toolbar 为 `MaterialToolbar`
- 设置页继续使用 `androidx.preference`（`PreferenceFragmentCompat` + `SwitchPreferenceCompat`）
- `PreferenceThemeOverlay.Material3/MaterialComponents` 这类 style 在当前依赖组合中并不存在，因此采用 `PreferenceThemeOverlay`（androidx.preference）配合全局 Material3 主题统一观感
- 如果需要“完整 Material3 的 Preference 组件级样式一致性”，建议两条路线：
  - 方案 A：为 preference 自定义 layout（switch widget / list item）并用 Material 组件实现
  - 方案 B：迁移设置页到 Jetpack Compose（Material3），彻底统一控件体系

## CMake/Prefab（面向接入方的 Native 集成）
- `android/ijkplayer/ijkplayer-arm64` 作为 AAR 提供预编译 so（arm64-v8a），并开启 Prefab publishing
- Prefab 包名与 so 对应关系：
  - `ijkplayer` → `libijkplayer.so`
  - `ijkffmpeg` → `libijkffmpeg.so`
  - `ijksdl` → `libijksdl.so`
- 接入方使用时，在 app 模块开启 `buildFeatures { prefab true }`，CMake 侧可 `find_package(ijkplayer CONFIG REQUIRED)` 并链接 `ijkplayer::ijkplayer`

## License
```text
Copyright (c) 2017 Bilibili
Licensed under LGPLv2.1 or later
```

---

下方为上游 ijkplayer 的历史信息与依赖声明（如需对照可从 GitHub 上游仓库查看）。
