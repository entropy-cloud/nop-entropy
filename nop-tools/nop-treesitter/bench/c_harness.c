#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "tree_sitter/api.h"

typedef struct {
    const char *name;
    const TSLanguage *(*language)(void);
    const char *path;
    int iterations;
} Case;

static char *read_file(const char *path, size_t *len) {
    FILE *f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "cannot open %s\n", path); exit(1); }
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    char *buf = malloc((size_t)n + 1);
    if (fread(buf, 1, (size_t)n, f) != (size_t)n) { fprintf(stderr, "short read\n"); exit(1); }
    buf[n] = 0;
    fclose(f);
    *len = (size_t)n;
    return buf;
}

extern const TSLanguage *tree_sitter_json(void);
extern const TSLanguage *tree_sitter_java(void);

int main(void) {
    Case cases[] = {
        {"json-10k", &tree_sitter_json, "_tmp/ts-bench/inputs/json-10k.json", 2000},
        {"json-100k", &tree_sitter_json, "_tmp/ts-bench/inputs/json-100k.json", 200},
        {"json-1m", &tree_sitter_json, "_tmp/ts-bench/inputs/json-1m.json", 20},
        {"java-single", &tree_sitter_java, "_tmp/ts-bench/inputs/java-single.java", 2000},
    };
    for (unsigned c = 0; c < sizeof(cases) / sizeof(cases[0]); c++) {
        Case *cs = &cases[c];
        size_t len = 0;
        char *source = read_file(cs->path, &len);
        TSParser *parser = ts_parser_new();
        ts_parser_set_language(parser, cs->language());
        /* warmup */
        for (int i = 0; i < 5; i++) {
            TSTree *tree = ts_parser_parse_string(parser, NULL, source, (uint32_t)len);
            ts_tree_delete(tree);
        }
        struct timespec t0, t1;
        clock_gettime(CLOCK_MONOTONIC, &t0);
        for (int i = 0; i < cs->iterations; i++) {
            TSTree *tree = ts_parser_parse_string(parser, NULL, source, (uint32_t)len);
            char *sexp = ts_node_string(ts_tree_root_node(tree));
            free(sexp);
            ts_tree_delete(tree);
        }
        clock_gettime(CLOCK_MONOTONIC, &t1);
        double seconds = (double)(t1.tv_sec - t0.tv_sec) + (double)(t1.tv_nsec - t0.tv_nsec) / 1e9;
        printf("%s %.1f ops/s (%d iters, %.3fs)\n", cs->name, cs->iterations / seconds,
               cs->iterations, seconds);
        ts_parser_delete(parser);
        free(source);
    }
    return 0;
}
