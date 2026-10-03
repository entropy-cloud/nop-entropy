/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao.dict;

import io.nop.api.core.annotations.ioc.IgnoreDepends;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.dict.DictProvider;
import io.nop.core.dict.IDictLoader;
import io.nop.core.dict.IDictProvider;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysDict;
import io.nop.sys.dao.entity.NopSysDictOption;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

public class SysDictLoader implements IDictLoader {
    static final Logger LOG = LoggerFactory.getLogger(SysDictLoader.class);

    @IgnoreDepends
    @Inject
    IDaoProvider daoProvider;

    @PostConstruct
    public void init() {
        IDictProvider dictProvider = DictProvider.instance();
        dictProvider.addDictLoader(NopSysDaoConstants.SYS_DICT_PREFIX, this);
    }

    @Override
    public boolean supportDict(String dictName) {
        return dictName.startsWith(NopSysDaoConstants.SYS_DICT_PREFIX);
    }

    @PreDestroy
    public void destroy() {
        IDictProvider dictProvider = DictProvider.instance();
        dictProvider.removeDictLoader(NopSysDaoConstants.SYS_DICT_PREFIX, this);
    }

    @Override
    public DictBean loadDict(String locale, String dictName, IEvalContext ctx) {
        IEntityDao<NopSysDictOption> dao = daoProvider.daoFor(NopSysDictOption.class);

        DictBean bean = new DictBean();
        // 回归覆盖 wi7#2（plan 2306 项 19）：不得忽略调用方传入的 locale。
        // 注：sys 字典表本身没有 locale 列，选项级多语言过滤需模型扩展，此处仅保证
        // 返回的 DictBean.locale 忠实反映请求
        bean.setLocale(locale != null ? locale : I18nMessageManager.instance().getDefaultLocale());
        bean.setName(dictName);

        QueryBean query = new QueryBean();
        query.setFilter(FilterBeans.eq(NopSysDictOption.PROP_NAME_dict + '.' + NopSysDict.PROP_NAME_dictName, dictName));
        query.addOrderField(NopSysDictOption.PROP_NAME_value, false);
        List<DictOptionBean> options = dao.findAllByQuery(query).stream().map(option -> {
            DictOptionBean opt = new DictOptionBean();
            opt.setLabel(option.getLabel());
            opt.setValue(option.getValue());
            opt.setDeprecated(StringHelper.isYes(option.getIsDeprecated()));
            opt.setInternal(StringHelper.isYes(option.getIsInternal()));
            return opt;
        }).collect(Collectors.toList());

        bean.setOptions(options);
        return bean;
    }

    @Override
    public boolean existsDict(String dictName) {
        IOrmEntityDao<NopSysDict> dao = (IOrmEntityDao<NopSysDict>) daoProvider.daoFor(NopSysDict.class);
        if (dao.getEntityModel().isUseTenant() && ContextProvider.currentTenantId() == null) {
            // 回归覆盖 wi7#3（plan 2306 项 36）：无租户上下文时无法执行租户过滤的 DB 检查，
            // 保持启动期乐观语义（返回 true），但必须显式记录而非静默放行——
            // 运行期调用命中此分支时日志可暴露误报风险
            LOG.warn("nop.sys.exists-dict-skip-tenant-check:dictName={}", dictName);
            return true;
        }

        QueryBean query = new QueryBean();
        query.setFilter(FilterBeans.eq(NopSysDict.PROP_NAME_dictName, dictName));
        return dao.existsByQuery(query);
    }
}