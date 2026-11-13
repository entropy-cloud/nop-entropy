package io.nop.auth.core.login;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C1-002（plan 2550 裁定 A）钉：roles 集合并发一致视图。
 * pin2a：并发 addRole × isUserInRole 无丢失/无 CME/权限误判；setRoles 拷贝进并发 Set；
 * serializeToJson 并发遍历面安全。
 */
public class TestUserContextRoles {

    @Test
    public void pin2a_concurrentAddRoleAndIsUserInRoleConsistentView() throws Exception {
        UserContextImpl ctx = new UserContextImpl();
        int writers = 8;
        int rolesPerWriter = 200;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int w = 0; w < writers; w++) {
            final int wid = w;
            pool.execute(() -> {
                try {
                    start.await();
                    for (int i = 0; i < rolesPerWriter; i++) {
                        String role = "role-" + wid + "-" + i;
                        ctx.addRole(role);
                        // 读侧（isUserInRole 裸读）与写侧并发：不得抛 CME / 误判已写角色
                        if (!ctx.isUserInRole(role)) {
                            failed.set(true);
                        }
                        ctx.getRoles().size();
                        if (i % 50 == 0) {
                            // 快照迭代面
                            for (String r : ctx.getRoles()) {
                                if (r == null)
                                    failed.set(true);
                            }
                        }
                    }
                } catch (Throwable t) {
                    failed.set(true);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent role writes must finish");
        pool.shutdownNow();

        assertFalse(failed.get(), "concurrent addRole x isUserInRole must stay consistent (no CME / lost role)");
        assertEquals(writers * rolesPerWriter, ctx.getRoles().size(), "no lost updates");
    }

    @Test
    public void pin2a_setRolesCopiesIntoConcurrentSet() {
        UserContextImpl ctx = new UserContextImpl();
        Set<String> external = new HashSet<>();
        external.add("admin");
        ctx.setRoles(external);

        // 外部 Set 后续变更不得影响上下文（拷贝语义）；字段不再是裸 HashSet
        external.add("intruder");
        assertFalse(ctx.isUserInRole("intruder"), "external set mutation must not leak into context");
        assertFalse(ctx.getRoles() instanceof HashSet, "roles field must not be the caller's bare HashSet");
        assertTrue(ctx.isUserInRole("admin"));

        // setRoles(null) 语义：清空
        ctx.setRoles(null);
        assertNull(ctx.getRoles());
        assertFalse(ctx.isUserInRole("admin"));
    }

    @Test
    public void pin2a_serializeToJsonSafeUnderConcurrentRoleAdd() throws Exception {
        UserContextImpl ctx = new UserContextImpl();
        ctx.addRole("r1");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch stop = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean(false);
        pool.execute(() -> {
            while (stop.getCount() > 0) {
                try {
                    // serializeToJson 遍历 roles（putNotNull("roles", roles)）
                    io.nop.core.lang.json.JsonTool.serialize(ctx, false);
                } catch (Throwable t) {
                    failed.set(true);
                    return;
                }
            }
        });
        for (int i = 0; i < 100; i++) {
            ctx.addRole("r-" + i);
        }
        stop.countDown();
        pool.shutdownNow();
        assertFalse(failed.get(), "serializeToJson must not break under concurrent addRole");
    }
}
