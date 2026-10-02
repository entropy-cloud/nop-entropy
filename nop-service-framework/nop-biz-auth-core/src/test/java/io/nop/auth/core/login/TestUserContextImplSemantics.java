package io.nop.auth.core.login;

import io.nop.auth.core.AuthCoreConstants;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserContextImpl} 纯逻辑语义：角色判定（primaryRole / 内置 user 角色 / 角色集合）、
 * 脏标记跟踪（序列化回写依据）、JSON 序列化形态（null 字段剔除、mfaRestricted 条件持久化）。
 */
public class TestUserContextImplSemantics {

    // ==================== isUserInRole ====================

    /**
     * primaryRole 命中即视为拥有该角色。
     */
    @Test
    public void testUserInRolePrimaryRoleMatch() {
        UserContextImpl context = new UserContextImpl();
        context.setPrimaryRole("admin");

        assertTrue(context.isUserInRole("admin"), "primaryRole match must grant the role");
    }

    /**
     * 平台契约：任何登录用户恒具有内置 user 角色。
     */
    @Test
    public void testUserAlwaysHasBuiltInUserRole() {
        UserContextImpl context = new UserContextImpl();

        assertTrue(context.isUserInRole(AuthCoreConstants.ROLE_USER),
                "every user context must implicitly carry the built-in user role");
    }

    /**
     * roles 集合中的角色生效；集合外的角色不生效。
     */
    @Test
    public void testUserInRoleFromRoleSet() {
        UserContextImpl context = new UserContextImpl();
        context.setRoles(Set.of("admin", "reporter"));

        assertTrue(context.isUserInRole("admin"));
        assertTrue(context.isUserInRole("reporter"));
        assertFalse(context.isUserInRole("nop-admin"), "role outside the set must be denied");
    }

    /**
     * isUserInAnyRole：null 集合放行（无角色约束），空集合拒绝，含任一命中角色放行。
     */
    @Test
    public void testUserInAnyRoleSemantics() {
        UserContextImpl context = new UserContextImpl();
        context.setRoles(Set.of("admin"));

        assertTrue(context.isUserInAnyRole(null), "null role requirement means unconstrained");
        assertFalse(context.isUserInAnyRole(java.util.Set.of()),
                "empty role requirement must deny");
        assertTrue(context.isUserInAnyRole(java.util.Set.of("admin", "nop-admin")));
        assertFalse(context.isUserInAnyRole(java.util.Set.of("reporter")));
    }

    // ==================== dirty tracking ====================

    /**
     * 脏标记仅在字段实际变化时置位：相同值重复 set 不置脏（避免无效回写）。
     */
    @Test
    public void testDirtyOnlyOnActualChange() {
        UserContextImpl context = new UserContextImpl();
        context.setUserId("u1");
        assertTrue(context.dirty(), "initial assignment must mark dirty");

        context.clearDirty();
        context.setUserId("u1");
        assertFalse(context.dirty(), "setting the same value must not mark dirty");

        context.setUserId("u2");
        assertTrue(context.dirty(), "changing value must mark dirty");
    }

    /**
     * addRole/removeRole 的脏标记：新增/移除既有角色置脏，重复添加不额外置脏。
     */
    @Test
    public void testAddRemoveRoleDirtySemantics() {
        UserContextImpl context = new UserContextImpl();
        context.addRole("admin");
        assertTrue(context.dirty(), "adding a new role must mark dirty");

        context.clearDirty();
        context.addRole("admin");
        assertFalse(context.dirty(), "adding an existing role must not mark dirty");

        context.removeRole("admin");
        assertTrue(context.dirty(), "removing an existing role must mark dirty");

        context.clearDirty();
        context.removeRole("ghost");
        assertFalse(context.dirty(), "removing a non-member role must not mark dirty");
    }

