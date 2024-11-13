/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.resource;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;
import io.nop.record.RecordConstants;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.writer.AppendableTextDataWriter;
import io.nop.record.writer.IBinaryDataWriter;
import io.nop.record.writer.ITextDataWriter;
import io.nop.record.writer.StreamBinaryDataWriter;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.Writer;

public class ResourceRecordIO<T> implements IResourceRecordIO<T> {

    private String modelFilePath = "/model/record/";

    public void setModelFilePath(String modelFilePath) {
        this.modelFilePath = modelFilePath;
    }

    @Override
    public IRecordInput<T> openInput(IResource resource, String encoding) {
        RecordFileMeta fileMeta = getFileMeta(resource);
        if (fileMeta.isBinary()) {
            //InputStream is = resource.getInputStream();
            //IBinaryDataReader out = new StreamBinaryDataReader(in);
            //return new ModelBasedBinaryRecordInput<>(out, fileMeta);
        } else {
            //Reader reader = ResourceHelper.toReader(resource, encoding, true);
            //ITextDataReader in = new AppendableTextDataWriter(new BufferedWriter(writer));
            //return new ModelBasedTextRecordInput<>(in, fileMeta);
        }
        return null;
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
        String path = getFileMetaPath(resource);
        return (RecordFileMeta) ResourceComponentManager.instance().loadComponentModel(path);
    }
}
