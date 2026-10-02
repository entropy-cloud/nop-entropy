package io.nop.wf.api.actor;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5: WfActorHelper 从 JSON 结构解析参与者的语义测试。
 * 缺省 actorType=user、空 id 被跳过、null/空输入返回空列表。
 */
public class TestWfActorHelper {
    private final IWfActorResolver resolver = new IWfActorResolver() {
        @Override
        public IWfActor resolveUser(String userId) {
            WfUserActorBean user = new WfUserActorBean();
            user.setActorId(userId);
            user.setActorName("name-" + userId);
            return user;
        }

        @Override
        public IWfActor resolveActor(String actorType, String actorId, String deptId) {
            if (IWfActor.ACTOR_TYPE_USER.equals(actorType))
                return resolveUser(actorId);
            WfActorBean actor = new WfActorBean();
            actor.setActorType(actorType);
            actor.setActorId(actorId);
            actor.setDeptId(deptId);
            return actor;
        }

        @Override
        public IWfActor getManager(IWfActor actor, int upLevel) {
            return null;
        }

        @Override
        public IWfActor getDeptManager(IWfActor actor, int upLevel) {
            return null;
        }
    };

    @Test
    public void testResolveActorFromJsonDefaultsToUserType() {
        Map<String, Object> json = new HashMap<>();
        json.put("id", "u1");

        IWfActor actor = WfActorHelper.resolveActorFromJson(resolver, json);
        assertEquals("user", actor.getActorType());
        assertEquals("u1", actor.getActorId());
    }

    @Test
    public void testResolveActorFromJsonWithDeptScope() {
        Map<String, Object> json = new HashMap<>();
        json.put("id", "d1");
        json.put("type", "dept");
        json.put("deptId", "dept-1");

        IWfActor actor = WfActorHelper.resolveActorFromJson(resolver, json);
        assertEquals("dept", actor.getActorType());
        assertEquals("dept-1", actor.getDeptId());
    }

    @Test
    public void testResolveActorFromJsonSkipsEmptyId() {
        assertNull(WfActorHelper.resolveActorFromJson(resolver, null));

        Map<String, Object> noId = new HashMap<>();
        assertNull(WfActorHelper.resolveActorFromJson(resolver, noId));

        Map<String, Object> emptyId = new HashMap<>();
        emptyId.put("id", "");
        assertNull(WfActorHelper.resolveActorFromJson(resolver, emptyId));
    }

    @Test
    public void testResolveActorsFromJsonSkipsInvalidEntries() {
        assertTrue(WfActorHelper.resolveActorsFromJson(resolver, null).isEmpty());
        assertTrue(WfActorHelper.resolveActorsFromJson(resolver, Collections.emptyList()).isEmpty());

        Map<String, Object> valid = new HashMap<>();
        valid.put("id", "u1");
        Map<String, Object> invalid = new HashMap<>();
        invalid.put("type", "user");

        List<IWfActor> actors = WfActorHelper.resolveActorsFromJson(resolver, Arrays.asList(valid, invalid));
        assertEquals(1, actors.size());
        assertEquals("u1", actors.get(0).getActorId());
    }
}
