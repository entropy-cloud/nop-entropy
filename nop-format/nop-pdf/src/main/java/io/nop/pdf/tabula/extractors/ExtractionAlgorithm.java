package io.nop.pdf.tabula.extractors;

import java.util.List;

import io.nop.pdf.tabula.Page;
import io.nop.pdf.tabula.Table;

public interface ExtractionAlgorithm {

    List<? extends Table> extract(Page page);
    String toString();
    
}
