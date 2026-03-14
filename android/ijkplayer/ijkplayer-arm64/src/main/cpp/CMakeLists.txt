cmake_minimum_required(VERSION 3.22)

project(ijkplayer_android LANGUAGES C CXX)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(IJK_REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../..")
get_filename_component(IJK_REPO_ROOT "${IJK_REPO_ROOT}" ABSOLUTE)

set(IJK_MEDIA_ROOT "${IJK_REPO_ROOT}/ijkmedia")

set(IJK_FFMPEG_SOURCE_DIR "${IJK_REPO_ROOT}/android/contrib/ffmpeg-arm64" CACHE PATH "FFmpeg source directory (for internal headers)")
set(IJK_FFMPEG_OUTPUT_DIR "${IJK_REPO_ROOT}/android/contrib/build/ffmpeg-arm64/output" CACHE PATH "FFmpeg build output directory")
set(IJK_FFMPEG_INCLUDE_DIR "${IJK_FFMPEG_OUTPUT_DIR}/include")

if (NOT EXISTS "${IJK_FFMPEG_SOURCE_DIR}")
    message(FATAL_ERROR "FFmpeg source dir not found: ${IJK_FFMPEG_SOURCE_DIR}")
endif()
if (NOT EXISTS "${IJK_FFMPEG_INCLUDE_DIR}")
    message(FATAL_ERROR "FFmpeg include dir not found: ${IJK_FFMPEG_INCLUDE_DIR}")
endif()

find_library(log_lib log)
find_library(android_lib android)
find_library(opensles_lib OpenSLES)
find_library(egl_lib EGL)
find_library(glesv2_lib GLESv2)

set(IJK_FFMPEG_LIB "${IJK_FFMPEG_OUTPUT_DIR}/lib/libijkffmpeg.so")
if (NOT EXISTS "${IJK_FFMPEG_LIB}")
    set(IJK_FFMPEG_LIB "${CMAKE_CURRENT_LIST_DIR}/../libs/${ANDROID_ABI}/libijkffmpeg.so")
endif()
if (NOT EXISTS "${IJK_FFMPEG_LIB}")
    message(FATAL_ERROR "libijkffmpeg.so not found (checked output and module libs)")
endif()

add_library(ijkffmpeg SHARED IMPORTED)
set_target_properties(ijkffmpeg PROPERTIES IMPORTED_LOCATION "${IJK_FFMPEG_LIB}")

add_library(cpufeatures STATIC
        "${CMAKE_ANDROID_NDK}/sources/android/cpufeatures/cpu-features.c")
target_include_directories(cpufeatures PUBLIC
        "${CMAKE_ANDROID_NDK}/sources/android/cpufeatures")

add_library(ijkj4a STATIC
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/j4a_allclasses.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/j4a_base.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/android/media/AudioTrack.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/android/media/MediaCodec.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/android/media/MediaFormat.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/android/media/PlaybackParams.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/android/os/Build.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/android/os/Bundle.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/java/nio/Buffer.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/java/nio/ByteBuffer.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/java/util/ArrayList.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/tv/danmaku/ijk/media/player/misc/IMediaDataSource.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/tv/danmaku/ijk/media/player/misc/IAndroidIO.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a/class/tv/danmaku/ijk/media/player/IjkMediaPlayer.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4au/class/android/media/AudioTrack.util.c"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4au/class/java/nio/ByteBuffer.util.c")
target_include_directories(ijkj4a PUBLIC
        "${IJK_MEDIA_ROOT}/ijkj4a"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4a"
        "${IJK_MEDIA_ROOT}/ijkj4a/j4au")
target_compile_options(ijkj4a PRIVATE -Wall -Wextra)

add_library(ijksoundtouch STATIC
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/AAFilter.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/FIFOSampleBuffer.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/FIRFilter.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/cpu_detect_x86.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/sse_optimized.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/RateTransposer.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/InterpolateCubic.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/InterpolateLinear.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/InterpolateShannon.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/TDStretch.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/BPMDetect.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/PeakFinder.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/SoundTouch.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/source/SoundTouch/mmx_optimized.cpp"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/ijksoundtouch_wrap.cpp")
target_include_directories(ijksoundtouch PUBLIC
        "${IJK_MEDIA_ROOT}"
        "${IJK_MEDIA_ROOT}/ijksoundtouch"
        "${IJK_MEDIA_ROOT}/ijksoundtouch/include")

add_library(ijksdl SHARED
        "${IJK_MEDIA_ROOT}/ijksdl/ijksdl_aout.c"
        "${IJK_MEDIA_ROOT}/ijksdl/ijksdl_audio.c"
        "${IJK_MEDIA_ROOT}/ijksdl/ijksdl_egl.c"
        "${IJK_MEDIA_ROOT}/ijksdl/ijksdl_error.c"
        "${IJK_MEDIA_ROOT}/ijksdl/ijksdl_mutex.c"
        "${IJK_MEDIA_ROOT}/ijksdl/ijksdl_stdinc.c"
        "${IJK_MEDIA_ROOT}/ijksdl/ijksdl_thread.c"
        "${IJK_MEDIA_ROOT}/ijksdl/ijksdl_timer.c"
        "${IJK_MEDIA_ROOT}/ijksdl/ijksdl_vout.c"
        "${IJK_MEDIA_ROOT}/ijksdl/ijksdl_extra_log.c"
        "${IJK_MEDIA_ROOT}/ijksdl/gles2/color.c"
        "${IJK_MEDIA_ROOT}/ijksdl/gles2/common.c"
        "${IJK_MEDIA_ROOT}/ijksdl/gles2/renderer.c"
        "${IJK_MEDIA_ROOT}/ijksdl/gles2/renderer_rgb.c"
        "${IJK_MEDIA_ROOT}/ijksdl/gles2/renderer_yuv420p.c"
        "${IJK_MEDIA_ROOT}/ijksdl/gles2/renderer_yuv444p10le.c"
        "${IJK_MEDIA_ROOT}/ijksdl/gles2/shader.c"
        "${IJK_MEDIA_ROOT}/ijksdl/gles2/fsh/rgb.fsh.c"
        "${IJK_MEDIA_ROOT}/ijksdl/gles2/fsh/yuv420p.fsh.c"
        "${IJK_MEDIA_ROOT}/ijksdl/gles2/fsh/yuv444p10le.fsh.c"
        "${IJK_MEDIA_ROOT}/ijksdl/gles2/vsh/mvp.vsh.c"
        "${IJK_MEDIA_ROOT}/ijksdl/dummy/ijksdl_vout_dummy.c"
        "${IJK_MEDIA_ROOT}/ijksdl/ffmpeg/ijksdl_vout_overlay_ffmpeg.c"
        "${IJK_MEDIA_ROOT}/ijksdl/ffmpeg/abi_all/image_convert_stub.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/android_audiotrack.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/android_nativewindow.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_android_jni.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_aout_android_audiotrack.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_aout_android_opensles.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_codec_android_mediacodec_dummy.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_codec_android_mediacodec_internal.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_codec_android_mediacodec_java.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_codec_android_mediacodec.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_codec_android_mediadef.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_codec_android_mediaformat_java.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_codec_android_mediaformat.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_vout_android_nativewindow.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_vout_android_surface.c"
        "${IJK_MEDIA_ROOT}/ijksdl/android/ijksdl_vout_overlay_android_mediacodec.c")
target_include_directories(ijksdl PRIVATE
        "${IJK_MEDIA_ROOT}"
        "${IJK_MEDIA_ROOT}/ijksdl"
        "${IJK_MEDIA_ROOT}/ijkj4a"
        "${IJK_FFMPEG_INCLUDE_DIR}"
        "${IJK_FFMPEG_SOURCE_DIR}")
target_compile_options(ijksdl PRIVATE -Wall -Wextra)
target_link_libraries(ijksdl PRIVATE
        ijkffmpeg
        ijkj4a
        cpufeatures
        ${log_lib}
        ${android_lib}
        ${opensles_lib}
        ${egl_lib}
        ${glesv2_lib})

add_library(ijkplayer SHARED
        "${IJK_MEDIA_ROOT}/ijkplayer/ff_cmdutils.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ff_ffplay.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ff_ffpipeline.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ff_ffpipenode.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ijkmeta.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ijkplayer.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/pipeline/ffpipeline_ffplay.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/pipeline/ffpipenode_ffplay_vdec.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/android/ffmpeg_api_jni.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/android/ijkplayer_android.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/android/ijkplayer_jni.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/android/pipeline/ffpipeline_android.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/android/pipeline/ffpipenode_android_mediacodec_vdec.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ijkavformat/allformats.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ijkavformat/cJSON.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ijkavformat/ijkiomanager_stub.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ijkavutil/ijkdict.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ijkavutil/ijkutils.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ijkavutil/ijkthreadpool.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ijkavutil/ijktree.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ijkavutil/ijkfifo.c"
        "${IJK_MEDIA_ROOT}/ijkplayer/ijkavutil/ijkstl.cpp")
target_include_directories(ijkplayer PRIVATE
        "${IJK_MEDIA_ROOT}"
        "${IJK_MEDIA_ROOT}/ijkplayer"
        "${IJK_MEDIA_ROOT}/ijkj4a"
        "${IJK_MEDIA_ROOT}/compat"
        "${IJK_FFMPEG_INCLUDE_DIR}"
        "${IJK_FFMPEG_SOURCE_DIR}")
target_compile_options(ijkplayer PRIVATE -Wall -Wextra)
target_link_libraries(ijkplayer PRIVATE
        ijkffmpeg
        ijksdl
        ijksoundtouch
        ${log_lib}
        ${android_lib})

