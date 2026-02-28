#include "ijkiomanager.h"
#include <stdlib.h>

int ijkio_manager_create(IjkIOManagerContext **ph, void *opaque) {
    if (!ph) return -1;
    IjkIOManagerContext *h = (IjkIOManagerContext *)calloc(1, sizeof(IjkIOManagerContext));
    if (!h) return -1;
    h->opaque = opaque;
    *ph = h;
    return 0;
}

void ijkio_manager_destroy(IjkIOManagerContext *h) {
    if (h) free(h);
}

void ijkio_manager_destroyp(IjkIOManagerContext **ph) {
    if (ph && *ph) {
        free(*ph);
        *ph = NULL;
    }
}

int ijkio_manager_set_callback(IjkIOManagerContext *h, void *callback) {
    (void)h;
    (void)callback;
    return 0;
}

void ijkio_manager_will_share_cache_map(IjkIOManagerContext *h) {
    (void)h;
}

void ijkio_manager_did_share_cache_map(IjkIOManagerContext *h) {
    (void)h;
}

void ijkio_manager_immediate_reconnect(IjkIOManagerContext *h) {
    (void)h;
}
