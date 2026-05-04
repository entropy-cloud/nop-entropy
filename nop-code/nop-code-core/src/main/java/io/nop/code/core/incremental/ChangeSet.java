package io.nop.code.core.incremental;

import io.nop.api.core.annotations.data.DataBean;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文件变更集，记录新增、修改、删除和未变更的文件列表
 */
@DataBean
public class ChangeSet {
    private List<Path> addedFiles = new ArrayList<>();
    private List<Path> modifiedFiles = new ArrayList<>();
    private List<Path> deletedFiles = new ArrayList<>();
    private List<Path> unchangedFiles = new ArrayList<>();

    public List<Path> getAddedFiles() {
        return Collections.unmodifiableList(addedFiles);
    }

    public void setAddedFiles(List<Path> addedFiles) {
        this.addedFiles = addedFiles != null ? new ArrayList<>(addedFiles) : new ArrayList<>();
    }

    public List<Path> getModifiedFiles() {
        return Collections.unmodifiableList(modifiedFiles);
    }

    public void setModifiedFiles(List<Path> modifiedFiles) {
        this.modifiedFiles = modifiedFiles != null ? new ArrayList<>(modifiedFiles) : new ArrayList<>();
    }

    public List<Path> getDeletedFiles() {
        return Collections.unmodifiableList(deletedFiles);
    }

    public void setDeletedFiles(List<Path> deletedFiles) {
        this.deletedFiles = deletedFiles != null ? new ArrayList<>(deletedFiles) : new ArrayList<>();
    }

    public List<Path> getUnchangedFiles() {
        return Collections.unmodifiableList(unchangedFiles);
    }

    public void setUnchangedFiles(List<Path> unchangedFiles) {
        this.unchangedFiles = unchangedFiles != null ? new ArrayList<>(unchangedFiles) : new ArrayList<>();
    }

    public List<Path> getAddedAndModified() {
        List<Path> result = new ArrayList<>(addedFiles.size() + modifiedFiles.size());
        result.addAll(addedFiles);
        result.addAll(modifiedFiles);
        return result;
    }
}
