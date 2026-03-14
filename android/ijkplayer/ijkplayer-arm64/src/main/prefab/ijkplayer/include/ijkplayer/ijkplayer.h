#pragma once

#ifdef __cplusplus
extern "C" {
#endif

typedef struct IjkMediaPlayer IjkMediaPlayer;

const char *ijkmp_version(void);
IjkMediaPlayer *ijkmp_create(int (*msg_loop)(void *));
void ijkmp_dec_ref_p(IjkMediaPlayer **pmp);

#ifdef __cplusplus
}
#endif

