/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */
package io.nop.ooxml.xlsx.parse;

// refactor from poi ReadonlySharedStringTable

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.handler.XNodeHandlerAdapter;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.model.SharedStringsPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SharedStringsTableParser extends XNodeHandlerAdapter {

    /**
     * 预分配容量上限：uniqueCount 是不可信属性，真实条目数由解析过程自然增长
     */
    static final int MAX_PREALLOC_SIZE = 10_000;
    public SharedStringsPart parseFromResource(IResource resource) {
        XNodeParser.instance().handler(this).parseFromResource(resource);
        return getResult();
    }

    public SharedStringsPart getResult() {
        SharedStringsPart table = new SharedStringsPart();
        table.setCount(count);
        table.setUniqueCount(uniqueCount);
        table.setItems(strings);
        return table;
    }

    public SharedStringsPart parseFromPart(IOfficePackagePart part) {
        part.processXml(this, null);
        return getResult();
    }

    private StringBuilder characters;
    private boolean tIsOpen;
    private boolean inRPh;

    private int count;
    private int uniqueCount;
    private List<String> strings;
    private boolean includePhoneticRuns;

    public SharedStringsTableParser(boolean includePhoneticRuns) {
        this.includePhoneticRuns = includePhoneticRuns;
    }

    @Override
    public void beginNode(SourceLocation loc, String localName, Map<String, ValueWithLocation> attrs) {
        if ("sst".equals(localName)) {
            this.count = getAttrInt(attrs, "count", 0);
            this.uniqueCount = getAttrInt(attrs, "uniqueCount", 0);

            // uniqueCount来自不可信的上传文件属性，按其预分配可能被几十字节的文件放大到GB级。
            // 设置预分配上限，超出部分由 ArrayList 自然增长
            this.strings = new ArrayList<>(Math.max(0, Math.min(this.uniqueCount, MAX_PREALLOC_SIZE)));
            characters = new StringBuilder(64);
        } else if ("si".equals(localName)) {
            if (strings == null)
                strings = new ArrayList<>();
            characters.setLength(0);
        } else if ("t".equals(localName)) {
            tIsOpen = true;
        } else if ("rPh".equals(localName)) {
            inRPh = true;
            // append space...this assumes that rPh always comes after regular <t>
            if (includePhoneticRuns && characters.length() > 0) {
                characters.append(" ");
            }
        }
    }

    @Override
    public void endNode(String localName) {
        if ("si".equals(localName)) {
            strings.add(characters.toString());
        } else if ("t".equals(localName)) {
            tIsOpen = false;
        } else if ("rPh".equals(localName)) {
            inRPh = false;
        }
    }

    @Override
    public void text(SourceLocation loc, String text) {
        if (tIsOpen) {
            if (inRPh && includePhoneticRuns) {
                characters.append(text);
            } else if (!inRPh) {
                characters.append(text);
            }
        }
    }
}
