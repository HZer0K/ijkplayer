/*
 * ffpipeline_ai.c
 *
 * Copyright (c) 2026 IJKPLAYER
 *
 * This file is part of ijkPlayer.
 *
 * ijkPlayer is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * ijkPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with ijkPlayer; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#include "ffpipeline_ai.h"
#include "ffpipenode_ai_video.h"
#include "../ff_ffplay.h"
#include <stdlib.h>

static SDL_Class g_pipeline_class = {
    .name = "ffpipeline_ai",
};

struct IJKFF_Pipeline_Opaque {
    FFPlayer   *ffp;
    ijkai_type  ai_type;
    char       *model_path;
    int         n_threads;
};

static void func_destroy(IJKFF_Pipeline *pipeline)
{
    if (!pipeline || !pipeline->opaque)
        return;
    IJKFF_Pipeline_Opaque *opaque = pipeline->opaque;
    if (opaque->model_path) {
        free(opaque->model_path);
        opaque->model_path = NULL;
    }
}

static IJKFF_Pipenode *func_open_video_decoder(IJKFF_Pipeline *pipeline, FFPlayer *ffp)
{
    IJKFF_Pipeline_Opaque *opaque = pipeline->opaque;
    return ffpipenode_create_ai_video_processor(ffp,
                                                 opaque->ai_type,
                                                 opaque->model_path,
                                                 opaque->n_threads);
}

static SDL_Aout *func_open_audio_output(IJKFF_Pipeline *pipeline, FFPlayer *ffp)
{
    return NULL;
}

IJKFF_Pipeline *ffpipeline_create_from_ai(struct FFPlayer *ffp,
                                           ijkai_type ai_type,
                                           const char *model_path,
                                           int n_threads)
{
    if (!model_path)
        return NULL;

    IJKFF_Pipeline *pipeline = ffpipeline_alloc(&g_pipeline_class,
                                                 sizeof(IJKFF_Pipeline_Opaque));
    if (!pipeline)
        return pipeline;

    IJKFF_Pipeline_Opaque *opaque = pipeline->opaque;
    opaque->ffp        = ffp;
    opaque->ai_type    = ai_type;
    opaque->model_path = strdup(model_path);
    opaque->n_threads  = n_threads;

    if (!opaque->model_path) {
        ffpipeline_free(pipeline);
        return NULL;
    }

    pipeline->func_destroy            = func_destroy;
    pipeline->func_open_video_decoder = func_open_video_decoder;
    pipeline->func_open_audio_output  = func_open_audio_output;

    return pipeline;
}
