// Definiuje globalne symbole stdout/stderr, których oczekuje runtime K/N.
// Celowo BEZ #include <stdio.h> — tam stdout/stderr to makra i nazwy by się
// zderzyły. Realne strumienie newlib podstawia konstruktor niżej.
typedef void KOPICO_FILE;

KOPICO_FILE *stdout;
KOPICO_FILE *stderr;

extern void kopico_get_real_streams(KOPICO_FILE **out, KOPICO_FILE **err);

__attribute__((constructor)) static void kopico_init_std_streams(void) {
    kopico_get_real_streams(&stdout, &stderr);
}
