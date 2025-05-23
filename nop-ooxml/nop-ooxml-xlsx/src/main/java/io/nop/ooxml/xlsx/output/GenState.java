package io.nop.ooxml.xlsx.output;

import io.nop.commons.bytes.ByteString;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;

import java.util.HashMap;
import java.util.Map;

public class GenState {
    public final ExcelOfficePackage pkg;
    public int nextSheetIndex;
    public Map<ByteString, String> images = new HashMap<>();
    public int nextImageIndex;

    public int nextDrawingIndex;

    public GenState(ExcelOfficePackage pkg) {
        this.pkg = pkg;
    }

    public int genSheetIndex() {
        return nextSheetIndex++;
    }
}
