/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认过滤器运行时自动应用 helper（架构基线 §4.4.1/§4.4.2，收口 0700-2 Non-Blocking Follow-up）。
 *
 * <p>查询执行（单表/JOIN/聚合）前，自动注入该表 {@code isDefault=true} 的 {@link NopMetaTableFilter#getDefinition()}
 * （JSON TreeBean，与 §2.5.2 D1 同结构）到 filter 树——与用户 filter AND 合并。单表（0800-1）/JOIN/聚合（0800-2）共用本 helper。
 *
 * <p>无状态，方法为静态。
 */
public final class DefaultFilterApplicator {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultFilterApplicator.class);


    private DefaultFilterApplicator() {
    }

    /**
     * 合并该表的 isDefault 过滤器到用户 filter（AND 合并）。
     *
     * <p>多张 isDefault 过滤器之间也 AND 合并（首版代码强制 isDefault 唯一性见 0700-2，但本 helper 容忍多条以防数据脏）。
     * 若无 isDefault 过滤器或其 definition 为空，原样返回用户 filter（可能为 null）。
     *
     * @param table        目标逻辑表
     * @param userFilter   用户传入 filter（可为 null）
     * @param filterDao    NopMetaTableFilter DAO
     * @return 合并后的 filter（无 isDefault 时原样返回 userFilter）
     */
    public static TreeBean applyDefaults(NopMetaTable table, TreeBean userFilter, IEntityDao<NopMetaTableFilter> filterDao) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTableFilter.PROP_NAME_metaTableId, table.getMetaTableId()));
        // NOP 约定：byte 1 = yes/true，0 = no/false（isDefault 无独立常量，0700-2 同约定）
        q.addFilter(FilterBeans.eq(NopMetaTableFilter.PROP_NAME_isDefault, (byte) 1));
        List<NopMetaTableFilter> defaults = filterDao.findAllByQuery(q);
        if (defaults == null || defaults.isEmpty()) {
            return userFilter;
        }
        List<TreeBean> parts = new ArrayList<>();
        if (userFilter != null) {
            parts.add(userFilter);
        }
        for (NopMetaTableFilter f : defaults) {
            String def = f.getDefinition();
            if (def == null || def.trim().isEmpty()) {
                continue;
            }
            try {
                TreeBean tb = JsonTool.parseBeanFromText(def, TreeBean.class);
                if (tb != null) {
                    parts.add(tb);
                }
            } catch (Exception e) {
                // 解析失败显式抛错（不静默忽略脏过滤器，避免过滤被悄悄跳过导致越权数据返回）
                throw new NopMetadataException(NopMetadataErrors.ERR_DEFAULT_FILTER_PARSE, e)
                        .param("filterId", f.getFilterId())
                        .param("error", messageOf(e));
            }
        }
        if (parts.isEmpty()) {
            return userFilter;
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return FilterBeans.and(parts);
    }

    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }
}
