package io.nop.file.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.file.dao.entity.NopFileRecord;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.orm.IOrmTemplate;
import io.nop.xlang.xmeta.IObjPropMeta;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-3.8-001 修复回归（plan 2386）：
 * ①filePath/isPublic 源模型 updatable=false —— CRUD update 携带这两列的变更时
 * 必须被 meta 层丢弃（validateForUpdate 过滤 isUpdatable()=false 的属性），落库值不变；
 * 普通可更新列（fileName）同请求中正常生效，证明 update 管线本身未被破坏。
 * ②merged xbiz 的 save/update/delete mutation 携带 roles=admin auth 声明。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestNopFileRecordUpdateProtectedProps extends JunitAutoTestCase {

    @Inject
    IBizObjectManager bizObjectManager;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Test
    public void testMetaMarksFilePathAndIsPublicNotUpdatable() {
        IBizObject bizObj = bizObjectManager.getBizObject("NopFileRecord");
        assertNotNull(bizObj);

        IObjPropMeta filePath = bizObj.requireObjMeta().getProp("filePath");
        assertNotNull(filePath, "filePath prop must exist in merged meta");
        assertFalse(filePath.isUpdatable(), "filePath must not be updatable via CRUD (P1-3.8-001)");

        IObjPropMeta isPublic = bizObj.requireObjMeta().getProp("isPublic");
        assertNotNull(isPublic, "isPublic prop must exist in merged meta");
        assertFalse(isPublic.isUpdatable(), "isPublic must not be updatable via CRUD (P1-3.8-001)");

        IObjPropMeta fileName = bizObj.requireObjMeta().getProp("fileName");
        assertNotNull(fileName);
        assertTrue(fileName.isUpdatable(), "fileName stays a normal updatable column");
    }

    @Test
    public void testUpdateDropsFilePathAndIsPublicChanges() {
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord record = dao.newEntity();
        record.setFileId("upd-prot-" + System.nanoTime());
        record.setFileName("orig.txt");
        record.setFilePath("/test/upd-prot/orig.txt");
        record.setFileExt("txt");
        record.setMimeType("text/plain");
        record.setFileLength(1L);
        record.setBizObjName("TestObj");
        record.setBizObjId("obj-1");
        record.setFieldName("attach");
        record.setOriginFileId("test");
        record.setIsPublic(false);
        dao.saveEntity(record);

        IBizObject bizObj = bizObjectManager.getBizObject("NopFileRecord");
        assertNotNull(bizObj);

        Map<String, Object> data = new HashMap<>();
        data.put("id", record.getFileId());
        data.put("fileName", "renamed.txt");
        // 攻击载荷：把 filePath 指向任意服务器路径 + 打开公开访问
        data.put("filePath", "/etc/passwd");
        data.put("isPublic", true);

        Map<String, Object> request = new HashMap<>();
        request.put("data", data);
        ormTemplate.runInNewSession(session -> {
            bizObj.invoke("update", request, null, new ServiceContextImpl());
            return null;
        });

        NopFileRecord reloaded = dao.requireEntityById(record.getFileId());
        assertEquals("renamed.txt", reloaded.getFileName(),
                "normal updatable column must be updated in the same request");
        assertEquals("/test/upd-prot/orig.txt", reloaded.getFilePath(),
                "filePath change must be dropped (updatable=false, P1-3.8-001)");
        assertEquals(Boolean.FALSE, reloaded.getIsPublic(),
                "isPublic change must be dropped (updatable=false, P1-3.8-001)");
    }

    @Test
    public void testMutationActionsCarryAdminAuth() {
        IBizObject bizObj = bizObjectManager.getBizObject("NopFileRecord");
        assertNotNull(bizObj);
        String prefix = bizObj.getBizObjName() + "__";

        for (String action : new String[]{"save", "update", "delete", "saveAll", "deleteAll"}) {
            GraphQLFieldDefinition def = findMutation(bizObj, prefix + action);
            assertNotNull(def, prefix + action + " must be registered as mutation");

            ActionAuthMeta auth = def.getAuth();
            assertNotNull(auth, prefix + action + " must carry <auth> after merge (P1-3.8-001)");
            assertNotNull(auth.getRoles(), prefix + action + " auth must declare roles");
            assertTrue(auth.getRoles().contains("admin"), prefix + action + " must be admin-only");
        }
    }

    private GraphQLFieldDefinition findMutation(IBizObject bizObj, String name) {
        Collection<GraphQLFieldDefinition> ops = bizObj.getOperationDefinitions(GraphQLOperationType.mutation);
        if (ops == null)
            return null;
        for (GraphQLFieldDefinition def : ops) {
            if (name.equals(def.getName()))
                return def;
        }
        return null;
    }
}
