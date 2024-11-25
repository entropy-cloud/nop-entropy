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
import io.nop.record.reader.ByteBufferBinaryDataReader;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.reader.ITextDataReader;
import io.nop.record.reader.RandomAccessFileBinaryDataReader;
import io.nop.record.reader.SimpleTextDataReader;
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
            File file = resource.toFile();
            IBinaryDataReader reader;
            if (file != null) {
                reader = new RandomAccessFileBinaryDataReader(file);
            } else {
                byte[] bytes = ResourceHelper.readBytes(resource);
                reader = new ByteBufferBinaryDataReader(ByteBuffer.wrap(bytes));
            }
            return new ModelBasedBinaryRecordInput<>(reader, fileMeta);
        } else {
            String text = ResourceHelper.readText(resource, encoding);
            ITextDataReader reader = new SimpleTextDataReader(text);
            return new ModelBasedTextRecordInput<>(reader, fileMeta);
        }
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
        String prefix = StringHelper.firstPart(fileName, '-');
        String path = StringHelper.appendPath(modelFilePath, prefix) + RecordConstants.RECORD_FILE_XML_POSTFIX;
        return path;
    }

    protected RecordFileMeta getFileMeta(IResource resource) {
        if (fileMeta != null)
            return fileMeta;

        String path = getFileMetaPath(resource);
        return (RecordFileMeta) ResourceComponentManager.instance().loadComponentModel(path);
    }
}
