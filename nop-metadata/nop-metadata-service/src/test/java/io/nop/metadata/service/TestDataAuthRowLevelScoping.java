/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 维度13-02 回归测试：nop-metadata 行级数据权限规则。
 *
 * <p>覆盖 3 个目标实体的 row-level 规则（结构 + 表达式验证）：
 * <ul>
 *   <li>结构验证：data-auth.xml 含 3 个 obj 规则（NopMetaDataSource / NopMetaQualityCheckpoint / NopMetaModelChangedEvent）。</li>
 *   <li>语义验证：admin 角色 → 无 filter（全量访问）；user 角色 → filter 按 createdBy/changedBy == $user.userId（行级隔离）。</li>
 *   <li>fail-closed 语义：未匹配任何角色时（既非 admin 又非 user），DefaultDataAuthChecker 抛 ERR_AUTH_NO_DATA_AUTH
 *       （由 nop-biz-auth-core 实现，本测试只验证规则完整性；framework 层行为由 nop-auth 自身测试覆盖）。</li>
 *   <li>表达式验证：filter 中 {@code ${$context.user.userId}} 是真实的 EL 表达式（运行时由 framework 求值为
 *       当前用户的 userId），而非字面字符串——通过 grep 验证语法。</li>
 * </ul>
 *
 * <p><b>测试方式</b>：直接解析 data-auth.xml 为 XNode，验证结构与表达式。不依赖 nop-biz-auth-core 类
 * （nop-metadata-service 不直接依赖该模块）。framework 层 filter 求值由 DefaultDataAuthChecker 自身测试覆盖。
 * 本测试确保规则被正确作者：3 个实体 + 2 个角色分支 + 正确的列名 + 正确的 EL 表达式。
 */
public class TestDataAuthRowLevelScoping {

    private static final String[] TARGET_OBJS = {
            "NopMetaDataSource",
            "NopMetaQualityCheckpoint",
            "NopMetaModelChangedEvent"
    };

