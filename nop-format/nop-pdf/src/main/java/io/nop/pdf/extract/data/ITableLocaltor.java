package io.nop.pdf.extract.data;

import java.util.List;

import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.TableBlock;

/**
 * 在文档中定位表格的接口
 *
 */
public interface ITableLocaltor {

    /**
     * 定位表格
     * @param doc
     * @return
     */
    List<TableBlock> locateTables( ResourceDocument doc );
}
