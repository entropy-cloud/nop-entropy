package io.nop.file.core;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;

import javax.inject.Inject;

import static io.nop.file.core.FileErrors.ARG_LENGTH;
import static io.nop.file.core.FileErrors.ARG_MAX_LENGTH;
import static io.nop.file.core.FileErrors.ERR_FILE_LENGTH_EXCEED_LIMIT;

public abstract class AbstractFileService {
    protected IFileStore fileStore;

    protected long maxFileLength;

    @Inject
    public void setFileStore(IFileStore fileStore) {
        this.fileStore = fileStore;
    }

    @InjectValue("@cfg:nop.file.upload.max-length|16777216")
    public void setMaxFileLength(long maxFileLength) {
        this.maxFileLength = maxFileLength;
    }

    protected void checkMaxLength(long length) {
        if (length > maxFileLength)
            throw new NopException(ERR_FILE_LENGTH_EXCEED_LIMIT)
                    .param(ARG_LENGTH, length).param(ARG_MAX_LENGTH, maxFileLength);
    }
}
