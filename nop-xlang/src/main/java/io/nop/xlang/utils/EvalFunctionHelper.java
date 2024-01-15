package io.nop.xlang.utils;

import io.nop.core.lang.eval.IEvalFunction;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class EvalFunctionHelper {
    public static IEvalFunction toEvalFunction(Object value) {
        if (value instanceof IEvalFunction)
            return (IEvalFunction) value;

        if (value instanceof Function)
            return (thisObj, args, scope) -> {
                Object arg = args.length > 0 ? args[0] : null;
                return ((Function) value).apply(arg);
            };
        
        if (value instanceof BiFunction) {
            return (thisObj, args, scope) -> {
                Object arg0 = args.length > 0 ? args[0] : null;
                Object arg1 = args.length > 1 ? args[1] : null;
                return ((BiFunction) value).apply(arg0, arg1);
            };
        }

        if (value instanceof Consumer) {
            return (thisObj, args, scope) -> {
                Object arg = args.length > 0 ? args[0] : null;
                ((Consumer) value).accept(arg);
                return null;
            };
        }

        if (value instanceof BiConsumer) {
            return (thisObj, args, scope) -> {
                Object arg0 = args.length > 0 ? args[0] : null;
                Object arg1 = args.length > 1 ? args[1] : null;
                ((BiConsumer) value).accept(arg0, arg1);
                return null;
            };
        }
        return null;
    }
}
