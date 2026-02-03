/**
 * Copyright (c) 2017-2026 Nop Platform. All rights reserved.
 */
package io.nop.core.resource.record.jsonl;

import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * JSON Lines based resource record IO.
 * Each line is a single JSON value, parsed using JsonTool in non-strict mode by default.
 */
public class JsonlResourceRecordIO<T> implements IResourceRecordIO<T> {

    private boolean supportZip = true;
    private String encoding = StringHelper.ENCODING_UTF8;
    private Type recordType = Map.class;
    private boolean nonStrict = true;

    public boolean isSupportZip() {
        return supportZip;
    }

    public void setSupportZip(boolean supportZip) {
        this.supportZip = supportZip;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Type getRecordType() {
        return recordType;
    }

    public void setRecordType(Type recordType) {
        this.recordType = recordType;
    }

    public boolean isNonStrict() {
        return nonStrict;
    }

    public void setNonStrict(boolean nonStrict) {
        this.nonStrict = nonStrict;
    }

    protected IGenericType getTypeInfo(IResource resource) {
        if (recordType == null)
            return PredefinedGenericTypes.MAP_STRING_ANY_TYPE;
        return BeanTool.instance().getGenericType(recordType);
    }

    @Override
    public IRecordInput<T> openInput(IResource resource, String encoding) {
        if (encoding == null)
            encoding = this.encoding;
        IGenericType typeInfo = getTypeInfo(resource);
        return new JsonlRecordInput<>(resource, encoding, typeInfo, supportZip, nonStrict);
    }

    @Override
    public IRecordOutput<T> openOutput(IResource resource, String encoding) {
        if (encoding == null)
            encoding = this.encoding;
        return new JsonlRecordOutput<>(resource, encoding, supportZip);
    }
}
