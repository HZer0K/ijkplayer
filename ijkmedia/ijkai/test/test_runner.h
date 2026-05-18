/*
 * test_runner.h
 *
 * Lightweight C test framework.
 * Usage:
 *   #include "test_runner.h"
 *   TEST(GroupName, test_name) {
 *       ASSERT_EQ(1, 1);
 *       ASSERT_TRUE(1 == 1);
 *   }
 *   int main() { return RUN_ALL_TESTS(); }
 */

#ifndef TEST_RUNNER_H
#define TEST_RUNNER_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <math.h>

/* Test state */
static int g_test_passed = 0;
static int g_test_failed = 0;
static int g_assert_failed = 0;
static const char *g_current_test = "";


#define TEST(suite, name) \
    static void test_##suite##_##name(void); \
    static void __register_##suite##_##name(void) __attribute__((constructor)); \
    static void __register_##suite##_##name(void) { \
        static struct test_entry __e = { \
            #suite "." #name, test_##suite##_##name \
        }; \
        __test_register(&__e); \
    } \
    static void test_##suite##_##name(void)

typedef struct test_entry {
    const char *name;
    void (*func)(void);
} test_entry;

#define MAX_TESTS 256
static test_entry g_test_table[MAX_TESTS];
static int g_test_count = 0;

static void __test_register(test_entry *e) {
    if (g_test_count < MAX_TESTS) {
        g_test_table[g_test_count++] = *e;
    }
}

#define ASSERT_TRUE(cond) do { \
    if (!(cond)) { \
        fprintf(stderr, "  FAILED [%s:%d] %s: ASSERT_TRUE(%s)\n", \
                __FILE__, __LINE__, g_current_test, #cond); \
        g_assert_failed = 1; \
        return; \
    } \
} while(0)

#define ASSERT_FALSE(cond) ASSERT_TRUE(!(cond))

#define ASSERT_EQ(a, b) do { \
    __typeof__(a) _a = (a); __typeof__(b) _b = (b); \
    if (_a != _b) { \
        fprintf(stderr, "  FAILED [%s:%d] %s: ASSERT_EQ(%s, %s) (%lld != %lld)\n", \
                __FILE__, __LINE__, g_current_test, #a, #b, \
                (long long)_a, (long long)_b); \
        g_assert_failed = 1; \
        return; \
    } \
} while(0)

#define ASSERT_NE(a, b) do { \
    __typeof__(a) _a = (a); __typeof__(b) _b = (b); \
    if (_a == _b) { \
        fprintf(stderr, "  FAILED [%s:%d] %s: ASSERT_NE(%s, %s) (both %lld)\n", \
                __FILE__, __LINE__, g_current_test, #a, #b, \
                (long long)_a); \
        g_assert_failed = 1; \
        return; \
    } \
} while(0)

#define ASSERT_NULL(ptr) ASSERT_TRUE((ptr) == NULL)
#define ASSERT_NOT_NULL(ptr) ASSERT_TRUE((ptr) != NULL)

#define ASSERT_STREQ(a, b) do { \
    const char *_a = (a); const char *_b = (b); \
    if (!_a || !_b || strcmp(_a, _b) != 0) { \
        fprintf(stderr, "  FAILED [%s:%d] %s: ASSERT_STREQ(\"%s\", \"%s\")\n", \
                __FILE__, __LINE__, g_current_test, \
                _a ? _a : "(null)", _b ? _b : "(null)"); \
        g_assert_failed = 1; \
        return; \
    } \
} while(0)

#define ASSERT_NEAR(a, b, eps) do { \
    double _a = (double)(a); double _b = (double)(b); \
    if (fabs(_a - _b) > (eps)) { \
        fprintf(stderr, "  FAILED [%s:%d] %s: ASSERT_NEAR(%s, %s, %g) (%g != %g)\n", \
                __FILE__, __LINE__, g_current_test, #a, #b, \
                (double)(eps), _a, _b); \
        g_assert_failed = 1; \
        return; \
    } \
} while(0)

#define ASSERT_MEMEQ(ptr1, ptr2, size) do { \
    if (memcmp((ptr1), (ptr2), (size)) != 0) { \
        fprintf(stderr, "  FAILED [%s:%d] %s: ASSERT_MEMEQ(%s, %s, %zu)\n", \
                __FILE__, __LINE__, g_current_test, #ptr1, #ptr2, \
                (size_t)(size)); \
        g_assert_failed = 1; \
        return; \
    } \
} while(0)

static int RUN_ALL_TESTS(void) {
    printf("========================================\n");
    printf("  Running %d test(s)\n", g_test_count);
    printf("========================================\n");
    for (int i = 0; i < g_test_count; i++) {
        g_current_test = g_test_table[i].name;
        g_assert_failed = 0;
        printf("[ RUN  ] %s\n", g_test_table[i].name);
        g_test_table[i].func();
        if (g_assert_failed) {
            printf("[ FAIL ] %s\n", g_test_table[i].name);
            g_test_failed++;
        } else {
            printf("[  OK  ] %s\n", g_test_table[i].name);
            g_test_passed++;
        }
    }
    printf("========================================\n");
    printf("  %d passed, %d failed out of %d\n",
           g_test_passed, g_test_failed, g_test_count);
    printf("========================================\n");
    return g_test_failed > 0 ? 1 : 0;
}

#endif /* TEST_RUNNER_H */