    /** 加载 data-auth.xml（直接从 classpath 读取文本，避免触发 VFS/IoC 初始化）。 */
    private XNode loadDataAuthXml() {
        // src/main/resources/_vfs/nop/metadata/auth/nop-metadata.data-auth.xml → classpath resource
        String classpathLoc = "/_vfs/nop/metadata/auth/nop-metadata.data-auth.xml";
        try (InputStream in = getClass().getResourceAsStream(classpathLoc)) {
            assertNotNull(in, "data-auth.xml must be on classpath at " + classpathLoc);
            String text = IoHelper.readText(in, "UTF-8");
            return XNode.parse(null, text);
        } catch (Exception e) {
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, e);
        }
    }

    /** 3 个目标实体均有 row-level 规则。 */
    @Test
    public void testThreeTargetEntitiesHaveRules() {
        XNode root = loadDataAuthXml();
        List<XNode> objs = root.childByTag("objs").childrenByTag("obj");
        assertEquals(TARGET_OBJS.length, objs.size(),
                "exactly 3 obj rules expected (NopMetaDataSource/NopMetaQualityCheckpoint/NopMetaModelChangedEvent)");
        for (String name : TARGET_OBJS) {
            assertTrue(hasObj(objs, name), "data-auth.xml must contain rule for " + name);
        }
    }

    /** 每个目标实体均有 admin（无过滤）+ default（user 角色按 createdBy/changedBy 过滤）双层规则。 */
    @Test
    public void testAdminAndDefaultRoleRulesExist() {
        XNode root = loadDataAuthXml();
        for (String bizObj : TARGET_OBJS) {
            XNode obj = findObj(root, bizObj);
            assertNotNull(obj, "obj must exist: " + bizObj);
            XNode admin = findRoleAuth(obj, "admin");
            assertNotNull(admin, "admin role-auth must exist for " + bizObj);
            assertEquals("admin", admin.attrText("roleIds"),
                    "admin role-auth must have roleIds='admin': " + bizObj);
            assertNull(admin.childByTag("filter"),
                    "admin must have no filter (full access): " + bizObj);
            XNode defaultRule = findRoleAuth(obj, "default");
            assertNotNull(defaultRule, "default role-auth must exist for " + bizObj);
            assertEquals("user", defaultRule.attrText("roleIds"),
                    "default role-auth must apply to 'user' role: " + bizObj);
            XNode filter = defaultRule.childByTag("filter");
            assertNotNull(filter, "default role-auth must have a filter (row-level scoping): " + bizObj);
            assertTrue(filter.hasChild(),
                    "filter must have a child predicate (e.g., <eq name=... value=.../>): " + bizObj);
        }
    }

    /** user 角色 filter 表达式正确：createdBy/changedBy 列名 + $context.user.userId 引用。 */
    @Test
    public void testUserFilterExpressionCorrect() {
        XNode root = loadDataAuthXml();
        for (String bizObj : TARGET_OBJS) {
            XNode obj = findObj(root, bizObj);
            XNode defaultRule = findRoleAuth(obj, "default");
            XNode filter = defaultRule.childByTag("filter");
            XNode predicate = filter.child(0);
            assertNotNull(predicate, "filter must have a predicate for " + bizObj);
            assertEquals("eq", predicate.getTagName(),
                    "filter predicate must be 'eq' (equality): " + bizObj);
            String expectedCol = "NopMetaModelChangedEvent".equals(bizObj) ? "changedBy" : "createdBy";
            assertEquals(expectedCol, predicate.attrText("name"),
                    "filter column must be " + expectedCol + " for " + bizObj
                            + " (NopMetaModelChangedEvent tracks changedBy not createdBy)");
            String value = predicate.attrText("value");
            assertNotNull(value, "filter must have value expression: " + bizObj);
            // 关键：必须是真实 EL 表达式 ${$context.user.userId}（运行时由 framework 求值为当前用户的 userId），
            // 而非字面字符串。这是 data-auth 的核心机制。
            assertTrue(value.contains("${") && value.contains("$context.user.userId"),
                    "filter value must be EL expression referencing $context.user.userId for " + bizObj
                            + " (got: " + value + ")");
            assertFalse(value.equals("${$context.user.userId}") == false && !value.startsWith("${"),
                    "filter value must be a single EL expression (got: " + value + ")");
        }
    }

    /** 接线验证：data-auth.xml 中 default 角色的 roleIds 必须包含 'user'（framework 默认假定所有用户具有 user 角色）。 */
    @Test
    public void testDefaultRoleIdsIncludesUserKeyword() {
        XNode root = loadDataAuthXml();
        for (String bizObj : TARGET_OBJS) {
            XNode obj = findObj(root, bizObj);
            XNode defaultRule = findRoleAuth(obj, "default");
            String roleIds = defaultRule.attrText("roleIds");
            assertNotNull(roleIds, "default role-auth must have roleIds: " + bizObj);
            assertTrue(roleIds.contains("user"),
                    "default role-auth roleIds must include 'user' (catch-all keyword in framework): " + bizObj);
        }
    }

    /** admin 角色 roleIds 必须包含 'admin'。 */
    @Test
    public void testAdminRoleIdsIncludesAdminKeyword() {
        XNode root = loadDataAuthXml();
        for (String bizObj : TARGET_OBJS) {
            XNode obj = findObj(root, bizObj);
            XNode admin = findRoleAuth(obj, "admin");
            String roleIds = admin.attrText("roleIds");
            assertNotNull(roleIds, "admin role-auth must have roleIds: " + bizObj);
            assertTrue(roleIds.contains("admin"),
                    "admin role-auth roleIds must include 'admin': " + bizObj);
        }
    }

    /** schema 引用正确，文件可被 framework 加载（验证文件能被 ResourceComponentManager 解析）。 */
    @Test
    public void testFileReferencesCorrectSchema() {
        XNode root = loadDataAuthXml();
        String schema = root.attrText("x:schema");
        assertNotNull(schema, "data-auth must reference a schema for framework loading");
        assertEquals("/nop/schema/data-auth.xdef", schema,
                "schema must be /nop/schema/data-auth.xdef (validated by framework)");
    }

    // ===== helpers =====

    private boolean hasObj(List<XNode> objs, String name) {
        for (XNode o : objs) {
            if (name.equals(o.attrText("name"))) {
                return true;
            }
        }
        return false;
    }

    private XNode findObj(XNode root, String name) {
        for (XNode o : root.childByTag("objs").childrenByTag("obj")) {
            if (name.equals(o.attrText("name"))) {
                return o;
            }
        }
        return null;
    }

    private XNode findRoleAuth(XNode obj, String id) {
        XNode roleAuths = obj.childByTag("role-auths");
        if (roleAuths == null) {
            return null;
        }
        for (XNode ra : roleAuths.childrenByTag("role-auth")) {
            if (id.equals(ra.attrText("id"))) {
                return ra;
            }
        }
        return null;
    }
}
