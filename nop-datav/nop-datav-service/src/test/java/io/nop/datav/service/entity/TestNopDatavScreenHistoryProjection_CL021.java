package io.nop.datav.service.entity;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavScreenBiz;
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenSnapshot;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CL-021 快照历史列投影红⑩钉（plan 2526 裁定⑪）。
 *
 * <p>结构断言（沿 2525 红④先例形态）：历史投影查询字段声明面恰为三元数据列、不含
 * SNAPSHOT_CONTENT 大列（缺陷态现走 findAllSnapshots 整行加载——结构断言当前即红）；
 * 功能断言：历史返回三元组与版本倒序不变。</p>
 */
public class TestNopDatavScreenHistoryProjection_CL021 extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavScreenBiz screenBiz;

    @Test
    public void testProjectionQueryExcludesSnapshotContent() {
        QueryBean query = io.nop.datav.service.entity.NopDatavScreenBizModel
                .historySnapshotQuery("screen-any");
        List<String> fields = query.getFields().stream()
                .map(QueryFieldBean::getName)
                .collect(Collectors.toList());
        assertEquals(3, fields.size(), "projection must declare exactly the three metadata columns");
        assertEquals(true, fields.containsAll(List.of("snapshotVersion", "publishedBy", "publishedTime")),
                "projection fields: " + fields);
        assertEquals(false, fields.contains("snapshotContent"),
                "SNAPSHOT_CONTENT must NOT be in the projected field set (red-10 structural assertion)");
    }

    /**
     * 接线钉（rule 23，沿 plan 2525 MA01 源码结构钉形态）：getScreenSnapshotHistory 方法体必须消费
     * historySnapshotQuery 投影通道、不得回退 findAllSnapshots 整行加载——变异（调用点回退旧路径）
     * 下本钉红。
     */
    @Test
    public void testHistoryMethodWiresProjectionQuery() throws Exception {
        java.nio.file.Path source = java.nio.file.Path.of(
                "src/main/java/io/nop/datav/service/entity/NopDatavScreenBizModel.java");
        String code = java.nio.file.Files.readString(source);
        int methodStart = code.indexOf(
                "public List<ScreenSnapshotHistory> getScreenSnapshotHistory");
        assertTrue(methodStart > 0, "getScreenSnapshotHistory must exist");
        int methodEnd = code.indexOf("\n    }", methodStart);
        String body = code.substring(methodStart, methodEnd);
        assertTrue(body.contains("historySnapshotQuery("),
                "history path must consume the projection query (not the full-row loader)");
        assertTrue(!body.contains("findAllSnapshots("),
                "full-row loader findAllSnapshots must NOT be used by the history path (red-10 wiring)");
    }

    @Test
    public void testScreenSnapshotHistoryReturnsTriplesInVersionOrder() {
        String screenId = seedScreenWithSnapshots("screen-cl021", 3);
        IServiceContext ctx = ownerContext("alice");

        List<?> history = screenBiz.getScreenSnapshotHistory(screenId, ctx);

        assertEquals(3, history.size(), "all snapshots listed");
        for (int i = 0; i < history.size(); i++) {
            Object entry = history.get(i);
            Long version = (Long) beanProp(entry, "snapshotVersion");
            assertNotNull(version, "snapshotVersion present");
            if (i > 0) {
                Long prev = (Long) beanProp(history.get(i - 1), "snapshotVersion");
                assertTrue(prev >= version, "descending version order");
            }
        }
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }

    private Object beanProp(Object bean, String name) {
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return bean.getClass().getMethod("get" + cap).invoke(bean);
        } catch (Exception e) {
            throw new IllegalStateException("no accessor for " + name, e);
        }
    }

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private String seedScreenWithSnapshots(String screenId, int count) {
        long now = System.currentTimeMillis();
        NopDatavScreen sc = new NopDatavScreen();
        sc.setScreenId(screenId);
        sc.setScreenName(screenId);
        sc.setDisplayName(screenId);
        sc.setScreenWidth(1920);
        sc.setScreenHeight(1080);
        sc.setAdaptorMode(10);
        sc.setPublishStatus(0);
        sc.setVersion(0L);
        sc.setCreatedBy("alice");
        sc.setCreateTime(new Timestamp(now));
        sc.setUpdatedBy("alice");
        sc.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavScreen.class).saveEntityDirectly(sc);

        for (int v = 1; v <= count; v++) {
            NopDatavScreenSnapshot snapshot = new NopDatavScreenSnapshot();
            snapshot.setSnapshotId(screenId + "-s" + v);
            snapshot.setScreenId(screenId);
            snapshot.setSnapshotVersion((long) v);
            snapshot.setSnapshotContent("{\"widgets\":" + v + "}");
            snapshot.setPublishedBy("alice");
            snapshot.setPublishedTime(new Timestamp(now + v));
            snapshot.setVersion(0L);
            snapshot.setCreatedBy("alice");
            snapshot.setCreateTime(new Timestamp(now + v));
            snapshot.setUpdatedBy("alice");
            snapshot.setUpdateTime(new Timestamp(now + v));
            daoProvider.daoFor(NopDatavScreenSnapshot.class).saveEntityDirectly(snapshot);
        }
        return screenId;
    }
}
