package io.nop.pdf.tabula.detectors;

import io.nop.pdf.tabula.Page;
import io.nop.pdf.tabula.Rectangle;

import java.util.List;

/**
 * Created by matt on 2015-12-14.
 */
public interface DetectionAlgorithm {
    List<Rectangle> detect(Page page);
}
