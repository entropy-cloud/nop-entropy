/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.model.compile;

import io.nop.core.resource.IResourceObjectLoader;
import io.nop.rule.core.excel.RuleExcelModelParser;
import io.nop.rule.core.model.RuleModel;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;

public class RuleExcelModelLoader implements IResourceObjectLoader<RuleModel> {
    @Override
    public RuleModel loadObjectFromPath(String path) {
        XLangCompileTool compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        RuleModel ruleModel = new RuleExcelModelParser(compileTool).parseFromVirtualPath(path);
        new RuleModelCompiler(compileTool).compileRule(ruleModel);
        return ruleModel;
    }
}