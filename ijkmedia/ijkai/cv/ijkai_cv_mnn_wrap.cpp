/*
 * ijkai_cv_mnn_wrap.cpp
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * C wrapper implementation for MNN C++ API.
 * Provides inference interface for CV modules.
 */

#include "ijkai_cv_mnn_wrap.h"

#include <cstdlib>
#include <cstring>
#include <cstdio>

// MNN headers (included via C++ interface)
#include <MNN/Interpreter.hpp>
#include <MNN/Matrix.h>
#include <MNN/Tensor.hpp>

/**
 * MNN inference context (internal)
 */
struct mnn_context {
    MNN::Interpreter *interpreter;
    MNN::Session     *session;
    MNN::Tensor      *input_tensor;
    MNN::Tensor      *output_tensor;
    int               backend;
    int               n_threads;
    int               input_n;
    int               input_c;
    int               input_w;
    int               input_h;
    bool              is_multi_output;
    int               output_count;
};

static MNNForwardType backend_to_mnn_type(int backend) {
    switch (backend) {
        case MNN_BACKEND_OPENCL:
            return MNN_FORWARD_OPENCL;
        case MNN_BACKEND_VULKAN:
            return MNN_FORWARD_VULKAN;
        case MNN_BACKEND_AUTO:
            return MNN_FORWARD_AUTO;
        case MNN_BACKEND_CPU:
        default:
            return MNN_FORWARD_CPU;
    }
}

mnn_context *mnn_init(const char *model_path, int backend, int n_threads) {
    if (!model_path) {
        fprintf(stderr, "[MNN] Error: model_path is NULL\n");
        return NULL;
    }

    mnn_context *ctx = (mnn_context *)calloc(1, sizeof(mnn_context));
    if (!ctx) {
        fprintf(stderr, "[MNN] Error: failed to allocate context\n");
        return NULL;
    }

    ctx->backend = backend;
    ctx->n_threads = (n_threads > 0) ? n_threads : 4;

    // Load MNN model
    ctx->interpreter = MNN::Interpreter::createFromFile(model_path);
    if (!ctx->interpreter) {
        fprintf(stderr, "[MNN] Error: failed to create interpreter from: %s\n", model_path);
        free(ctx);
        return NULL;
    }

    // Create session with backend config
    MNN::ScheduleConfig config;
    config.type = backend_to_mnn_type(backend);
    config.numThread = ctx->n_threads;

    MNN::BackendConfig backend_config;
    backend_config.precision = MNN::BackendConfig::Precision_Normal;
    backend_config.power = MNN::BackendConfig::Power_Normal;
    backend_config.memory = MNN::BackendConfig::Memory_Normal;
    config.backendConfig = &backend_config;

    ctx->session = ctx->interpreter->createSession(config);
    if (!ctx->session) {
        fprintf(stderr, "[MNN] Error: failed to create session\n");
        delete ctx->interpreter;
        free(ctx);
        return NULL;
    }

    // Get input tensor
    ctx->input_tensor = ctx->interpreter->getSessionInput(ctx->session, NULL);
    if (!ctx->input_tensor) {
        fprintf(stderr, "[MNN] Error: failed to get input tensor\n");
        delete ctx->interpreter;
        free(ctx);
        return NULL;
    }

    // Store input dimensions
    auto dims = ctx->input_tensor->shape();
    ctx->input_n = (dims.size() > 0) ? (int)dims[0] : 1;
    ctx->input_c = (dims.size() > 1) ? (int)dims[1] : 3;
    ctx->input_h = (dims.size() > 2) ? (int)dims[2] : 0;
    ctx->input_w = (dims.size() > 3) ? (int)dims[3] : 0;
    // Handle NHWC layout (batch, height, width, channels)
    if (dims.size() == 4 && ctx->input_tensor->getDimensionType() == MNN::Tensor::TENSORFLOW) {
        ctx->input_n = (int)dims[0];
        ctx->input_h = (int)dims[1];
        ctx->input_w = (int)dims[2];
        ctx->input_c = (int)dims[3];
    }

    // Get output tensor
    ctx->output_tensor = ctx->interpreter->getSessionOutput(ctx->session, NULL);
    if (!ctx->output_tensor) {
        fprintf(stderr, "[MNN] Warning: no default output tensor\n");
    }

    printf("[MNN] Initialized: backend=%d, threads=%d, input=%dx%dx%dx%d\n",
           backend, ctx->n_threads,
           ctx->input_n, ctx->input_c, ctx->input_w, ctx->input_h);

    return ctx;
}

