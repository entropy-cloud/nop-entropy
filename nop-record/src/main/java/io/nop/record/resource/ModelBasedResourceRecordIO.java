/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.resource;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;
import io.nop.record.RecordConstants;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.BlockCachedBinaryDataReader;
import io.nop.record.reader.BlockCachedTextDataReader;
import io.nop.record.reader.ByteBufferBinaryDataReader;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.reader.ITextDataReader;
import io.nop.record.reader.RandomAccessFileBinaryDataReader;
import io.nop.record.reader.ReaderTextDataReader;
import io.nop.record.reader.SimpleTextDataReader;
import io.nop.record.reader.StreamBinaryDataReader;
import io.nop.record.writer.AppendableTextDataWriter;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.writer.ITextDataWriter;
import io.nop.record.writer.StreamBinaryDataWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

public class ModelBasedResourceRecordIO<T> implements IResourceRecordIO<T> {

    private String modelFilePath = "/model/record/";
    private RecordFileMeta fileMeta;

    private int maxInMemorySize = 1024 * 1024;

    public void setMaxInMemorySize(int maxInMemorySize) {
        this.maxInMemorySize = maxInMemorySize;
    }

    public void setModelFilePath(String modelFilePath) {
        this.modelFilePath = modelFilePath;
    }

    public void setFileModel(RecordFileMeta fileMeta) {
        this.fileMeta = fileMeta;
    }

    public static <T> ModelBasedResourceRecordIO<T> fromFileModel(RecordFileMeta fileMeta) {
        Guard.notNull(fileMeta, "fileMeta");
        ModelBasedResourceRecordIO<T> io = new ModelBasedResourceRecordIO<>();
        io.setFileModel(fileMeta);
        return io;
    }

    @Override
    public IRecordInput<T> openInput(IResource resource, String encoding) {
        RecordFileMeta fileMeta = getFileMeta(resource);
        if (fileMeta.isBinary()) {
            return createBinaryInput(resource);
        } else {
            return createTextInput(resource, encoding);
        }
    }

    protected IRecordInput<T> createBinaryInput(IResource resource) {
        File file = resource.toFile();
        IBinaryDataReader reader;
        if (file != null) {
            reader = new RandomAccessFileBinaryDataReader(file);
        } else if (resource.length() <= maxInMemorySize) {
            byte[] bytes = ResourceHelper.readBytes(resource);
            reader = new ByteBufferBinaryDataReader(ByteBuffer.wrap(bytes));
        } else {
            IBinaryDataReader baseReader = new StreamBinaryDataReader(resource.getInputStream());
            reader = new BlockCachedBinaryDataReader(baseReader, 4096, false, maxInMemorySize / 4096);
        }
        RecordFileMeta fileMeta = getFileMeta(resource);
        return new ModelBasedBinaryRecordInput<>(reader, fileMeta);
    }

    protected IRecordInput<T> createTextInput(IResource resource, String encoding) {
        ITextDataReader reader;
        if (resource.length() <= maxInMemorySize) {
            String text = ResourceHelper.readText(resource, encoding);
            reader = new SimpleTextDataReader(text);
        } else {
            ITextDataReader baseReader = new ReaderTextDataReader(resource.getReader(encoding));
            reader = new BlockCachedTextDataReader(baseReader, 4096, false, maxInMemorySize / 4096);
        }
        return new ModelBasedTextRecordInput<>(reader, fileMeta);
    }

    @Override
    public IRecordOutput<T> openOutput(IResource resource, String encoding) {
        RecordFileMeta fileMeta = getFileMeta(resource);
        if (fileMeta.isBinary()) {
            OutputStream writer = ResourceHelper.toOutputStream(resource, true);
            IBinaryDataWriter out = new StreamBinaryDataWriter(writer);
            return new ModelBasedBinaryRecordOutput<>(out, fileMeta);
        } else {
            Writer writer = ResourceHelper.toWriter(resource, encoding, true);
            ITextDataWriter out = new AppendableTextDataWriter(new BufferedWriter(writer));
            return new ModelBasedTextRecordOutput<>(out, fileMeta);
        }
    }

    protected String getFileMetaPath(IResource resource) {
        String fileName = StringHelper.fileNameNoExt(resource.getName());

        String path = StringHelper.renderTemplate(modelFilePath, name -> {
            if (name.equals(RecordConstants.VAR_FILE_NAME)) {
                return fileName;
            } else if (name.equals(RecordConstants.VAR_FILE_NAME_PREFIX)) {
                return StringHelper.firstPart(fileName, '-');
            } else if (name.equals(RecordConstants.VAR_FILE_NAME_SUFFIX)) {
                return StringHelper.lastPart(fileName, '-');
            } else {
                throw new IllegalArgumentException("nop.err.record.invalid-placeholder-in-model-path:" + name + ",path=" + modelFilePath);
            }
        });
        return path;
    }

    protected RecordFileMeta getFileMeta(IResource resource) {
        if (fileMeta != null)
            return fileMeta;

        String path = getFileMetaPath(resource);
        return (RecordFileMeta) ResourceComponentManager.instance().loadComponentModel(path);
    }
}
