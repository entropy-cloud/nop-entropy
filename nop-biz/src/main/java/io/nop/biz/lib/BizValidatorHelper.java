package io.nop.biz.lib;

import io.nop.biz.BizConstants;
import io.nop.biz.crud.BizFilterEvaluator;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.validator.ModelBasedValidator;
import io.nop.core.model.validator.ValidatorModel;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xmeta.SchemaLoader;

public class BizValidatorHelper {
    public static ValidatorModel parseValidator(XNode node, IXLangCompileScope scope) {
        IXDefinition xdef = SchemaLoader.loadXDefinition(XLangConstants.XDSL_SCHEMA_VALIDATOR);
        DslModelParser parser = new DslModelParser();
        parser.setCompileTool(new XLangCompileTool(scope));
        ValidatorModel model = (ValidatorModel) parser.parseWithXDef(xdef, node.cloneInstance());
        return model;
    }

    public static void runValidator(ValidatorModel model, IServiceContext context) {
        String checkLibPath = model.getCheckLibPath();
        if (checkLibPath == null)
            checkLibPath = BizConstants.XLIB_BIZ_CHECK_PATH;
        new ModelBasedValidator(model, new BizFilterEvaluator(checkLibPath, context))
                .validateWithDefaultCollector(context.getEvalScope(), model.getFatalSeverity());
    }
}
