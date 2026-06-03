/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.api;

import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.std.StdTreeEntity;
import io.nop.api.core.util.ICancelToken;

import java.util.List;

/**
 * 树形结构操作接口，用于具有父子层级关系的实体。
 *
 * @param <O> 输出类型（如 XxxOutputBean）
 */
public interface ICrudTreeApi<O> {

    @BizQuery
    List<O> findRoots(@Optional @Name("query") QueryBean query,
                      @Optional @Name("selection") FieldSelectionBean selection,
                      ICancelToken cancelToken);

    default List<O> findRoots(@Optional @Name("query") QueryBean query) {
        return findRoots(query, null, null);
    }

    default List<O> findRoots(@Optional @Name("query") QueryBean query,
                              @Optional @Name("selection") FieldSelectionBean selection) {
        return findRoots(query, selection, null);
    }

    @BizQuery
    PageBean<StdTreeEntity> findTreeEntityPage(@Optional @Name("query") QueryBean query,
                                               @Optional @Name("selection") FieldSelectionBean selection,
                                               ICancelToken cancelToken);

    default PageBean<StdTreeEntity> findTreeEntityPage(@Optional @Name("query") QueryBean query) {
        return findTreeEntityPage(query, null, null);
    }

    default PageBean<StdTreeEntity> findTreeEntityPage(@Optional @Name("query") QueryBean query,
                                                       @Optional @Name("selection") FieldSelectionBean selection) {
        return findTreeEntityPage(query, selection, null);
    }

    @BizQuery
    List<StdTreeEntity> findTreeEntityList(@Optional @Name("query") QueryBean query,
                                           @Optional @Name("selection") FieldSelectionBean selection,
                                           ICancelToken cancelToken);

    default List<StdTreeEntity> findTreeEntityList(@Optional @Name("query") QueryBean query) {
        return findTreeEntityList(query, null, null);
    }

    default List<StdTreeEntity> findTreeEntityList(@Optional @Name("query") QueryBean query,
                                                   @Optional @Name("selection") FieldSelectionBean selection) {
        return findTreeEntityList(query, selection, null);
    }
}
