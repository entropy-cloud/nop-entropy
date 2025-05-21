package io.nop.pdf.extract.table;

import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.TableBlock;

/**
 * 表格合并
 *
 */
public interface ITableMerger {

    TableBlock merge( ResourceDocument doc, TableBlock table1, TableBlock table2 );
}
