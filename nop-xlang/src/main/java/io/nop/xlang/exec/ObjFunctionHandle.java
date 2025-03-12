package io.nop.xlang.exec;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.functions.ArrayAdapterFunction;
import io.nop.core.reflect.IMethodModelCollection;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.hook.IMethodMissingHook;
import io.nop.core.reflect.hook.MethodMissingHookFunction;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static io.nop.xlang.XLangErrors.ARG_ARG_COUNT;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_FUNC_NAME;
import static io.nop.xlang.XLangErrors.ARG_METHOD_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CLASS_NO_STATIC_METHOD;
import static io.nop.xlang.XLangErrors.ERR_EXEC_NO_OBJ_METHOD;

public class ObjFunctionHandle {
    static final Set<String> ARRAY_FUNCTIONS = Set.of("map", "flatMap", "forEach", "filter",
            "reduce", "reduceRight", "some", "every", "find", "findIndex", "indexOf", "lastIndexOf",
            "includes", "join", "concat", "reverse", "toString", "toLocaleString");

    private transient Pair<Class<?>, IEvalFunction> _cacheFunc = Pair.of(null, null);

    private transient Pair<Class<?>, IEvalFunction> _cacheStaticFunc = Pair.of(null, null);

    public IEvalFunction getFunction(Class<?> clazz, String funcName, Object... argValues) {
        IEvalFunction func;
        Pair<Class<?>, IEvalFunction> pair = _cacheFunc;
        if (pair.getLeft() == clazz) {
            func = pair.getRight();
        } else {
            if (clazz.isArray() && ARRAY_FUNCTIONS.contains(funcName)) {
                func = ReflectionManager.instance().getClassModel(List.class).getMethod(funcName, argValues.length);
                if (func != null) {
                    func = new ArrayAdapterFunction(func);
                    _cacheFunc = Pair.of(clazz, func);
                    return func;
                } else {
                    return null;
                }
            }

            IMethodModelCollection coll = ReflectionManager.instance().getClassModel(clazz).getMethodsByName(funcName);
            if (coll == null) {
                if (IMethodMissingHook.class.isAssignableFrom(clazz)) {
                    func = new MethodMissingHookFunction(funcName);
                } else {
                    return null;
                }
            } else {
                func = coll.getUniqueMethod(argValues.length);
            }
            if (func == null) {
                func = coll.getMethodForArgValues(argValues);
            } else {
                _cacheFunc = Pair.of(clazz, func);
            }
        }
        return func;
    }

    public IEvalFunction getStaticFunction(Class<?> clazz, String funcName, Object... argValues) {
        IEvalFunction func;
        Pair<Class<?>, IEvalFunction> pair = _cacheStaticFunc;
        if (pair.getLeft() == clazz) {
            func = pair.getRight();
        } else {
            IMethodModelCollection coll = ReflectionManager.instance().getClassModel(clazz)
                    .getStaticMethodsByName(funcName);
            if (coll == null) {
                return null;
            }
            func = coll.getUniqueMethod(argValues.length);
            if (func == null) {
                func = coll.getMethodForArgValues(argValues);
            } else {
                _cacheStaticFunc = Pair.of(clazz, func);
            }
        }
        return func;
    }

    public IEvalFunction getFunctionForObj(Object obj, String funcName, Function<ErrorCode, NopException> errorFactory,
                                           Object... argValues) {
        Class<?> clazz = obj.getClass();
        if (clazz == Class.class) {
            IEvalFunction fn = getStaticFunction((Class<?>) obj, funcName, argValues);
            if (fn == null) {
                throw errorFactory.apply(ERR_EXEC_CLASS_NO_STATIC_METHOD).param(ARG_CLASS_NAME, ((Class<?>) obj).getName())
                        .param(ARG_ARG_COUNT, argValues.length).param(ARG_METHOD_NAME, funcName);
            }
            return fn;
        }
        IEvalFunction fn = getFunction(clazz, funcName, argValues);
        if (fn == null) {
            throw errorFactory.apply(ERR_EXEC_NO_OBJ_METHOD).param(ARG_CLASS_NAME, clazz.getName())
                    .param(ARG_ARG_COUNT, argValues.length).param(ARG_FUNC_NAME, funcName);
        }
        return fn;
    }
}
