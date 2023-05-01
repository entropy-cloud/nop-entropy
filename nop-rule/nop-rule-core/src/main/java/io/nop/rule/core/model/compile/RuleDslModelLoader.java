package io.nop.rule.core.model.compile;

import io.nop.core.resource.IResourceObjectLoader;
import io.nop.rule.core.model.RuleModel;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdsl.DslModelParser;

public class RuleDslModelLoader implements IResourceObjectLoader<RuleModel> {

    @Override
    public RuleModel loadObjectFromPath(String path) {
        XLangCompileTool compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        RuleModel ruleModel = (RuleModel) new DslModelParser().parseFromVirtualPath(path);
        new RuleModelCompiler(compileTool).compileRule(ruleModel);
        return ruleModel;
    }
}