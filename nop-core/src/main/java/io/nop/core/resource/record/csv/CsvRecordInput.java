/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.record.csv;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordResourceMeta;
import io.nop.dataset.record.RecordResourceMeta;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_READ_COUNT;
import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_READ_CSV_ROW_FAIL;

public class CsvRecordInput<T> implements IRecordInput<T> {
    private final IResource resource;
    private final List<String> headers;
    private final IGenericType beanType;
    private final boolean trimValue;
    private Reader reader;
    private Iterator<CSVRecord> it;
    private long readCount;
    private List<String> fileHeaders;

    public CsvRecordInput(IResource resource, String encoding, CSVFormat format, IGenericType beanType,
                          List<String> headers, boolean trimValue, boolean supportZip) {
        this.resource = resource;
        this.beanType = beanType;
        this.trimValue = trimValue;
        this.reader = ResourceHelper.toReader(resource, encoding, supportZip);
        try {
            CSVParser csvReader = CSVParser.parse(reader, format.withHeader());
            this.it = csvReader.iterator();
            fileHeaders = csvReader.getHeaderNames();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        if (!fileHeaders.isEmpty()) {
            fileHeaders = CollectionHelper.trimStringList(fileHeaders);
            if (headers != null) {
                // 仅保留指定的headers
                this.headers = new ArrayList<>(fileHeaders);
                this.headers.retainAll(fileHeaders);
            } else {
                this.headers = fileHeaders;
            }
        } else {
            this.headers = headers == null ? Collections.emptyList() : headers;
        }
    }

    @Override
    public IRecordResourceMeta getMeta() {
        return new RecordResourceMeta(headers, null);
    }

    @Override
    public long getReadCount() {
        return readCount;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    @Override
    public boolean hasNext() {
        return it.hasNext();
    }

    @Override
    public T next() {
        try {
            readCount++;
            Map<String, Object> map = buildMap(it.next());
            if (beanType == null || beanType == PredefinedGenericTypes.MAP_STRING_ANY_TYPE)
                return (T) map;
            return (T) BeanTool.instance().buildBean(map, beanType, null);
        } catch (Exception e) {
            throw new NopException(ERR_RESOURCE_READ_CSV_ROW_FAIL,e).param(ARG_RESOURCE, resource)
                    .param(ARG_RESOURCE_PATH, resource.getPath()).param(ARG_READ_COUNT, readCount);
        }
    }

    Map<String, Object> buildMap(CSVRecord record) {
        Map<String, Object> bean = new LinkedHashMap<>();
        for (int i = 0, n = fileHeaders.size(); i < n; i++) {
            String header = fileHeaders.get(i);
            if (StringHelper.isEmpty(header)) {
                continue;
            }
            if (headers != fileHeaders) {
                if (!headers.contains(header)) {
                    continue;
                }
            }
            if(record.size() <= i)
                continue;
            String value = record.get(i);
            if (trimValue) {
                value = StringHelper.strip(value);
            } else if (StringHelper.isEmpty(value)) {
                value = null;
            }
            BeanTool.setComplexProperty(bean, header, value);
        }
        return bean;
    }
}