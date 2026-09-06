/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.api;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCrudApiPageIterator {

    /**
     * 只实现findPage的桩后端：每次返回恰好pageSize条、hasNext为null（未填）、nextCursor为null，
     * 模拟不支持游标契约的后端实现。findPage调用超过maxFetches次时抛AssertionError，
     * 使"无限重复拉取首页"以测试失败的形式暴露，而不是挂死测试进程。
     */
    static class NoCursorCrudApi implements ICrudApi<String, String> {
        final int pageSize;
        final int maxFetches;
        int fetchCount;

        NoCursorCrudApi(int pageSize, int maxFetches) {
            this.pageSize = pageSize;
            this.maxFetches = maxFetches;
        }

        @Override
        public PageBean<String> findPage(QueryBean query, FieldSelectionBean selection, ICancelToken cancelToken) {
            fetchCount++;
            if (fetchCount > maxFetches)
                throw new AssertionError("infinite fetch loop: findPage called " + fetchCount + " times");
            List<String> items = new ArrayList<>(pageSize);
            for (int i = 0; i < pageSize; i++) {
                items.add("item-" + fetchCount + "-" + i);
            }
            PageBean<String> page = new PageBean<>();
            page.setItems(items);
            page.setTotal(pageSize);
            // hasNext/nextCursor均不填
            return page;
        }

        @Override
        public long findCount(QueryBean query, ICancelToken cancelToken) {
            return 0;
        }

        @Override
        public String findFirst(QueryBean query, FieldSelectionBean selection, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public List<String> findList(QueryBean query, FieldSelectionBean selection, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public String get(String id, boolean ignoreUnknown, FieldSelectionBean selection, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public List<String> batchGet(Collection<String> ids, boolean ignoreUnknown,
                                     FieldSelectionBean selection, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public DictBean asDict(ICancelToken cancelToken) {
            return null;
        }

        @Override
        public String save(String data, FieldSelectionBean selection, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public String saveOrUpdate(String data, FieldSelectionBean selection, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public String copyForNew(String data, FieldSelectionBean selection, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public String update(String data, FieldSelectionBean selection, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public void batchUpdate(Set<String> ids, String data, boolean ignoreUnknown, ICancelToken cancelToken) {
        }

        @Override
        public int updateByQuery(QueryBean query, String data, ICancelToken cancelToken) {
            return 0;
        }

        @Override
        public boolean delete(String id, ICancelToken cancelToken) {
            return false;
        }

        @Override
        public Set<String> batchDelete(Set<String> ids, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public void batchModify(List<String> data, String common, Set<String> delIds, ICancelToken cancelToken) {
        }

        @Override
        public int deleteByQuery(QueryBean query, ICancelToken cancelToken) {
            return 0;
        }

        @Override
        public void addManyToManyRelations(String id, String propName, Collection<String> relValues,
                                           TreeBean filter, ICancelToken cancelToken) {
        }

        @Override
        public void removeManyToManyRelations(String id, String propName, Collection<String> relValues,
                                              TreeBean filter, ICancelToken cancelToken) {
        }

        @Override
        public void updateManyToManyRelations(String id, String propName, Collection<String> relValues,
                                              TreeBean filter, ICancelToken cancelToken) {
        }

        @Override
        public PageBean<String> deleted_findPage(QueryBean query, FieldSelectionBean selection,
                                                 ICancelToken cancelToken) {
            return null;
        }

        @Override
        public String deleted_get(String id, boolean ignoreUnknown, FieldSelectionBean selection,
                                  ICancelToken cancelToken) {
            return null;
        }

        @Override
        public String recoverDeleted(String id, FieldSelectionBean selection, ICancelToken cancelToken) {
            return null;
        }
    }

    /**
     * 支持游标契约的桩后端：返回pageSize条且nextCursor推进，最后一页hasNext=FALSE。
     */
    static class CursorCrudApi extends NoCursorCrudApi {
        final int totalPages;
        int page = 0;

        CursorCrudApi(int pageSize, int totalPages) {
            super(pageSize, Integer.MAX_VALUE);
            this.totalPages = totalPages;
        }

        @Override
        public PageBean<String> findPage(QueryBean query, FieldSelectionBean selection, ICancelToken cancelToken) {
            fetchCount++;
            page++;
            List<String> items = new ArrayList<>(pageSize);
            for (int i = 0; i < pageSize; i++) {
                items.add("page-" + page + "-" + i);
            }
            PageBean<String> p = new PageBean<>();
            p.setItems(items);
            p.setTotal((long) pageSize * totalPages);
            if (page < totalPages) {
                p.setHasNext(true);
                p.setNextCursor("cursor-" + page);
            } else {
                p.setHasNext(false);
                p.setNextCursor(null);
            }
            return p;
        }
    }

    /**
     * 回归：后端不返回游标（且不填hasNext）时，迭代器必须终止，不能无限重复拉取同一页。
     */
    @Test
    public void testStopsWhenBackendReturnsNoCursor() {
        NoCursorCrudApi api = new NoCursorCrudApi(2, 3);
        CrudApiPageIterator<String> iterator = new CrudApiPageIterator<>(api, null, null, null, 2);

        assertTrue(iterator.hasNext());
        List<String> batch = iterator.next();
        assertEquals(2, batch.size());

        // 无游标可用，不能再继续拉取
        assertFalse(iterator.hasNext(), "iterator must stop when backend provides no cursor");
        assertFalse(iterator.hasNext());
        assertEquals(1, api.fetchCount, "findPage must be invoked exactly once");
    }

    /**
     * 游标契约正常时多页迭代行为不受影响。
     */
    @Test
    public void testIteratesAllPagesWithCursor() {
        CursorCrudApi api = new CursorCrudApi(2, 3);
        CrudApiPageIterator<String> iterator = new CrudApiPageIterator<>(api, null, null, null, 2);

        int batches = 0;
        while (iterator.hasNext()) {
            List<String> batch = iterator.next();
            assertEquals(2, batch.size());
            batches++;
        }
        assertEquals(3, batches);
        assertEquals(3, api.fetchCount);
    }
}
