package io.nop.core.lang.eval.functions;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArrayAdapterFunction implements IEvalFunction {
    private final IEvalFunction func;

    public ArrayAdapterFunction(IEvalFunction func) {
        this.func = func;
    }

    List<Object> toList(Object thisObj) {
        Class<?> clazz = thisObj.getClass();
        if (!clazz.getComponentType().isPrimitive())
            return Arrays.asList((Object[]) thisObj);

        int length = Array.getLength(thisObj);
        List<Object> ret = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            ret.add(Array.get(thisObj, i));
        }
        return ret;
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return func.invoke(toList(thisObj), args, scope);
    }

    @Override
    public Object call0(Object thisObj, IEvalScope scope) {
        return func.call0(toList(thisObj), scope);
    }

    @Override
    public Object call1(Object thisObj, Object arg, IEvalScope scope) {
        return func.call1(toList(thisObj), arg, scope);
    }

    @Override
    public Object call2(Object thisObj, Object arg1, Object arg2, IEvalScope scope) {
        return func.call2(toList(thisObj), arg1, arg2, scope);
    }

    @Override
    public Object call3(Object thisObj, Object arg1, Object arg2, Object arg3, IEvalScope scope) {
        return func.call3(toList(thisObj), arg1, arg2, arg3, scope);
    }

    @Override
    public IEvalFunction bind(Object thisObj) {
        return func.bind(toList(thisObj));
    }
}
