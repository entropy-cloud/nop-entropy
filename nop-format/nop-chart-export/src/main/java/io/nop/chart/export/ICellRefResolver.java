package io.nop.chart.export;

import java.util.List;

/**
 * Interface for resolving cell references to actual data values
 */
public interface ICellRefResolver {
    
    /**
     * 根据单元格引用获取单个值
     * @param cellRef 单元格引用，如 "A1", "B2" 等
     * @return 单元格的值，可能为null
     */
    Object getValue(String cellRef);
    
    /**
     * 根据单元格区域引用获取值列表
     * @param cellRangeRef 单元格区域引用，如 "A1:A10", "B2:D5" 等
     * @return 值列表，不会为null但可能为空
     */
    List<Object> getValues(String cellRangeRef);
    
    /**
     * 检查单元格引用是否有效
     * @param cellRef 单元格引用
     * @return true如果引用格式有效
     */
    boolean isValidRef(String cellRef);
}