package io.nop.pdf.extract.dashline;

import java.util.List;

import io.nop.pdf.extract.struct.ImageBlock;
import io.nop.pdf.extract.struct.ShapeBlock;

/**
 * 虚线检测
 */
public interface DashlineDetector {

    /**
     * 从图像检测虚线
     * 
     * @param blocks
     * @return
     */
    public List<ShapeBlock> process( List<ImageBlock> blocks );
}
