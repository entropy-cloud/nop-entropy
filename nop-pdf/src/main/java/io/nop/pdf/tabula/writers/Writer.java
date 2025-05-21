package io.nop.pdf.tabula.writers;

import java.io.IOException;
import java.util.List;

import io.nop.pdf.tabula.Table;

public interface Writer {

    void write(Appendable out, Table table) throws IOException;

    void write(Appendable out, List<Table> tables) throws IOException;

}
