package io.nop.dataset.binder;

import io.nop.commons.type.StdDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDataParameterBinders {

    static final class ArrayDataParameters implements IDataParameters {
        private final Object[] values;

        ArrayDataParameters(int size) {
            this.values = new Object[size];
        }

        @Override
        public Object getObject(int index) {
            return values[index];
        }

        @Override
        public void setObject(int index, Object value) {
            values[index] = value;
        }
    }

    @Test
    public void testFloatBinderUsesDoubleSemantics() {
        // StdSqlType.FLOAT 声明的 StdDataType 为 DOUBLE，binder 的读写必须是 Double 语义。
        // DialectImpl.getDataParameterBinder 在 stdType == sqlType.getStdDataType()（DOUBLE==DOUBLE）时
        // 直接返回此 binder，不包 AutoConvertDataParameterBinder，因此 binder 自身必须与声明一致
        assertEquals(StdDataType.DOUBLE, DataParameterBinders.FLOAT.getStdDataType());

        ArrayDataParameters params = new ArrayDataParameters(1);

        // 修复前：setValue 强转 (Float)，传入 Double（EQL/ORM 对 FLOAT 列的参数语义）抛 ClassCastException
        DataParameterBinders.FLOAT.setValue(params, 0, 2.5d);
        assertEquals(Double.valueOf(2.5d), params.getObject(0));

        // 修复前：getFloat 把底层 Double 静默降级为 float 精度，与声明的 DOUBLE 不符
        Object value = DataParameterBinders.FLOAT.getValue(params, 0);
        assertEquals(Double.class, value.getClass());
        assertEquals(Double.valueOf(2.5d), value);
    }

    @Test
    public void testRealBinderKeepsFloatSemantics() {
        // REAL 声明 StdDataType.FLOAT，读写 Float 语义自洽，保持不变
        assertEquals(StdDataType.FLOAT, DataParameterBinders.REAL.getStdDataType());

        ArrayDataParameters params = new ArrayDataParameters(1);
        DataParameterBinders.REAL.setValue(params, 0, 1.5f);
        assertEquals(Float.valueOf(1.5f), DataParameterBinders.REAL.getValue(params, 0));
    }
}