int mnn_run(mnn_context *ctx,
            const float *input,
            int in_n, int in_c, int in_w, int in_h,
            float *output, int out_size) {
    if (!ctx || !ctx->interpreter || !ctx->session) {
        return -1;
    }

    // Copy input data into input tensor
    MNN::Tensor *input_tensor = ctx->input_tensor;
    if (input_tensor->getDimensionType() == MNN::Tensor::TENSORFLOW) {
        // NHWC layout: use host tensor for cross-layout copy
        auto host_tensor = new MNN::Tensor(input_tensor, MNN::Tensor::TENSORFLOW);
        float *host_data = host_tensor->host<float>();
        if (host_data) {
            ::memcpy(host_data, input, in_n * in_c * in_w * in_h * sizeof(float));
        }
        input_tensor->copyFromHostTensor(host_tensor);
        delete host_tensor;
    } else if (input_tensor->getDimensionType() == MNN::Tensor::CAFFE) {
        // NCHW layout: use host tensor for cross-layout copy
        auto host_tensor = new MNN::Tensor(input_tensor, MNN::Tensor::CAFFE);
        float *host_data = host_tensor->host<float>();
        if (host_data) {
            ::memcpy(host_data, input, in_n * in_c * in_w * in_h * sizeof(float));
        }
        input_tensor->copyFromHostTensor(host_tensor);
        delete host_tensor;
    } else {
        // Directly resize and copy
        input_tensor->resize({in_n, in_c, in_h, in_w});
        auto host_tensor = new MNN::Tensor(input_tensor, MNN::Tensor::CAFFE);
        float *host_data = host_tensor->host<float>();
        if (host_data) {
            ::memcpy(host_data, input, in_n * in_c * in_w * in_h * sizeof(float));
        }
        input_tensor->copyFromHostTensor(host_tensor);
        delete host_tensor;
    }

    // Run inference
    ctx->interpreter->runSession(ctx->session);

    // Get output
    MNN::Tensor *output_tensor = ctx->interpreter->getSessionOutput(ctx->session, NULL);
    if (!output_tensor) {
        return -1;
    }

    // Copy output data
    auto host_output = new MNN::Tensor(output_tensor, output_tensor->getDimensionType());
    output_tensor->copyToHostTensor(host_output);

    size_t copy_size = (size_t)out_size;
    size_t avail_size = host_output->elementSize() * sizeof(float);
    if (copy_size > avail_size) {
        copy_size = avail_size;
    }

    float *host_out_data = host_output->host<float>();
    if (host_out_data && output) {
        ::memcpy(output, host_out_data, copy_size);
    }

    delete host_output;
    return 0;
}

