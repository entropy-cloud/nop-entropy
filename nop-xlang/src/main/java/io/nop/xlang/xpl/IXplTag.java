/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.collections.IKeyedElement;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.hook.IExtensibleObject;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.utils.ExprEvalHelper;
import io.nop.xlang.xpl.utils.XplTagHelper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface IXplTag extends IExtensibleObject, ISourceLocationGetter, IKeyedElement {
    default String key() {
        return getTagName();
    }

    String getTagName();

    String getDisplayName();

    String getDescription();

    XLangOutputMode getOutputMode();

    IXplTagCompiler getTagCompiler();

    boolean isMacro();

    boolean isDeprecated();

    boolean isInternal();

    List<? extends IXplTagAttribute> getAttrs();

    default List<String> getAttrNames() {
        return getAttrs().stream().map(IXplTagAttribute::getName).collect(Collectors.toList());
    }

    IXplTagAttribute getAttr(String attrName);

    List<? extends IXplTagSlot> getSlots();

    IXplTagSlot getSlot(String slotName);

    IXplTagReturn getTagReturn();

    /**
     * 如果非空，则允许传入未声明的属性。所有的未知属性都会自动到一个Map类型的参数中。
     */
    String getUnknownAttrsVar();

    /**
     * 如果非空，则所有未知属性都会自动汇总到一个Map类型的参数中。同时每个属性也不再单独对应一个参数
     */
    String getAttrsVar();

    String getCallLocationVar();

    String getTagFuncName();

    IFunctionModel getFunctionModel();

//    @EvalMethod
//    default Map<String, Object> prepareArgs(IEvalScope scope, Map<String, Object> args) {
//        return XplTagHelper.prepareTagArgs(this, args, scope);
//    }

    @EvalMethod
    default Object invokeWithNamedArgs(IEvalScope scope, Map<String, Object> args) {
        ExecutableFunction fn = (ExecutableFunction) getFunctionModel().getInvoker();
        return fn.executeWithArgs(XLang.getExecutor(), XplTagHelper.buildTagArgValues(this, args, scope), new EvalRuntime(scope));
    }

    @EvalMethod
    default Object generateXjson(IEvalScope scope, Map<String, Object> args) {
        return ExprEvalHelper.generateXjson(ctx -> {
            ExecutableFunction fn = (ExecutableFunction) getFunctionModel().getInvoker();
            return fn.executeWithArgs(XLang.getExecutor(), XplTagHelper.buildTagArgValues(this, args, scope), ctx);
        }, new EvalRuntime(scope));
    }
}
