/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.common.gen;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.ITableView;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static io.nop.ooxml.common.OfficeConstants.NS_EXT_PREFIX;
import static io.nop.ooxml.common.OfficeErrors.ARG_ALLOWED_NAMES;
import static io.nop.ooxml.common.OfficeErrors.ARG_CELL_POS;
import static io.nop.ooxml.common.OfficeErrors.ARG_NAME;
import static io.nop.ooxml.common.OfficeErrors.ERR_EXPORT_INVALID_CONFIG_NAME;

class XplGenConfigParser {
    static final String VAR_IMPORT_LIBS = "importLibs";
    static final String VAR_BEFORE_GEN = "beforeGen";
    static final String VAR_AFTER_GEN = "afterGen";
    static final String VAR_DUMP = "dump";
    static final String VAR_DUMP_FILE = "dumpFile";

    static final String VAR_DELETE_ALL_AFTER_CONFIG_TABLE = "deleteAllAfterConfigTable";

    static final List<String> ALL_VAR_NAMES = Arrays.asList(VAR_DUMP, VAR_IMPORT_LIBS, VAR_BEFORE_GEN, VAR_AFTER_GEN,
            VAR_DELETE_ALL_AFTER_CONFIG_TABLE);

    public XplGenConfig parseFromTable(ITableView table) {
        XplGenConfig ret = new XplGenConfig();

        // 跳过第一行。第一行为标题行
        for (int i = 1, n = table.getRowCount(); i < n; i++) {
            ICellView cell = table.getCell(i, 0);
            if (cell == null || cell.isBlankCell())
                continue;

            String text = cell.getText();
            String valueText = table.getCellText(i, cell.getColSpan());

            if (VAR_DUMP.equals(text)) {
                if (valueText != null)
                    valueText = valueText.toLowerCase(Locale.ROOT);
                Boolean b = ConvertHelper.toBoolean(valueText);
                if (b != null)
                    ret.setDump(b);
            } else if (VAR_IMPORT_LIBS.equals(text)) {
                ret.setImportLibs(parseImportLibs(valueText));
            } else if (VAR_BEFORE_GEN.equals(text)) {
                ret.setBeforeGen(parseXpl(valueText));
            } else if (VAR_AFTER_GEN.equals(text)) {
                ret.setAfterGen(parseXpl(valueText));
            } else if (VAR_DUMP_FILE.equals(text)) {
                ret.setDumpFile(valueText);
            } else if (VAR_DELETE_ALL_AFTER_CONFIG_TABLE.equals(text)) {
                if (valueText != null)
                    valueText = valueText.toLowerCase(Locale.ROOT);
                Boolean b = ConvertHelper.toBoolean(valueText);
                if (b != null)
                    ret.setDeleteAllAfterConfigTable(true);
            } else if (text.startsWith(NS_EXT_PREFIX)) {
                // 允许扩展属性
                ret.setExtProp(text, valueText);
            } else {
                throw new NopException(ERR_EXPORT_INVALID_CONFIG_NAME).param(ARG_NAME, text)
                        .param(ARG_CELL_POS, CellPosition.of(i, 0).toABString())
                        .param(ARG_ALLOWED_NAMES, ALL_VAR_NAMES);
            }
        }
        return ret;
    }

    List<String> parseImportLibs(String text) {
        List<String> list = StringHelper.stripedSplit(text, ',');
        return list;
    }

    XNode parseXpl(String text) {
        if (StringHelper.isBlank(text))
            return null;
        XNode node = XNodeParser.instance().forFragments(true).parseFromText(null, text);
        node.setTagName("c:unit");
        node.setAttr("xpl:outputMode", "none");
        return node;
    }
}