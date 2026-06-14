# 常见问题

## 滤镜相关

**Q: 均值模糊（avgblur）或其他软件 filter 报 `Filter not found`？**

A: `module-lite.sh` 精确控制了哪些 FFmpeg filter 被编译进 so。`avgblur`、`pad` 等与 Vulkan 无关的 CPU filter 需要在 `IJK_ENABLE_FILTERS=1` 段显式 `--enable-filter=avgblur`。当前 `module-lite.sh` 已包含 `avgblur` 和 `pad`；若使用自定义配置，请确保所用 filter 均已显式启用，然后重新编译 FFmpeg so。

**Q: FFmpeg 编译报 `spirv_compiler not found`？**

A: 这是 Android NDK 下 `libglslang` 检测失败的已知问题（NDK libc 内置 pthread/stdc++，configure 无法独立链接）。已通过默认禁用 Vulkan filters（`IJK_ENABLE_VULKAN_FILTERS=0`）解决，Vulkan 设备支持不受影响。

**Q: 链接阶段报 `ff_vk_* duplicate symbol`？**

A: FFmpeg 8 构建系统将 `libavutil/vulkan.c` 同时编译进 `libavcodec.a` 和 `libavutil.a`。已在链接命令加入 `-Wl,--allow-multiple-definition` 解决，行为安全（两份来源相同）。

## 运行时问题

**Q: 运行时崩溃 `UnsatisfiedLinkError: library "libc++_shared.so" not found`？**

A: `libijkplayer.so` 依赖 C++ 共享运行时 `libc++_shared.so`，APK 中必须包含该文件。`compile-ijk.sh` 会自动将 NDK 中的 `libc++_shared.so` 复制到 `libs/arm64-v8a/`，Gradle 打包时会自动包含。若仍报错，请先执行 `./compile-ijk.sh clean && ./compile-ijk.sh arm64` 重新构建。

**Q: `./compile-glslang.sh: 权限不够`？**

A: 执行一次 `chmod +x android/contrib/*.sh` 给所有构建脚本添加执行权限。

**Q: `libavcodec/avfft.h` 找不到？**

A: `avfft.h` 在 FFmpeg 6.0 中已删除。ijkplayer 代码已改为条件包含，FFmpeg 6+ 时自动使用内联 stub，无需手动修改。

## AI 推理

**Q: AI 推理会阻塞视频播放吗？**

A: 不会。AI 框架采用完全异步架构，推理任务在有界队列中执行，独立工作线程处理，不阻塞视频解码与渲染主线程。队列满时自动丢弃过期任务（>100ms），确保实时性。

**Q: 如何启用 LLM 功能？**

A: 需要先初始化并编译 llama.cpp，然后在 CMakeLists.txt 中设置 `IJKAI_ENABLE_LLM=ON`，重新编译 ijkplayer。详细步骤见 [AI 框架文档](ai.md)。

**Q: 支持哪些 LLM 模型？**

A: 支持 GGUF 格式的模型，推荐使用 Qwen2.5-0.5B-Instruct（轻量级，Demo 默认模型）、llama-3.2-1b/3b、phi-3-mini 等。模型可自动从 HuggingFace 下载，也可手动放到设备存储中。

**Q: 运行时崩溃 `UnsatisfiedLinkError: library "libomp.so" not found`？**

A: 这是 llama.cpp 编译时启用了 OpenMP 导致的。已在 `compile-llama.sh` 中设置 `-DGGML_OPENMP=OFF` 禁用 OpenMP。如果遇到此问题，请重新编译 llama.cpp 和 ijkplayer：
```bash
cd android/contrib
./compile-llama.sh
cd ..
./compile-ijk.sh clean
./compile-ijk.sh arm64
```

**Q: AI 模型下载失败或很慢？**

A: Demo App 已内置国内镜像降级机制：先尝试从 HuggingFace (huggingface.co) 下载，失败后自动切换到国内镜像 hf-mirror.com。如果两个源都失败，可手动下载模型文件（Qwen2.5-0.5B-Instruct GGUF 格式，约 350MB）放到设备存储的 `Android/data/<package>/files/models/` 目录。
