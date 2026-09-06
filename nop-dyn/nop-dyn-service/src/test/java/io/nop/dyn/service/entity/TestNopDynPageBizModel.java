/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dyn.dao.NopDynDaoConstants;
import io.nop.dyn.dao.entity.NopDynModule;
import io.nop.dyn.dao.entity.NopDynPage;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NopDynPageBizModel.getPage 的路径契约：按 getPagePath 的实际构造
 * （/{moduleId}/{pageGroup}/{pageName}.page.yaml，pageGroup 默认 "pages" 但可配置）解析与过滤，
 * 不能硬编码 "/pages/" 段且只认 ".page.json" 后缀
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopDynPageBizModel extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    NopDynPageBizModel pageBizModel;

    @Test
    public void testGetPageRespectsPageGroupAndYamlPostfix() {
        ormTemplate.runInSession(() -> {
            NopDynModule module = saveModule("app-demo");
            savePage(module, "main", "pages", "{\"type\":\"page\",\"title\":\"default-group\"}");
            savePage(module, "main", "custom", "{\"type\":\"page\",\"title\":\"custom-group\"}");

            // 修复前：getPage硬编码匹配"/pages/"段，custom分组的页面无法经该API访问
            Map<String, Object> page = pageBizModel.getPage("/app/demo/custom/main.page.json", new ServiceContextImpl());
            assertEquals("custom-group", page.get("title"), "非默认pageGroup分组的页面必须可按路径访问");

            // getPagePath生成的是.page.yaml后缀，getPage必须兼容该契约
            Map<String, Object> defaultPage = pageBizModel.getPage("/app/demo/pages/main.page.yaml", new ServiceContextImpl());
            assertEquals("default-group", defaultPage.get("title"), "pageGroup过滤必须生效，同名页面不能串组");

            // 既有.page.json路径行为保持不变
            Map<String, Object> jsonPage = pageBizModel.getPage("/app/demo/pages/main.page.json", new ServiceContextImpl());
            assertEquals("default-group", jsonPage.get("title"));
        });
    }

    private NopDynModule saveModule(String moduleName) {
        NopDynModule module = new NopDynModule();
        module.setModuleName(moduleName);
        module.setDisplayName("Demo Module");
        module.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);
        daoProvider.daoFor(NopDynModule.class).saveEntity(module);
        // 同session内的查询需要先flush才能看到新增数据
        daoProvider.daoFor(NopDynModule.class).flushSession();
        return module;
    }

    private void savePage(NopDynModule module, String pageName, String pageGroup, String content) {
        IEntityDao<NopDynPage> dao = daoProvider.daoFor(NopDynPage.class);
        NopDynPage page = dao.newEntity();
        page.setModule(module);
        page.setPageName(pageName);
        page.setPageGroup(pageGroup);
        page.setPageContent(content);
        page.setPageSchemaType("amis");
        page.setStatus(1);
        dao.saveEntity(page);
        dao.flushSession();
    }
}
