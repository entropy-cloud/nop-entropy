package io.nop.xlang.janino;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.impl.EvalMethodInvoker;
import io.nop.core.reflect.impl.MethodInvoker;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.script.IScriptCompiler;
import io.nop.xlang.script.ScriptCompilerRegistry;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ScriptEvaluator;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_ERR_MSG;
import static io.nop.xlang.XLangErrors.ERR_SCRIPT_COMPILE_ERROR;
import static io.nop.xlang.expr.ExprConstants.SYS_VAR_SCOPE;

public class JaninoScriptCompiler implements IScriptCompiler {
    static final String LANG_JAVA = "java";

    static final JaninoScriptCompiler INSTANCE = new JaninoScriptCompiler();

    public static void register() {
        ScriptCompilerRegistry.instance().registerCompiler(LANG_JAVA, INSTANCE);
    }

    public static void unregister() {
        ScriptCompilerRegistry.instance().unregisterCompiler(LANG_JAVA, INSTANCE);
    }

    @Override
    public IEvalFunction compile(SourceLocation loc, String text,
                                 List<? extends IFunctionArgument> args,
                                 IGenericType returnType,
                                 IXLangCompileScope scope) {
        if (args == null)
            args = Collections.emptyList();
        if (returnType == null)
            returnType = PredefinedGenericTypes.VOID_TYPE;

        ScriptEvaluator evaluator = new ScriptEvaluator();
        evaluator.setReturnType(returnType.getRawClass());
        evaluator.setStaticMethod(true);
        int n = args.size() + 1;
        String[] paramNames = new String[n];
        Class<?>[] paramTypes = new Class[n];
        // 确保第一个参数总是scope，使得方法满足EvalMethod格式要求。
        paramNames[0] = SYS_VAR_SCOPE;
        paramTypes[0] = IEvalScope.class;

        for (int i = 1; i < n; i++) {
            paramNames[i] = args.get(i - 1).getName();
            paramTypes[i] = args.get(i - 1).getRawClass();
        }
        evaluator.setParameters(paramNames, paramTypes);
        try {
            String code = buildCode(text, returnType);
            evaluator.cook("JaninoScript.java", code);
            Method method = evaluator.getMethod();
            IEvalFunction invoker = new MethodInvoker(method);
            return new EvalMethodInvoker(invoker);
        } catch (CompileException e) {
            SourceLocation errorLoc = loc == null ? null :
                    loc.offset(e.getLocation().getLineNumber() - 1, e.getLocation().getColumnNumber());
            String errMsg = e.getMessage();
            throw new NopException(ERR_SCRIPT_COMPILE_ERROR, e).loc(errorLoc)
                    .param(ARG_ERR_MSG, errMsg);
        }
    }

    private String buildCode(String text, IGenericType returnType) {
        StringBuilder sb = new StringBuilder();
//        sb.append(returnType.getTypeName());
//        sb.append(' ').append("execute(");
//        for (int i = 0, n = args.size(); i < n; i++) {
//            IFunctionArgument arg = args.get(i);
//            sb.append(arg.getType().getTypeName()).append(' ').append(arg.getName());
//            if (i != n - 1)
//                sb.append(',');
//        }
//        sb.append("){\n");
        sb.append(text);
        if (returnType != PredefinedGenericTypes.VOID_TYPE) {
            if (!text.contains("return "))
                sb.append("\n return null;");
        }
        //  sb.append("\n}");
        return sb.toString();
    }
}
