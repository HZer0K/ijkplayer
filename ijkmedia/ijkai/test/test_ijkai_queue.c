/*
 * test_ijkai_queue.c
 *
 * 异步队列单元测试
 * 覆盖: 创建/释放、入队出队、优先级淘汰、关闭唤醒、超时、多线程
 *
 * 编译: gcc -std=c99 -lpthread \
 *           -I.. -I../async \
 *           test_ijkai_queue.c ../async/ijkai_queue.c \
 *           -o test_ijkai_queue && ./test_ijkai_queue
 */

#define _POSIX_C_SOURCE 200809L

#include "test_runner.h"
#include "../async/ijkai_queue.h"
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>

/* 便携 sleep 函数 */
static void msleep(unsigned int ms) {
    struct timespec ts;
    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (long)(ms % 1000) * 1000000L;
    nanosleep(&ts, NULL);
}

/* ========== 辅助函数 (在使用前定义) ========== */

struct pop_args {
    ijkai_task_queue *q;
    ijkai_task *out;
    int *result;
};

static void *pop_waiter_thread(void *arg) {
    struct pop_args *pa = (struct pop_args*)arg;
    *(pa->result) = ijkai_queue_pop(pa->q, pa->out, 5000);
    return NULL;
}

static void *consumer_thread(void *arg) {
    ijkai_task_queue **args = (ijkai_task_queue**)arg;
    ijkai_task_queue *qq = args[0];
    volatile int *cnt = (volatile int*)args[1];
    for (int i = 0; i < 30; i++) {
        ijkai_task out;
        if (ijkai_queue_pop(qq, &out, 2000) == 0) {
            (*cnt)++;
        }
    }
    return NULL;
}

typedef struct {
    ijkai_task_queue *queue;
    int thread_id;
    int count;
    int *results;
    int start_delay_ms;
} producer_args;

static void *producer_thread(void *arg) {
    producer_args *pa = (producer_args*)arg;
    if (pa->start_delay_ms > 0) {
        msleep(pa->start_delay_ms);
    }
    for (int i = 0; i < pa->count; i++) {
        ijkai_task t = {IJKAI_TASK_LLM, NULL, pa->thread_id * 1000 + i, IJKAI_PRIORITY_NORMAL};
        int ret = ijkai_queue_push(pa->queue, &t);
        if (pa->results) pa->results[i] = ret;
    }
    return NULL;
}

/* ========== 创建/释放 ========== */

TEST(QueueCreate, basic) {
    ijkai_task_queue *q = ijkai_queue_create(10);
    ASSERT_NOT_NULL(q);
    ASSERT_EQ(ijkai_queue_size(q), 0);
    ASSERT_FALSE(ijkai_queue_is_full(q));
    ijkai_queue_release(q);
}

TEST(QueueCreate, zero_size_returns_null) {
    ijkai_task_queue *q = ijkai_queue_create(0);
    ASSERT_NULL(q);
}

TEST(QueueCreate, negative_size_returns_null) {
    ijkai_task_queue *q = ijkai_queue_create(-5);
    ASSERT_NULL(q);
}

TEST(QueueCreate, release_null_safe) {
    ijkai_queue_release(NULL);
    ASSERT_TRUE(1);
}

TEST(QueueCreate, single_element) {
    ijkai_task_queue *q = ijkai_queue_create(1);
    ASSERT_NOT_NULL(q);
    ijkai_queue_release(q);
}

/* ========== 入队/出队 ========== */

TEST(QueuePushPop, basic_fifo) {
    ijkai_task_queue *q = ijkai_queue_create(10);
    ASSERT_NOT_NULL(q);

    ijkai_task t1 = {IJKAI_TASK_LLM, NULL, 100, IJKAI_PRIORITY_NORMAL};
    ijkai_task t2 = {IJKAI_TASK_CV, NULL, 200, IJKAI_PRIORITY_HIGH};

    ASSERT_EQ(ijkai_queue_push(q, &t1), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t2), 0);
    ASSERT_EQ(ijkai_queue_size(q), 2);

    ijkai_task out;
    ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
    ASSERT_EQ(out.type, IJKAI_TASK_LLM);

    ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
    ASSERT_EQ(out.type, IJKAI_TASK_CV);

    ijkai_queue_release(q);
}

