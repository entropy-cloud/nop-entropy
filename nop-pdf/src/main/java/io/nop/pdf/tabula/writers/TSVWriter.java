package io.nop.pdf.tabula.writers;

import org.apache.commons.csv.CSVFormat;

public class TSVWriter extends CSVWriter {

    public TSVWriter() {
        super(CSVFormat.TDF);
    }

}
