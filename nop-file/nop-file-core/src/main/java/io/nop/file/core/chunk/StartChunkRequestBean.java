package io.nop.file.core.chunk;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class StartChunkRequestBean {
    private String filename;

    private int chunkSize;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }
}