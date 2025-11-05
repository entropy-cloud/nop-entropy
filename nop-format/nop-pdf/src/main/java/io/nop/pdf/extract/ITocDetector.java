package io.nop.pdf.extract;

import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.TocTable;

/**
 * 目录检测接口
 *
 */
public interface ITocDetector {

    public TocTable detect( ResourceDocument doc );
}