TEST(QueuePushPop, fill_and_drain) {
    ijkai_task_queue *q = ijkai_queue_create(5);
    ASSERT_NOT_NULL(q);

    for (int i = 0; i < 5; i++) {
        ijkai_task t = {IJKAI_TASK_LLM, NULL, 0, IJKAI_PRIORITY_NORMAL};
        ASSERT_EQ(ijkai_queue_push(q, &t), 0);
    }
    ASSERT_TRUE(ijkai_queue_is_full(q));
    ASSERT_EQ(ijkai_queue_size(q), 5);

    for (int i = 0; i < 5; i++) {
        ijkai_task out;
        ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
        ASSERT_EQ(out.type, IJKAI_TASK_LLM);
    }
    ASSERT_EQ(ijkai_queue_size(q), 0);

    ijkai_queue_release(q);
}

TEST(QueuePushPop, null_queue) {
    ijkai_task t = {IJKAI_TASK_LLM, NULL, 0, IJKAI_PRIORITY_NORMAL};
    ASSERT_EQ(ijkai_queue_push(NULL, &t), -1);

    ijkai_task out;
    ASSERT_EQ(ijkai_queue_pop(NULL, &out, 100), -1);
}

TEST(QueuePushPop, null_task) {
    ijkai_task_queue *q = ijkai_queue_create(5);
    ASSERT_EQ(ijkai_queue_push(q, NULL), -1);
    ijkai_queue_release(q);
}

TEST(QueuePushPop, pop_timeout) {
    ijkai_task_queue *q = ijkai_queue_create(5);
    ijkai_task out;
    ASSERT_EQ(ijkai_queue_pop(q, &out, 10), -1);
    ijkai_queue_release(q);
}

TEST(QueuePushPop, timestamp_auto_set) {
    ijkai_task_queue *q = ijkai_queue_create(5);
    ijkai_task t = {IJKAI_TASK_LLM, NULL, 0, IJKAI_PRIORITY_NORMAL};
    ASSERT_EQ(ijkai_queue_push(q, &t), 0);

    ijkai_task out;
    ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
    ASSERT_NE(out.timestamp, 0);

    ijkai_queue_release(q);
}

/* ========== 优先级淘汰 ========== */

TEST(QueuePriority, drop_lowest_when_full) {
    ijkai_task_queue *q = ijkai_queue_create(5);
    ASSERT_NOT_NULL(q);

    ijkai_task t1 = {IJKAI_TASK_LLM, NULL, 100, IJKAI_PRIORITY_HIGH};
    ijkai_task t2 = {IJKAI_TASK_CV, NULL, 200, IJKAI_PRIORITY_LOW};
    ijkai_task t3 = {IJKAI_TASK_LLM, NULL, 300, IJKAI_PRIORITY_LOW};
    ijkai_task t4 = {IJKAI_TASK_CV, NULL, 400, IJKAI_PRIORITY_NORMAL};
    ijkai_task t5 = {IJKAI_TASK_LLM, NULL, 500, IJKAI_PRIORITY_HIGH};

    ASSERT_EQ(ijkai_queue_push(q, &t1), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t2), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t3), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t4), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t5), 0);
    ASSERT_TRUE(ijkai_queue_is_full(q));

    // Push a CRITICAL priority task - should drop the oldest LOW priority task (t2 at ts=200)
    ijkai_task t6 = {IJKAI_TASK_CV, NULL, 600, IJKAI_PRIORITY_CRITICAL};
    ASSERT_EQ(ijkai_queue_push(q, &t6), 0);
    ASSERT_EQ(ijkai_queue_size(q), 5);

    // Remaining: t1(HIGH,100), t3(LOW,300), t4(NORMAL,400), t5(HIGH,500), t6(CRITICAL,600)
    // t2(LOW,200) should be dropped
    int found_high = 0, found_normal = 0, found_critical = 0, found_low = 0;
    for (int i = 0; i < 5; i++) {
        ijkai_task out;
        ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
        if (out.priority == IJKAI_PRIORITY_HIGH) found_high++;
        if (out.priority == IJKAI_PRIORITY_NORMAL) found_normal++;
        if (out.priority == IJKAI_PRIORITY_CRITICAL) found_critical++;
        if (out.priority == IJKAI_PRIORITY_LOW) found_low++;
    }
    ASSERT_EQ(found_high, 2);    // t1, t5
    ASSERT_EQ(found_normal, 1);   // t4
    ASSERT_EQ(found_critical, 1); // t6
    ASSERT_EQ(found_low, 1);      // t3 (the newer LOW)

    ijkai_queue_release(q);
}

