package io.nop.pdf.extract;

import java.util.List;

import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.ShapeBlock;

/**
 * 特殊用途线条检测接口
 */
public interface ILineDetector {

    List<ShapeBlock> detect( ResourcePage page );
}
