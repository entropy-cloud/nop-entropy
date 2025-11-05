package io.nop.pdf.extract.table;

import java.util.List;

import io.nop.pdf.extract.struct.ShapeBlock;
import io.nop.pdf.extract.struct.TableBlock;

public interface TableDetector {

    /**
     * 扫描表格结构
     * 
     * @param shapeBlocks
     * @return
     */
    public List<TableBlock> process( List<ShapeBlock> shapeBlocks );
}
