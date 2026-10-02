package io.nop.wf.api.actor;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5: 参与者模型语义测试。
 * IWfActor default 方法（actorKey/containsUser/isSame/isActor）与
 * WfActorCandidatesBean/WfActorCandidateBean 的候选匹配语义。
 */
public class TestWfActorModelSemantics {
    private static WfUserActorBean user(String id, String deptId) {
        WfUserActorBean user = new WfUserActorBean();
        user.setActorId(id);
        user.setActorName(id);
        user.setDeptId(deptId);
        return user;
    }

    private static WfActorBean actor(String type, String id, String deptId, String... userIds) {
        WfActorBean actor = new WfActorBean();
        actor.setActorType(type);
        actor.setActorId(id);
        actor.setDeptId(deptId);
        if (userIds.length > 0) {
            actor.setUsers(Arrays.stream(userIds).map(uid -> {
                WfUserActorBean u = new WfUserActorBean();
                u.setActorId(uid);
                u.setActorName(uid);
                return u;
            }).collect(java.util.stream.Collectors.toList()));
        }
        return actor;
    }

    @Test
    public void testActorKeySemantics() {
        // user 类型 actorKey 即 userId；非 user 类型为 type:id
        assertEquals("u1", user("u1", null).getActorKey());
        assertEquals("dept:d1", actor("dept", "d1", null).getActorKey());
        assertEquals("role:r1", actor("role", "r1", null).getActorKey());
    }

    @Test
    public void testContainsUserSemantics() {
        // user actor 只包含自身
        assertTrue(user("u1", null).containsUser("u1"));
        assertFalse(user("u1", null).containsUser("u2"));

        // 非 user actor 通过 users 成员列表判断
        WfActorBean dept = actor("dept", "d1", null, "u1", "u2");
        assertTrue(dept.containsUser("u1"));
        assertFalse(dept.containsUser("u3"));

        // all actor 包含任意用户
        assertTrue(actor(IWfActor.ACTOR_TYPE_ALL, "all", null).containsUser("anyone"));
    }

    @Test
    public void testIsSameAndIsActor() {
        WfUserActorBean u1 = user("u1", "dept-1");
        WfUserActorBean same = user("u1", "dept-1");
        WfUserActorBean otherDept = user("u1", "dept-2");

        assertTrue(u1.isSame(same));
        // deptId 不同则不算同一个 actor
        assertFalse(u1.isSame(otherDept));

        assertTrue(u1.isActor(IWfActor.ACTOR_TYPE_USER, "u1", null));
        assertTrue(u1.isActor(IWfActor.ACTOR_TYPE_USER, "u1", "dept-1"));
        assertFalse(u1.isActor(IWfActor.ACTOR_TYPE_USER, "u1", "dept-2"));
        assertTrue(u1.isUser("u1"));
        assertFalse(u1.isUser("u2"));
    }

    @Test
    public void testWfActorBeanLazyUsersLoader() {
        // 未配置 users 时返回空列表
        WfActorBean empty = new WfActorBean();
        empty.setActorType("dept");
        empty.setActorId("d1");
        assertTrue(empty.getUsers().isEmpty());

        // loader 必须在首次 getUsers() 之前设置才能生效
        WfActorBean actor = new WfActorBean();
        actor.setActorType("dept");
        actor.setActorId("d2");
        actor.setUsersLoader(() -> Collections.singletonList(user("u9", null)));
        assertEquals(1, actor.getUsers().size());
        assertEquals("u9", actor.getUsers().get(0).getActorId());

        // getUsers() 首次调用即把结果物化进 users 字段，之后再设置 loader 会被忽略（产品语义）
        WfActorBean lateLoader = new WfActorBean();
        assertTrue(lateLoader.getUsers().isEmpty());
        lateLoader.setUsersLoader(() -> Collections.singletonList(user("u9", null)));
        assertTrue(lateLoader.getUsers().isEmpty(), "物化后设置的loader不生效");
    }

    @Test
    public void testCandidatesContainAndFind() {
        WfActorCandidatesBean candidates = new WfActorCandidatesBean();
        assertFalse(candidates.hasActor());
        assertNull(candidates.findCandidate(user("u1", null)));

        WfActorBean dept = actor("dept", "d1", null, "u1", "u2");
        candidates.addActorCandidate(dept, true, "m1", 3, false);

        assertTrue(candidates.hasActor());
        assertTrue(candidates.containsUser("u1"));
        assertFalse(candidates.containsUser("u3"));
        assertTrue(candidates.containsActor(actor("dept", "d1", null)));

        // selectUser=true 的候选：匹配其 users 列表中的 user 类型 actor
        WfActorCandidateBean found = candidates.findCandidate(user("u2", null));
        assertNotNull(found);
        assertEquals("m1", found.getActorModelId());
        assertEquals(3, found.getVoteWeight());
        assertNull(candidates.findCandidate(user("u3", null)));
    }

    @Test
    public void testCandidateSelectedActorMatchingByMode() {
        // selectUser=false：按 actor 本身 isSame 匹配，user 类型 actor 无法命中 dept 候选
        WfActorCandidateBean direct = new WfActorCandidateBean(
                actor("dept", "d1", null, "u1"), false, "m1", 1, false);
        assertTrue(direct.containsSelectedActor(actor("dept", "d1", null)));
        assertFalse(direct.containsSelectedActor(user("u1", null)));

        // selectUser=true：user 类型 actor 通过 containsUser 命中
        WfActorCandidateBean selectUser = new WfActorCandidateBean(
                actor("dept", "d1", null, "u1", "u2"), true, "m2", 2, true);
        assertTrue(selectUser.containsSelectedActor(user("u1", null)));
        assertFalse(selectUser.containsSelectedActor(user("u3", null)));
        // 非 user 类型 actor 不参与 selectUser 匹配
        assertFalse(selectUser.containsSelectedActor(actor("role", "u1", null)));

        assertTrue(selectUser.containsUser("u2"));
        assertTrue(direct.containsActor(actor("dept", "d1", null)));
    }
}
