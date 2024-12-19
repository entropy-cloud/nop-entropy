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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.nop.core.CoreErrors.ARG_READ_COUNT;
import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_READ_CSV_ROW_FAIL;

public class CsvRecordInput<T> implements IRecordInput<T> {
    static final Logger LOG = LoggerFactory.getLogger(CsvRecordInput.class);
    private final IResource resource;
    private List<String> headers;
    private List<String> headerLabels;
    private final IGenericType beanType;
    private final boolean trimValue;
    private Reader reader;
    private Iterator<CSVRecord> it;
    private long readCount;
    private List<String> fileHeaders;

    private List<String> normalizedHeaders;

    private Function<List<String>, List<String>> headerNormalizer;

    public CsvRecordInput(IResource resource, String encoding, CSVFormat format, IGenericType beanType,
                          boolean trimValue, boolean supportZip) {
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
        this.normalizedHeaders = fileHeaders;
    }

    public void setHeaderNormalizer(Function<List<String>, List<String>> headerNormalizer) {
        this.headerNormalizer = headerNormalizer;
    }

    protected void normalizeFileHeaders() {
        if (!fileHeaders.isEmpty()) {
            fileHeaders = CollectionHelper.trimStringList(fileHeaders);
            if (headerNormalizer != null) {
                this.normalizedHeaders = headerNormalizer.apply(fileHeaders);
            } else {
                // label不为空，则起到选择作用，从fileHeaders中选择对应的header
                List<String> headers = this.headers;
                if (headerLabels != null && !headerLabels.isEmpty()) {
                    this.normalizedHeaders = new ArrayList<>(headerLabels.size());

                    for (String label : headerLabels) {
                        int index = fileHeaders.indexOf(label);
                        if (index >= 0) {
                            normalizedHeaders.add(CollectionHelper.get(headers, index));
                        } else {
                            LOG.info("nop.csv.ignore-header:header={}, allowed={}", label, headerLabels);
                            normalizedHeaders.add(null);
                        }
                    }
                } else if (headers != null && !headers.isEmpty()) {
                    // 没有指定label，相当于是重命名Header
                    this.normalizedHeaders = headers;
                } else {
                    normalizedHeaders = fileHeaders;
                }
            }
        } else {
            this.normalizedHeaders = headers == null ? Collections.emptyList() : headers;
        }
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public void setHeaderLabels(List<String> headerLabels) {
        this.headerLabels = headerLabels;
    }

    @Override
    public void beforeRead(Map<String, Object> map) {
        this.normalizeFileHeaders();
    }

    @Override
    public IRecordResourceMeta getMeta() {
        return new RecordResourceMeta(normalizedHeaders, null);
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
            throw new NopException(ERR_RESOURCE_READ_CSV_ROW_FAIL, e).param(ARG_RESOURCE, resource)
                    .param(ARG_RESOURCE_PATH, resource.getPath()).param(ARG_READ_COUNT, readCount);
        }
    }

    Map<String, Object> buildMap(CSVRecord record) {
        Map<String, Object> bean = new LinkedHashMap<>();
        for (int i = 0, n = normalizedHeaders.size(); i < n; i++) {
            String header = normalizedHeaders.get(i);
            if (StringHelper.isEmpty(header)) {
                continue;
            }
            if (record.size() <= i)
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