    /**
     * accessToken/refreshToken setter 无条件置脏（token 轮换必须回写缓存）。
     */
    @Test
    public void testTokenSettersAlwaysMarkDirty() {
        UserContextImpl context = new UserContextImpl();
        context.clearDirty();

        context.setAccessToken("tok");
        assertTrue(context.dirty(), "setAccessToken must mark dirty");

        context.clearDirty();
        context.setRefreshToken("rtok");
        assertTrue(context.dirty(), "setRefreshToken must mark dirty");
    }

    /**
     * 扩展属性 setAttr/setAttrs 变更置脏；lastAccessTime 仅在值变化时置脏。
     */
    @Test
    public void testAttrsAndLastAccessTimeDirtySemantics() {
        UserContextImpl context = new UserContextImpl();
        context.clearDirty();

        context.setAttr("k", "v");
        assertTrue(context.dirty(), "setting a new attr must mark dirty");

        context.clearDirty();
        context.setAttr("k", "v");
        assertFalse(context.dirty(), "setting the same attr value must not mark dirty");

        context.clearDirty();
        context.setLastAccessTime(1000L);
        assertTrue(context.dirty(), "changing lastAccessTime must mark dirty");

        context.clearDirty();
        context.setLastAccessTime(1000L);
        assertFalse(context.dirty(), "same lastAccessTime must not mark dirty");
    }

    // ==================== JSON 序列化形态 ====================

    /**
     * 序列化剔除 null 字段、保留角色集合；mfaRestricted=false 时不输出该标志
     * （正常会话 JSON 形态零变化契约）。
     */
    @Test
    public void testJsonOmitsNullsAndMfaFlagWhenFalse() {
        UserContextImpl context = new UserContextImpl();
        context.setUserId("u1");
        context.setUserName("alice");
        context.setRoles(Set.of("admin"));

        String json = JsonTool.serialize(context, false);

        assertTrue(json.contains("\"userId\":\"u1\""), "userId must be serialized, got: " + json);
        assertTrue(json.contains("\"userName\":\"alice\""));
        assertTrue(json.contains("\"roles\""), "roles must be serialized");
        assertFalse(json.contains("nickName"), "null fields must be omitted");
        assertFalse(json.contains("mfaRestricted"),
                "mfaRestricted=false must not change the session JSON shape");
    }

    /**
     * mfaRestricted=true 时序列化携带该标志（受限会话 Dao-cache 持久化触点）。
     */
    @Test
    public void testMfaRestrictedSerializedOnlyWhenTrue() {
        UserContextImpl context = new UserContextImpl();
        context.setUserId("u1");
        context.setMfaRestricted(true);

        String json = JsonTool.serialize(context, false);
        assertTrue(json.contains("\"mfaRestricted\":true"),
                "restricted session must persist the mfaRestricted flag, got: " + json);

        context.setMfaRestricted(false);
        assertFalse(JsonTool.serialize(context, false).contains("mfaRestricted"),
                "clearing the flag must remove it from serialization");
    }

    /**
     * attrs 非空时序列化输出，空 attrs 不输出（null 剔除契约）。
     */
    @Test
    public void testAttrsSerialization() {
        UserContextImpl context = new UserContextImpl();
        context.setAttr("tenant-switch", "allowed");

        String json = JsonTool.serialize(context, false);
        assertTrue(json.contains("\"attrs\"") && json.contains("tenant-switch"),
                "non-empty attrs must be serialized, got: " + json);
    }

    /**
     * 登录类型与租户字段的 setter 脏标记联动（walk 关键字段）。
     */
    @Test
    public void testTenantAndLoginTypeDirtyTracking() {
        UserContextImpl context = new UserContextImpl();
        context.clearDirty();

        context.setTenantId("t1");
        assertTrue(context.dirty(), "tenant change must mark dirty");

        context.clearDirty();
        context.setLoginType(2);
        assertTrue(context.dirty(), "login type change must mark dirty");

        context.clearDirty();
        context.setLoginType(2);
        assertFalse(context.dirty(), "same login type must not mark dirty");
    }
}
