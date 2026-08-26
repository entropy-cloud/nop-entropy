/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.orm.fetcher;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.graphql.GraphQLConnection;
import io.nop.api.core.beans.graphql.GraphQLPageInfo;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.context.IServiceContext;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.biz.GraphQLQueryMethod;
import io.nop.graphql.core.biz.IBizObjectQueryProcessor;
import io.nop.graphql.core.engine.DataFetchingEnvironment;
import io.nop.graphql.core.engine.GraphQLExecutionContext;
import io.nop.core.context.ServiceContextImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 偏移分页契约：query对象路径（query:{offset,limit}）下hasNextPage必须按生效limit判定。
 * first/last路径的limit必须在fetcher层按maxFetchSize钳制。
 */
public class TestOrmEntityPropConnectionFetcher extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 记录doFindPage0实际收到的query，返回固定行数。
     */
    static class RecordingProcessor implements IBizObjectQueryProcessor<Object> {
        final AtomicReference<QueryBean> capturedQuery = new AtomicReference<>();
        final int returnRows;

        RecordingProcessor(int returnRows) {
            this.returnRows = returnRows;
        }

        @Override
        public long doFindCount0(QueryBean query, String authObjName,
                                 BiConsumer<QueryBean, IServiceContext> prepareQuery, IServiceContext context) {
            return 0;
        }

        @Override
        public PageBean<Object> doFindPage0(QueryBean query, String authObjName,
                                            BiConsumer<QueryBean, IServiceContext> prepareQuery,
                                            FieldSelectionBean selection, IServiceContext context) {
            capturedQuery.set(query);
            prepareQuery.accept(query, context);
            PageBean<Object> page = new PageBean<>();
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < returnRows; i++)
                items.add(new Item("row-" + i));
            page.setItems(items);
            page.setTotal(returnRows);
            return page;
        }

        @Override
        public List<Object> doFindList0(QueryBean query, String authObjName,
                                        BiConsumer<QueryBean, IServiceContext> prepareQuery,
                                        FieldSelectionBean selection, IServiceContext context) {
            return null;
        }

        @Override
        public Object doFindFirst0(QueryBean query, String authObjName,
                                   BiConsumer<QueryBean, IServiceContext> prepareQuery,
                                   FieldSelectionBean selection, IServiceContext context) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static GraphQLConnection<Object> fetch(RecordingProcessor processor, int maxFetchSize,
                                                   Map<String, Object> args) {
        OrmEntityPropConnectionFetcher fetcher = new OrmEntityPropConnectionFetcher(
                processor, "TestObj", maxFetchSize, null, GraphQLQueryMethod.findConnection,
                io.nop.api.core.beans.FilterBeans.and(new ArrayList<>()), null);

        DataFetchingEnvironment env = new DataFetchingEnvironment();
        env.setExecutionContext(new GraphQLExecutionContext(new ServiceContextImpl()));
        env.setSource(new Object());
        FieldSelectionBean selection = new FieldSelectionBean();
        selection.setArgs(args);
        env.setSelectionBean(selection);

        return (GraphQLConnection<Object>) fetcher.get(env);
    }

    /** 带id属性的简单条目：getCursor按PROP_ID取游标 */
    public static final class Item {
        private final String id;

        public Item(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static Map<String, Object> queryArgs(int offset, int limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("offset", offset);
        query.put("limit", limit);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", query);
        return args;
    }

    /**
     * 参数经query:{offset,limit}对象传入、返回行数小于生效limit时，必须是最后一页。
     * 修复前比较用的是input.getLimit()（该路径下恒为0），data.size() < 0恒false，
     * hasNextPage恒为true——客户端按pageInfo翻页会无限追加空页请求。
     */
    @Test
    public void testQueryObjectPathHasNextPageUsesEffectiveLimit() {
        RecordingProcessor processor = new RecordingProcessor(3);

        GraphQLConnection<Object> conn = fetch(processor, 0, queryArgs(0, 5));

        GraphQLPageInfo pageInfo = conn.getPageInfo();
        assertFalse(pageInfo.getHasNextPage(),
                "3 rows returned with effective limit 5 must be the last page");
        assertFalse(pageInfo.getHasPreviousPage());
        assertEquals(5, processor.capturedQuery.get().getLimit());
    }

    /**
     * 返回行数达到生效limit时仍有下一页（对照语义）。
     */
    @Test
    public void testQueryObjectPathFullPageStillHasNext() {
        RecordingProcessor processor = new RecordingProcessor(5);

        GraphQLConnection<Object> conn = fetch(processor, 0, queryArgs(0, 5));

        assertTrue(conn.getPageInfo().getHasNextPage(), "full page may have a next page");
    }

    /**
     * 顶层limit参数路径（input.limit>0）不受影响。
     */
    @Test
    public void testTopLevelLimitPathHasNextPage() {
        RecordingProcessor processor = new RecordingProcessor(2);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("limit", 4);
        args.put("offset", 0);
        GraphQLConnection<Object> conn = fetch(processor, 0, args);

        assertFalse(conn.getPageInfo().getHasNextPage());
    }

    /**
     * first分页路径：fetchItems用first+1覆盖limit后必须再按maxSize钳制，
     * 字段级graphql:maxFetchSize=10不得被first:999绕过。
     * 修复前实际下发的limit为1000（first+1），字段级上限失效。
     */
    @Test
    public void testFirstPathClampedToMaxFetchSize() {
        RecordingProcessor processor = new RecordingProcessor(10);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("first", 999);
        GraphQLConnection<Object> conn = fetch(processor, 10, args);

        assertEquals(10, processor.capturedQuery.get().getLimit(),
                "first+1 must be clamped to the field-level maxFetchSize");
        assertFalse(conn.getPageInfo().getHasNextPage(), "10 rows fetched with first=999 clamped to 10: no next page");
    }

    /**
     * last分页路径同样受maxFetchSize钳制。
     */
    @Test
    public void testLastPathClampedToMaxFetchSize() {
        RecordingProcessor processor = new RecordingProcessor(10);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("last", 999);
        GraphQLConnection<Object> conn = fetch(processor, 10, args);

        assertEquals(10, processor.capturedQuery.get().getLimit(),
                "last+1 must be clamped to the field-level maxFetchSize");
    }

    /**
     * first小于上限时不受钳制影响（first+1照常探测下一页）。
     */
    @Test
    public void testFirstBelowLimitUnaffected() {
        RecordingProcessor processor = new RecordingProcessor(3);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("first", 3);
        GraphQLConnection<Object> conn = fetch(processor, 10, args);

        assertEquals(4, processor.capturedQuery.get().getLimit(), "first+1 probe limit preserved when below max");
        assertFalse(conn.getPageInfo().getHasNextPage());
    }
}
