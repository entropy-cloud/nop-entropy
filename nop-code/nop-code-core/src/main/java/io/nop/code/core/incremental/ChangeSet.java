package io.nop.code.core.incremental;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
/**
 * 文件变更集，记录新增、修改、删除和未变更的文件列表
 */
@DataBean
public class ChangeSet {
    private List<String> addedFiles = new ArrayList<>();
    private List<String> modifiedFiles = new ArrayList<>();
    private List<String> deletedFiles = new ArrayList<>();
    private List<String> unchangedFiles = new ArrayList<>();

    public List<String> getAddedFiles() {
        return Collections.unmodifiableList(addedFiles);
    }

    public void setAddedFiles(List<String> addedFiles) {
        this.addedFiles = addedFiles != null ? new ArrayList<>(addedFiles) : new ArrayList<>();
    }

    public List<String> getModifiedFiles() {
        return Collections.unmodifiableList(modifiedFiles);
    }

    public void setModifiedFiles(List<String> modifiedFiles) {
        this.modifiedFiles = modifiedFiles != null ? new ArrayList<>(modifiedFiles) : new ArrayList<>();
    }

    public List<String> getDeletedFiles() {
        return Collections.unmodifiableList(deletedFiles);
    }

    public void setDeletedFiles(List<String> deletedFiles) {
        this.deletedFiles = deletedFiles != null ? new ArrayList<>(deletedFiles) : new ArrayList<>();
    }

    public List<String> getUnchangedFiles() {
        return Collections.unmodifiableList(unchangedFiles);
    }

    public void setUnchangedFiles(List<String> unchangedFiles) {
        this.unchangedFiles = unchangedFiles != null ? new ArrayList<>(unchangedFiles) : new ArrayList<>();
    }

    public List<String> getAddedAndModified() {
        List<String> result = new ArrayList<>(addedFiles.size() + modifiedFiles.size());
        result.addAll(addedFiles);
        result.addAll(modifiedFiles);
        return result;
    }
}
