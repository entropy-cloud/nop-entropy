/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.layout;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.CommonErrors;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xmeta.layout.parse.LayoutModelParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestLayoutModelParser extends BaseTestCase {
    @Test
    public void testParse() {
        String layout = " !a @b \n ==^[display name]== \n a[xx] \n-----\n *c \n v \n ====uu===\n ==>#sub[User Info]== \nd(2)";
        LayoutModel model = parse(layout);
        System.out.println(model.toString());
        System.out.println(JsonTool.serialize(model, true));
        assertEquals(attachmentJsonText("layout_1.json"), JsonTool.serialize(model, true));
        String str = model.toString().trim();
        System.out.println(str);
        assertEquals(normalizeCRLF(attachmentText("layout_1.txt").trim()), str);
    }

    @Test
    public void testNested() {
        String layout = "===a====\n x[1\\n2] y\n ===#c=== \n ====^##default====~~~\n d\n ====##b====\nz  h";
        LayoutModel model = parse(layout);
        System.out.println(model.toString());
        String str = model.toString();
        assertEquals(str, parse(str).toString());
        System.out.println(JsonTool.serialize(model,true));

        assertEquals(1, model.getGroups().size());
        assertEquals(2, model.getGroups().get(0).getRowCount());
        assertEquals(1, model.getGroups().get(0).getRow(1).getCells().size());
        LayoutGroupModel group = (LayoutGroupModel) model.getGroups().get(0).getCell(1, 0);
        assertEquals(1, group.getTable().getRowCount());
        assertEquals(2, group.getTable().getRow(0).getColCount());
    }

    LayoutModel parse(String layout) {
        return new LayoutModelParser().parseFromText(null, layout);
    }

    @Test
    public void testLabel() {
        String layout = "===>a b====\n x[1\\n2] y\n ";
        LayoutModel model = parse(layout);
        System.out.println(model.toString());
    }

    @Test
    public void testParseSpecialChar() {
        String layout = "a$b$c\n";
        try {
            parse(layout);
            fail();
        } catch (NopException e) {
            assertEquals(CommonErrors.ERR_SCAN_INVALID_XML_NAME.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void testLayoutGroup() {
        String text = "====#sub1[sub1]====\n" +
                "===##sub_1[sub1 1]===\n" +
                "deptName[名称] parentId[父ID]\n" +
                "orderNum[排序] deptType[类型]\n" +
                "managerId[部门负责人] email[邮件]\n" +
                "phone[电话] createdBy[创建人]\n" +
                "====##sub1_2[sub1 2]===\n" +
                "createTime[创建时间] updatedBy[修改人]\n" +
                "updateTime[修改时间] remark[备注]";

        LayoutModel model = parse(text);
        model.getGroups().forEach(group -> group.getId());
        model.getFirstTable().forEachLayoutCell(cell -> cell.getId());
    }

    @Test
    public void testEmptyGroup(){
        String text = "a \n ====group===\n";
        LayoutModel model = parse(text);
        String result = model.toString();
        System.out.println(result);
        assertEquals(2, model.getGroups().size());
    }
}