int mnn_run_multi_output(mnn_context *ctx,
                         const float *input,
                         int in_n, int in_c, int in_w, int in_h,
                         float **outputs, int *out_sizes, int out_count) {
    if (!ctx || !ctx->interpreter || !ctx->session || !outputs || !out_sizes) {
        return -1;
    }

    // Copy input data
    MNN::Tensor *input_tensor = ctx->input_tensor;
    auto host_input = new MNN::Tensor(input_tensor, input_tensor->getDimensionType());
    float *host_in_data = host_input->host<float>();
    if (host_in_data) {
        ::memcpy(host_in_data, input, in_n * in_c * in_w * in_h * sizeof(float));
    }
    input_tensor->copyFromHostTensor(host_input);
    delete host_input;

    // Run inference
    ctx->interpreter->runSession(ctx->session);

    // Get all outputs (up to out_count)
    auto output_names = ctx->interpreter->getSessionOutputAll(ctx->session);
    int count = 0;
    for (auto &kv : output_names) {
        if (count >= out_count) break;

        MNN::Tensor *tensor = kv.second;
        auto host_output = new MNN::Tensor(tensor, tensor->getDimensionType());
        tensor->copyToHostTensor(host_output);

        size_t copy_size = (size_t)out_sizes[count];
        size_t avail_size = host_output->elementSize() * sizeof(float);
        if (copy_size > avail_size) {
            copy_size = avail_size;
        }

        float *host_data = host_output->host<float>();
        if (host_data && outputs[count]) {
            ::memcpy(outputs[count], host_data, copy_size);
        }

        delete host_output;
        count++;
    }

    return 0;
}

int mnn_get_input_dims(mnn_context *ctx, int *n, int *c, int *w, int *h) {
    if (!ctx) return -1;
    if (n) *n = ctx->input_n;
    if (c) *c = ctx->input_c;
    if (w) *w = ctx->input_w;
    if (h) *h = ctx->input_h;
    return 0;
}

int mnn_get_output_dims(mnn_context *ctx, int *n, int *c, int *w, int *h) {
    if (!ctx || !ctx->output_tensor) return -1;

    auto dims = ctx->output_tensor->shape();
    int on = (dims.size() > 0) ? (int)dims[0] : 1;
    int oc = (dims.size() > 1) ? (int)dims[1] : 3;
    int oh = (dims.size() > 2) ? (int)dims[2] : 0;
    int ow = (dims.size() > 3) ? (int)dims[3] : 0;

    if (dims.size() == 4 && ctx->output_tensor->getDimensionType() == MNN::Tensor::TENSORFLOW) {
        on = (int)dims[0];
        oh = (int)dims[1];
        ow = (int)dims[2];
        oc = (int)dims[3];
    }

    if (n) *n = on;
    if (c) *c = oc;
    if (w) *w = ow;
    if (h) *h = oh;
    return 0;
}

int mnn_get_output_dims_by_index(mnn_context *ctx, int index,
                                 int *n, int *c, int *w, int *h) {
    if (!ctx || !ctx->interpreter || !ctx->session) return -1;

    auto outputs = ctx->interpreter->getSessionOutputAll(ctx->session);
    int i = 0;
    for (auto &kv : outputs) {
        if (i == index) {
            MNN::Tensor *tensor = kv.second;
            auto dims = tensor->shape();
            int on = (dims.size() > 0) ? (int)dims[0] : 1;
            int oc = (dims.size() > 1) ? (int)dims[1] : 3;
            int oh = (dims.size() > 2) ? (int)dims[2] : 0;
            int ow = (dims.size() > 3) ? (int)dims[3] : 0;

            if (dims.size() == 4 && tensor->getDimensionType() == MNN::Tensor::TENSORFLOW) {
                on = (int)dims[0];
                oh = (int)dims[1];
                ow = (int)dims[2];
                oc = (int)dims[3];
            }

            if (n) *n = on;
            if (c) *c = oc;
            if (w) *w = ow;
            if (h) *h = oh;
            return 0;
        }
        i++;
    }

    return -1; // index not found
}

void mnn_release(mnn_context *ctx) {
    if (!ctx) return;

    if (ctx->session) {
        if (ctx->interpreter) {
            ctx->interpreter->releaseSession(ctx->session);
        }
        ctx->session = NULL;
    }

    if (ctx->interpreter) {
        delete ctx->interpreter;
        ctx->interpreter = NULL;
    }

    free(ctx);
    printf("[MNN] Context released\n");
}
