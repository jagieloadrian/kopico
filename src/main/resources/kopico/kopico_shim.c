// Warstwa zgodności dla runtime Kotlin/Native na bare-metal RP2040.
// Środowisko: jeden wątek, brak OS, brak MMU. Runtime skompilowany z
// -Xbinary=gc=noop -Xallocator=std, więc nie tworzy wątków (pthread_create
// nie występuje) — wszystkie prymitywy wątkowe degradują do no-op/statyków.
#include <errno.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "pico/stdlib.h"

// ---------- mmap: statyczna arena (RP2040 nie ma MMU) ----------
// ponytail: bump-alokator bez zwalniania; munmap = no-op. Wystarcza dla
// gc=noop; przy przejściu na prawdziwy GC potrzebny freelist.
#define KOPICO_ARENA_SIZE (64 * 1024)
static unsigned char kopico_arena[KOPICO_ARENA_SIZE] __attribute__((aligned(8)));
static size_t kopico_arena_used = 0;

void *mmap(void *addr, size_t length, int prot, int flags, int fd, long offset) {
    (void)addr; (void)prot; (void)flags; (void)fd; (void)offset;
    size_t aligned = (length + 7u) & ~7u;
    if (kopico_arena_used + aligned > KOPICO_ARENA_SIZE) {
        errno = ENOMEM;
        return (void *)-1;
    }
    void *p = &kopico_arena[kopico_arena_used];
    kopico_arena_used += aligned;
    return p;
}

int munmap(void *addr, size_t length) { (void)addr; (void)length; return 0; }

// ---------- pthread: single-thread no-op ----------
typedef unsigned kopico_pt_handle;

int pthread_mutex_init(void *m, const void *a) { (void)m; (void)a; return 0; }
int pthread_mutex_destroy(void *m) { (void)m; return 0; }
int pthread_mutex_lock(void *m) { (void)m; return 0; }
int pthread_mutex_trylock(void *m) { (void)m; return 0; }
int pthread_mutex_unlock(void *m) { (void)m; return 0; }

int pthread_cond_init(void *c, const void *a) { (void)c; (void)a; return 0; }
int pthread_cond_destroy(void *c) { (void)c; return 0; }
int pthread_cond_broadcast(void *c) { (void)c; return 0; }
int pthread_cond_signal(void *c) { (void)c; return 0; }
int pthread_cond_wait(void *c, void *m) { (void)c; (void)m; return 0; }
int pthread_cond_timedwait(void *c, void *m, const void *t) {
    (void)c; (void)m; (void)t;
    return ETIMEDOUT;
}

int pthread_rwlock_rdlock(void *l) { (void)l; return 0; }
int pthread_rwlock_wrlock(void *l) { (void)l; return 0; }
int pthread_rwlock_unlock(void *l) { (void)l; return 0; }

// TLS-klucze: mały statyczny słownik — jeden wątek, więc zwykła tablica.
#define KOPICO_MAX_KEYS 16
static const void *kopico_key_values[KOPICO_MAX_KEYS];
static unsigned kopico_next_key = 1;

int pthread_key_create(unsigned *key, void (*destructor)(void *)) {
    (void)destructor;
    if (kopico_next_key >= KOPICO_MAX_KEYS) return EAGAIN;
    *key = kopico_next_key++;
    return 0;
}

int __pthread_key_create(unsigned *key, void (*destructor)(void *)) {
    return pthread_key_create(key, destructor);
}

int pthread_setspecific(unsigned key, const void *value) {
    if (key >= KOPICO_MAX_KEYS) return EINVAL;
    kopico_key_values[key] = value;
    return 0;
}

void *pthread_getspecific(unsigned key) {
    return key < KOPICO_MAX_KEYS ? (void *)kopico_key_values[key] : NULL;
}

int pthread_once(int *once, void (*init)(void)) {
    if (*once == 0) {
        *once = 1;
        init();
    }
    return 0;
}

kopico_pt_handle pthread_self(void) { return 1; }
int pthread_detach(kopico_pt_handle t) { (void)t; return 0; }
int pthread_setname_np(kopico_pt_handle t, const char *n) { (void)t; (void)n; return 0; }

int sched_yield(void) { return 0; }

// ---------- misc POSIX ----------
unsigned sleep(unsigned seconds) {
    sleep_ms(seconds * 1000u);
    return 0;
}

int dladdr(const void *addr, void *info) { (void)addr; (void)info; return 0; }

long syscall(long number, ...) {
    (void)number;
    errno = ENOSYS;
    return -1;
}

// ---------- TLS (general-dynamic) ----------
// ponytail: jeden moduł, jeden wątek → jeden statyczny blok TLS.
// Poprawne per-moduł TLS wymagałoby -femulated-tls; upgrade gdy runtime
// zacznie realnie używać wielu modułów TLS.
typedef struct {
    unsigned long ti_module;
    unsigned long ti_offset;
} kopico_tls_index;

#define KOPICO_TLS_SIZE (4 * 1024)
static unsigned char kopico_tls_block[KOPICO_TLS_SIZE] __attribute__((aligned(8)));

void *__tls_get_addr(kopico_tls_index *ti) {
    return &kopico_tls_block[ti->ti_offset % KOPICO_TLS_SIZE];
}

// local-exec TLS: __aeabi_read_tp zwraca thread pointer. Specjalne ABI —
// wolno zmienić tylko r0, stąd naked asm zamiast zwykłej funkcji C.
unsigned char kopico_tls_area[KOPICO_TLS_SIZE] __attribute__((aligned(8)));

__attribute__((naked)) void *__aeabi_read_tp(void) {
    __asm__ volatile(
        "ldr r0, =kopico_tls_area\n"
        "bx lr\n");
}

// ---------- std::condition_variable ----------
// arm-none-eabi libstdc++ jest zbudowane w modelu single-thread i nie ma tych
// symboli; runtime K/N referuje je z nieużywanych ścieżek wielowątkowych.
// Definiujemy no-opy pod zmanglowanymi nazwami (poprawne identyfikatory C).
void _ZNSt18condition_variableC1Ev(void *self) { (void)self; }
void _ZNSt18condition_variableD1Ev(void *self) { (void)self; }
void _ZNSt18condition_variable10notify_allEv(void *self) { (void)self; }
void _ZNSt18condition_variable10notify_oneEv(void *self) { (void)self; }
void _ZNSt18condition_variable4waitERSt11unique_lockISt5mutexE(void *self, void *lock) {
    (void)self; (void)lock;
}

// ---------- stdout/stderr jako symbole ----------
// W newlib to makra na pola _reent — runtime K/N oczekuje globalnych symboli.
// Globalne zmienne definiuje kopico_stdio_globals.c (osobny TU bez stdio.h);
// tu, mając stdio.h w zasięgu, oddajemy realne strumienie newlib.
#include <stdio.h>
void kopico_get_real_streams(void **out, void **err) {
    *out = (void *)stdout;
    *err = (void *)stderr;
}