TEST(QueuePriority, drop_oldest_same_priority) {
    ijkai_task_queue *q = ijkai_queue_create(3);

    ijkai_task t1 = {IJKAI_TASK_LLM, NULL, 100, IJKAI_PRIORITY_NORMAL};
    ijkai_task t2 = {IJKAI_TASK_CV, NULL, 200, IJKAI_PRIORITY_NORMAL};
    ijkai_task t3 = {IJKAI_TASK_LLM, NULL, 300, IJKAI_PRIORITY_NORMAL};

    ASSERT_EQ(ijkai_queue_push(q, &t1), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t2), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t3), 0);

    ijkai_task t4 = {IJKAI_TASK_CV, NULL, 400, IJKAI_PRIORITY_NORMAL};
    ASSERT_EQ(ijkai_queue_push(q, &t4), 0); // drops t1 (oldest same priority)

    ijkai_task out;
    ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
    ASSERT_EQ(out.timestamp, 200); // t2
    ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
    ASSERT_EQ(out.timestamp, 300); // t3
    ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
    ASSERT_EQ(out.timestamp, 400); // t4

    ijkai_queue_release(q);
}

TEST(QueuePriority, new_low_rejected_when_full) {
    ijkai_task_queue *q = ijkai_queue_create(3);
    ASSERT_NOT_NULL(q);

    ijkai_task t1 = {IJKAI_TASK_LLM, NULL, 100, IJKAI_PRIORITY_HIGH};
    ijkai_task t2 = {IJKAI_TASK_CV, NULL, 200, IJKAI_PRIORITY_HIGH};
    ijkai_task t3 = {IJKAI_TASK_LLM, NULL, 300, IJKAI_PRIORITY_HIGH};
    ASSERT_EQ(ijkai_queue_push(q, &t1), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t2), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t3), 0);

    ijkai_task t4 = {IJKAI_TASK_CV, NULL, 400, IJKAI_PRIORITY_LOW};
    ASSERT_EQ(ijkai_queue_push(q, &t4), -1); // rejected
    ASSERT_EQ(ijkai_queue_size(q), 3);

    ijkai_queue_release(q);
}

/* ========== 关闭/停止 ========== */

TEST(QueueShutdown, basic) {
    ijkai_task_queue *q = ijkai_queue_create(5);
    ijkai_queue_shutdown(q);

    ijkai_task out;
    ASSERT_EQ(ijkai_queue_pop(q, &out, 10), -1);

    ijkai_queue_release(q);
}

TEST(QueueShutdown, wakes_up_waiter) {
    ijkai_task_queue *q = ijkai_queue_create(5);
    int pop_result = -2;
    ijkai_task out;
    struct pop_args args = {q, &out, &pop_result};

    pthread_t thread;
    pthread_create(&thread, NULL, pop_waiter_thread, &args);

    msleep(100);
    ijkai_queue_shutdown(q);
    pthread_join(thread, NULL);

    ASSERT_EQ(pop_result, -1);
    ijkai_queue_release(q);
}

TEST(QueueShutdown, null_safe) {
    ijkai_queue_shutdown(NULL);
    ASSERT_TRUE(1);
}

/* ========== 边界条件 ========== */

TEST(QueueEdge, size_of_null) {
    ASSERT_EQ(ijkai_queue_size(NULL), 0);
}

TEST(QueueEdge, is_full_of_null) {
    ASSERT_FALSE(ijkai_queue_is_full(NULL));
}

