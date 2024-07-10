/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.record.csv;

import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;
import org.apache.commons.csv.CSVFormat;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CsvResourceRecordIO<T> implements IResourceRecordIO<T> {
    private List<String> headers;
    private boolean supportZip = true;
    private boolean trimValue = true;
    private Type recordType = Map.class;
    private CSVFormat format = CSVFormat.DEFAULT.withIgnoreEmptyLines().withTrim();
    private String encoding = StringHelper.ENCODING_UTF8;

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public void setFormat(String csvFormat) {
        this.format = CSVFormat.valueOf(csvFormat);
    }

    public void setCsvFormat(CSVFormat format) {
        this.format = format;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public boolean isTrimValue() {
        return trimValue;
    }

    public void setTrimValue(boolean trimValue) {
        this.trimValue = trimValue;
    }

    public boolean isSupportZip() {
        return supportZip;
    }

    public void setSupportZip(boolean supportZip) {
        this.supportZip = supportZip;
    }

    public Type getRecordType() {
        return recordType;
    }

    public void setRecordType(Type recordType) {
        this.recordType = recordType;
    }

    @Override
    public IRecordInput<T> openInput(IResource resource, String encoding) {
        if (encoding == null)
            encoding = this.encoding;
        return new CsvRecordInput<>(resource, encoding, format, getTypeInfo(resource), headers, trimValue, supportZip);
    }

    protected IGenericType getTypeInfo(IResource resource) {
        if (recordType == null)
            return PredefinedGenericTypes.MAP_STRING_ANY_TYPE;
        return BeanTool.instance().getGenericType(recordType);
    }

    @Override
    public IRecordOutput<T> openOutput(IResource resource, String encoding) {
        if (encoding == null)
            encoding = this.encoding;
        Collection<String> headers = this.headers;
        if (headers == null && recordType != List.class)
            headers = BeanTool.getReadableComplexPropNames(getTypeInfo(resource));

        return new CsvRecordOutput<>(resource, encoding, format, headers, supportZip);
    }
}