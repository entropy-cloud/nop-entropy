package io.nop.credential.web;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D4-02（A1-audit successor，2026-08-17）：action-auth delta 受限资源角色集与
 * {@code CredentialConfigs.CFG_CREDENTIAL_ADMIN_ROLES} 缺省值（{@code admin,nop-admin}）
 * 双源对齐的结构性断言——delta 解析后每个受限资源的 roles 集合必须同时包含
 * {@code admin} 与 {@code nop-admin}（静态层不跟踪 admin-roles 配置变化的边界声明
 * 见 owner doc `nop-credential.md` 管理面权限章节）。
 */
public class NopCredentialActionAuthDeltaTest {

    /** D4-02 对齐口径：受限资源 = delete/reencryptAll/Usage/Auth/OauthState 全部收紧点。 */
    private static final List<String> RESTRICTED_RESOURCE_IDS = Arrays.asList(
            "FNPT:NopCredential:delete",
            "FNPT:NopCredential:reencryptAll",
            "FNPT:NopCredentialUsage:query",
            "FNPT:NopCredentialUsage:mutation",
            "FNPT:NopCredentialAuth:query",
            "FNPT:NopCredentialAuth:mutation",
            "FNPT:NopCredentialOauthState:query",
            "FNPT:NopCredentialOauthState:mutation");

    private static Element requireResource(Document doc, String resourceId) {
        NodeList nodes = doc.getElementsByTagName("resource");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            if (resourceId.equals(el.getAttribute("id"))) {
                return el;
            }
        }
        return null;
    }

    @Test
    public void restrictedResourcesCarryAdminAndNopAdminRoles() throws Exception {
        String path = "_vfs/nop/credential/auth/nop-credential.action-auth.xml";
        try (InputStream in = NopCredentialActionAuthDeltaTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "delta file must exist on classpath: " + path);
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);

            for (String resourceId : RESTRICTED_RESOURCE_IDS) {
                Element el = requireResource(doc, resourceId);
                assertNotNull(el, "restricted resource must be declared in delta: " + resourceId);
                String roles = el.getAttribute("roles");
                Set<String> roleSet = new HashSet<>(Arrays.asList(roles.split("\\s*,\\s*")));
                assertTrue(roleSet.contains("admin"),
                        resourceId + " roles must contain 'admin' (D4-02 aligned with admin-roles default), got: " + roles);
                assertTrue(roleSet.contains("nop-admin"),
                        resourceId + " roles must contain 'nop-admin' (D4-02 aligned with admin-roles default), got: " + roles);
                assertEquals(2, roleSet.size(),
                        resourceId + " roles must be exactly the default admin-roles set {admin,nop-admin}, got: " + roles);
            }
        }
    }
}
