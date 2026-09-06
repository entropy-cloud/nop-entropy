package io.nop.report.docx.renderer;

import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.handler.CollectXmlHandler;
import io.nop.excel.model.XptCellModel;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedTable;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.lang.reflect.Proxy;

import static io.nop.report.docx.ReportDocxConstants.VAR_XPT_NODE;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXptWordTableRenderer {

    static IXptRuntime mockXptRuntime(ExpandedCell cell) {
        return (IXptRuntime) Proxy.newProxyInstance(IXptRuntime.class.getClassLoader(),
                new Class[]{IXptRuntime.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getCell"))
                        return cell;
                    if (method.getName().equals("hashCode"))
                        return System.identityHashCode(proxy);
                    return null;
                });
    }

    /**
     * tc节点缺失w:p子节点（手工构造或损坏的模板）时不应抛NPE
     */
    @Test
    public void testRenderCellMissingParagraphNode() throws Exception {
        ExpandedTable table = new ExpandedTable(1, 1);
        ExpandedCell cell = table.getCell(0, 0);

        XptCellModel model = new XptCellModel();
        model.setName("A1");
        XNode tc = XNode.make("w:tc");
        // 没有w:p子节点
        tc.appendChild(XNode.make("w:tcPr"));
        model.prop_set(VAR_XPT_NODE, tc);
        cell.setModel(model);

        StringWriter writer = new StringWriter();
        CollectXmlHandler handler = new CollectXmlHandler(writer).indentRoot(false).indent(false);

        XptWordTableRenderer renderer = new XptWordTableRenderer(null);
        renderer.renderCell(cell, handler, mockXptRuntime(cell));

        String xml = writer.toString();
        assertTrue(xml.contains("w:tc"), xml);
        assertTrue(xml.contains("w:p"), "should render an empty paragraph node: " + xml);
    }
}
