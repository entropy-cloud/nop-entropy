package io.nop.pdf.extract.export;

import io.nop.pdf.extract.struct.Block;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;
import io.nop.pdf.extract.struct.TextlineBlock;
import io.nop.commons.util.StringHelper;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class ResourceDocumentTxtExporter extends
        AbstractResourceDocumentExporter {

    @Override
    public void exportToWriter(ResourceDocument doc, Writer writer,
                               String encoding) throws IOException {
        for (ResourcePage page : doc.getPages()) {

            List<Block> blocks = page.getSortedBlocks();
            for (Block block : blocks) {
                if (block instanceof TextlineBlock) {
                    TextlineBlock textline = (TextlineBlock) block;
                    writer.write(textline.getContent());
                    writer.write("\r\n");
                } else if (block instanceof TableBlock) {
                    TableBlock table = (TableBlock) block;
                    writeTable(writer, table);
                }
            }
        }
    }

    void writeTable(Writer writer, TableBlock table) throws IOException {
        writer.write("\r\n-----------------------------------------------------");
        for (int p = 0; p < table.getRowCount(); p++) {

            writer.write("\r\n|");
            for (int q = 0; q < table.getColCount(); q++) {

                TableCellBlock cell = table.getCell(p, q);
                if (cell == null)
                    continue;

                String celltext = cell.content;
                if (celltext != null) {
                    celltext = StringHelper.replace(celltext, "\r\n", " ");
                    celltext = StringHelper.replace(celltext, "\n", " ");
                    writer.write(celltext);
                }
                writer.write("|");
            }
            writer.write("\r\n");
        }
        writer.write("\r\n----------------------------------------------------\r\n");
    }
}