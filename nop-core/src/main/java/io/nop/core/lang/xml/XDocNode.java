/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml;

/**
 * 内部使用，保存docType, instruction, encoding等变量，不作为对外接口。外部调用者只知道XNode这一个接口类型
 *
 * @author canonical_entropy@163.com
 */
public class XDocNode extends XNode {

    private static final long serialVersionUID = -7280043822070609462L;

    private String docType;
    private String instruction;
    private String encoding;

    public XDocNode(String tagName) {
        super(tagName);
    }

    @Override
    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String encoding() {
        return encoding;
    }

    public void encoding(String encoding) {
        this.encoding = encoding;
    }

    @Override
    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    @Override
    protected XNode makeNode() {
        XDocNode docNode = new XDocNode(getTagName());
        docNode.setDocType(docType);
        docNode.setInstruction(instruction);
        return docNode;
    }
}