TEST(QueueEdge, push_after_shutdown) {
    ijkai_task_queue *q = ijkai_queue_create(5);
    ijkai_queue_shutdown(q);
    ijkai_task t = {IJKAI_TASK_LLM, NULL, 0, IJKAI_PRIORITY_NORMAL};
    ASSERT_EQ(ijkai_queue_push(q, &t), 0);
    ijkai_queue_release(q);
}

/* ========== 多线程 ========== */

TEST(QueueMultiThread, multiple_producers) {
    ijkai_task_queue *q = ijkai_queue_create(100);
    ASSERT_NOT_NULL(q);

    pthread_t p1, p2;
    producer_args a1 = {q, 0, 20, NULL, 0};
    producer_args a2 = {q, 1, 20, NULL, 0};

    pthread_create(&p1, NULL, producer_thread, &a1);
    pthread_create(&p2, NULL, producer_thread, &a2);

    int total = 0;
    while (total < 40) {
        ijkai_task out;
        int ret = ijkai_queue_pop(q, &out, 100);
        if (ret == 0) total++;
    }

    pthread_join(p1, NULL);
    pthread_join(p2, NULL);
    ASSERT_EQ(total, 40);
    ijkai_queue_release(q);
}

TEST(QueueMultiThread, producer_consumer) {
    ijkai_task_queue *q = ijkai_queue_create(10);
    ASSERT_NOT_NULL(q);

    volatile int consumed = 0;
    void *consumer_arg[2] = {q, (void*)&consumed};

    pthread_t consumer;
    pthread_create(&consumer, NULL, consumer_thread, consumer_arg);

    msleep(50);
    for (int i = 0; i < 30; i++) {
        ijkai_task t = {IJKAI_TASK_LLM, NULL, i, IJKAI_PRIORITY_NORMAL};
        ijkai_queue_push(q, &t);
        msleep(1);
    }

    pthread_join(consumer, NULL);
    ASSERT_EQ(consumed, 30);
    ijkai_queue_release(q);
}

/* ========== 大数据量压力 ========== */

TEST(QueueStress, push_pop_1000) {
    ijkai_task_queue *q = ijkai_queue_create(50);
    ASSERT_NOT_NULL(q);

    for (int round = 0; round < 20; round++) {
        for (int i = 0; i < 50; i++) {
            ijkai_task t = {IJKAI_TASK_LLM, NULL, round * 1000 + i,
                (ijkai_priority)(i % 4)};
            ASSERT_EQ(ijkai_queue_push(q, &t), 0);
        }
        for (int i = 0; i < 50; i++) {
            ijkai_task out;
            ASSERT_EQ(ijkai_queue_pop(q, &out, 1000), 0);
        }
    }
    ASSERT_EQ(ijkai_queue_size(q), 0);
    ijkai_queue_release(q);
}

/* ========== 优先级组合 ========== */

TEST(QueueFIFO, mixed_priorities) {
    ijkai_task_queue *q = ijkai_queue_create(10);
    ASSERT_NOT_NULL(q);

    ijkai_task t1 = {IJKAI_TASK_LLM, NULL, 100, IJKAI_PRIORITY_HIGH};
    ijkai_task t2 = {IJKAI_TASK_CV, NULL, 200, IJKAI_PRIORITY_NORMAL};
    ijkai_task t3 = {IJKAI_TASK_CV, NULL, 300, IJKAI_PRIORITY_CRITICAL};
    ijkai_task t4 = {IJKAI_TASK_LLM, NULL, 400, IJKAI_PRIORITY_LOW};

    ASSERT_EQ(ijkai_queue_push(q, &t1), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t2), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t3), 0);
    ASSERT_EQ(ijkai_queue_push(q, &t4), 0);

    ijkai_task out;
    ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
    ASSERT_EQ(out.priority, IJKAI_PRIORITY_HIGH);
    ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
    ASSERT_EQ(out.priority, IJKAI_PRIORITY_NORMAL);
    ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
    ASSERT_EQ(out.priority, IJKAI_PRIORITY_CRITICAL);
    ASSERT_EQ(ijkai_queue_pop(q, &out, 100), 0);
    ASSERT_EQ(out.priority, IJKAI_PRIORITY_LOW);

    ijkai_queue_release(q);
}

int main(void) {
    return RUN_ALL_TESTS();
}
