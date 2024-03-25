package io.nop.record.codec.impl;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.bean.IBeanConstructor;
import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;
import java.util.List;

public class BeanBinaryCodec implements IFieldBinaryCodec {
    public static class PropCodec {
        private final String name;
        private final IFieldBinaryCodec codec;
        private final int length;
        private final Charset charset;

        private final IPropertySetter setter;

        private final IPropertyGetter getter;

        public PropCodec(String name, IPropertySetter setter, IPropertyGetter getter,
                         IFieldBinaryCodec codec, int length, Charset charset) {
            this.name = name;
            this.setter = setter;
            this.getter = getter;
            this.codec = codec;
            this.length = length;
            this.charset = charset;
        }
    }

    private final IBeanConstructor constructor;
    private final List<PropCodec> props;

    public BeanBinaryCodec(IBeanConstructor constructor,
                           List<PropCodec> props) {
        this.constructor = constructor;
        this.props = props;
    }

    @Override
    public Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context) {
        Object bean = constructor.newInstance();
        for (PropCodec prop : props) {
            Object value = prop.codec.decode(input, prop.length, prop.charset, context);
            prop.setter.setProperty(bean, prop.name, value, DisabledEvalScope.INSTANCE);
        }
        return bean;
    }

    @Override
    public void encode(IRecordBinaryOutput output, Object bean, int length, Charset charset, IFieldCodecContext context) {
        for (PropCodec prop : props) {
            Object value = prop.getter.getProperty(bean, prop.name, DisabledEvalScope.INSTANCE);
            prop.codec.encode(output, value, prop.length, prop.charset, context);
        }
    }
}
