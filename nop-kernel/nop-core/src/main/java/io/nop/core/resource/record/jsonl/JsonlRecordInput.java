/**
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 */
package io.nop.core.resource.record.jsonl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordResourceMeta;
import io.nop.dataset.record.RecordResourceMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_READ_COUNT;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_READ_JSONL_ROW_FAIL;

public class JsonlRecordInput<T> implements IRecordInput<T> {
    static final Logger LOG = LoggerFactory.getLogger(JsonlRecordInput.class);

    private final String resourcePath;
    private final IGenericType beanType;
    private final boolean nonStrict;

    private final Reader reader;
    private final BufferedReader br;

    private long readCount;
    private String nextLine;

    public JsonlRecordInput(IResource resource, String encoding,
                            IGenericType beanType, boolean supportZip, boolean nonStrict) {
        this.resourcePath = resource.getPath();
        this.beanType = beanType;
        this.nonStrict = nonStrict;
        this.reader = ResourceHelper.toReader(resource, encoding, supportZip);
        this.br = new BufferedReader(this.reader);
    }

    @Override
    public void beforeRead(Map<String, Object> map) {
        // JSONL does not use header metadata
    }

    @Override
    public IRecordResourceMeta getMeta() {
        // No header information for JSONL
        return new RecordResourceMeta(Collections.emptyList(), null);
    }

    @Override
    public long getReadCount() {
        return readCount;
    }

    @Override
    public void close() throws IOException {
        br.close();
    }

    @Override
    public boolean hasNext() {
        if (nextLine != null)
            return true;
        try {
            String line;
            while ((line = br.readLine()) != null) {
                if (StringHelper.isBlank(line))
                    continue;
                nextLine = line;
                return true;
            }
            return false;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T next() {
        if (!hasNext())
            throw new IllegalStateException("no more records");

        String line = nextLine;
        nextLine = null;
        readCount++;

        try {
            Object obj;
            if (nonStrict) {
                obj = JsonTool.parseNonStrict(line);
            } else {
                obj = JsonTool.parse(line);
            }

            if (beanType == null || beanType == PredefinedGenericTypes.MAP_STRING_ANY_TYPE) {
                return (T) obj;
            }

            return (T) BeanTool.instance().buildBean(obj, beanType, null);
        } catch (Exception e) {
            throw new NopException(ERR_RESOURCE_READ_JSONL_ROW_FAIL, e)
                    .param(ARG_RESOURCE_PATH, resourcePath)
                    .param(ARG_READ_COUNT, readCount);
        }
    }
}
