/*
 * Copyright (c) 2026 IJKPLAYER
 *
 * IjkAIEngine - AI推理引擎Java API
 *
 * 提供LLM/CV推理的Java接口，底层通过JNI调用ijkai C库。
 * 所有推理操作都是异步的，不阻塞主线程。
 */

package tv.danmaku.ijk.media.player;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * AI推理引擎 - 集成llama.cpp (LLM) 和 MNN (CV)
 *
 * 使用示例:
 * <pre>
 * IjkAIEngine engine = new IjkAIEngine();
 * engine.init("/sdcard/models/llama-3.2-1b-q4.gguf", 4);
 *
 * engine.promptAsync("你好", new IjkAIEngine.Callback() {
 *     public void onText(String text, boolean isComplete) { ... }
 *     public void onError(String error) { ... }
 * });
 *
 * engine.release();
 * </pre>
 */
public class IjkAIEngine {

    // ============ AI类型枚举 ============

    /** LLM推理 */
    public static final int TYPE_LLM = 0;
    /** CV超分辨率 */
    public static final int TYPE_CV_SR = 1;
    /** CV目标检测 */
    public static final int TYPE_CV_DETECT = 2;
    /** 多模态 */
    public static final int TYPE_MULTIMODAL = 3;

    // ============ 回调接口 ============

    /**
     * LLM推理回调
     */
    public interface Callback {
        /**
         * 推理输出文本(流式)
         * @param text 生成的文本片段
         * @param isComplete 是否推理完成
         */
        void onText(@NonNull String text, boolean isComplete);

        /**
         * 推理错误
         * @param error 错误信息
         */
        void onError(@NonNull String error);
    }

    // ============ 内部状态 ============

    /** Native指针 */
    private long mNativePtr = 0;
    /** 是否已初始化 */
    private boolean mInitialized = false;
    /** 主线程Handler(用于回调) */
    private Handler mMainHandler;

    // ============ 构造函数 ============

    public IjkAIEngine() {
        try {
            loadLibrary();
        } catch (UnsatisfiedLinkError e) {
            // LLM库可选的，加载失败不影响主播放器
            android.util.Log.w("IjkAIEngine", "Failed to load native library: " + e.getMessage());
        }
    }

    // ============ 加载Native库 ============

    private static boolean sLibraryLoaded = false;

    private static synchronized void loadLibrary() {
        if (sLibraryLoaded) return;
        try {
            System.loadLibrary("ijkplayer");
            sLibraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.w("IjkAIEngine", "libijkplayer not available: " + e.getMessage());
        }
    }

    // ============ 初始化/释放 ============

    /**
     * 初始化LLM引擎
     *
     * @param modelPath 模型文件路径(GGUF格式)
     * @param nThreads  推理线程数(推荐2-4)
     * @return true初始化成功
     */
    public boolean init(@NonNull String modelPath, int nThreads) {
        return init(TYPE_LLM, modelPath, nThreads);
    }

    /**
     * 初始化AI引擎
     *
     * @param type      AI类型
     * @param modelPath 模型文件路径
     * @param nThreads  推理线程数
     * @return true初始化成功
     */
    public boolean init(int type, @NonNull String modelPath, int nThreads) {
        if (mInitialized) {
            release();
        }

        mMainHandler = new Handler(Looper.getMainLooper());
        mNativePtr = nativeInit(type, modelPath, nThreads);
        mInitialized = (mNativePtr != 0);

        if (mInitialized) {
            android.util.Log.i("IjkAIEngine",
                "AI engine initialized: type=" + type + ", threads=" + nThreads);
        } else {
            android.util.Log.e("IjkAIEngine", "Failed to initialize AI engine");
        }

        return mInitialized;
    }

    /**
     * 释放AI引擎资源
     */
    public void release() {
        if (mNativePtr != 0) {
            nativeRelease(mNativePtr);
            mNativePtr = 0;
        }
        mInitialized = false;
        android.util.Log.i("IjkAIEngine", "AI engine released");
    }

    // ============ LLM推理 ============

    /**
     * LLM推理(异步)
     *
     * @param prompt   提示词
     * @param callback 回调(在主线程调用)
     * @return 0成功, -1失败
     */
    public int prompt(@NonNull String prompt, @Nullable Callback callback) {
        return prompt(prompt, callback, 256);
    }

    /**
     * LLM推理(异步)
     *
     * @param prompt    提示词
     * @param callback  回调(在主线程调用)
     * @param maxTokens 最大生成token数
     * @return 0成功, -1失败
     */
    public int prompt(@NonNull String prompt, @Nullable Callback callback, int maxTokens) {
        if (!mInitialized || mNativePtr == 0 || prompt.isEmpty()) {
            if (callback != null) {
                postError(callback, "Engine not initialized or empty prompt");
            }
            return -1;
        }

        // 包装回调到主线程
        CallbackWrapper wrapper = new CallbackWrapper(callback, mMainHandler);
        return nativeLLMPrompt(mNativePtr, prompt, wrapper, maxTokens);
    }

    /**
     * 简写方法 - 异步推理(在主线程回调)
     */
    public void promptAsync(@NonNull String prompt, @Nullable Callback callback) {
        prompt(prompt, callback);
    }

    // ============ 统计信息 ============

    /**
     * 获取生成的token数量
     */
    public int getTokenCount() {
        if (mNativePtr == 0) return 0;
        return nativeGetTokenCount(mNativePtr);
    }

    /**
     * 获取已处理帧数
     */
    public int getProcessedFrames() {
        if (mNativePtr == 0) return 0;
        return nativeGetProcessedFrames(mNativePtr);
    }

    /**
     * 是否已初始化
     */
    public boolean isInitialized() {
        return mInitialized;
    }

    // ============ 回调包装 ============

    /**
     * 回调包装器(确保在主线程调用)
     */
    private static class CallbackWrapper implements Callback {
        private final Callback mCallback;
        private final Handler mHandler;

        CallbackWrapper(Callback callback, Handler handler) {
            this.mCallback = callback;
            this.mHandler = handler;
        }

        @Override
        public void onText(final String text, final boolean isComplete) {
            if (mCallback == null) return;
            mHandler.post(() -> mCallback.onText(text, isComplete));
        }

        @Override
        public void onError(final String error) {
            if (mCallback == null) return;
            mHandler.post(() -> mCallback.onError(error));
        }
    }

    private void postError(Callback callback, String error) {
        mMainHandler.post(() -> callback.onError(error));
    }

    // ============ Native方法 ============

    private static native long nativeInit(int type, String modelPath, int nThreads);
    private static native void nativeRelease(long nativePtr);
    private static native int nativeLLMPrompt(long nativePtr, String prompt,
        Object callback, int maxTokens);
    private static native int nativeGetTokenCount(long nativePtr);
    private static native int nativeGetProcessedFrames(long nativePtr);

    // ============ finalize ============

    @Override
    protected void finalize() throws Throwable {
        try {
            release();
        } finally {
            super.finalize();
        }
    }
}
