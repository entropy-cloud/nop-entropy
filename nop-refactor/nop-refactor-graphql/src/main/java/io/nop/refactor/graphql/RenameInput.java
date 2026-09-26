package io.nop.refactor.graphql;

import java.util.List;

/**
 * The rename face's input (roadmap WI12, plan 12 adjudication 7 — the
 * GraphQL shape of the WI10/WI11 RenameRequest triple plus the module file
 * set): the target locator carries exactly one of the two machine-friendly
 * forms — an FQN, or a module file path plus a byte offset (no cursor, no
 * selection) — the new name, and the module file set the rename searches.
 * The {@code paths} semantics differ from the rewrite face's by design: a
 * rename's file set is the SEARCH DOMAIN (the symbol-resolution scope),
 * not the write-target list. No scope field: v1 is module-scoped by the
 * WI2 adjudication, so the input carries nothing to widen.
 */
public class RenameInput {

    private List<String> paths;
    private String fqn;
    private String path;
    private Integer byteOffset;
    private String newName;

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public String getFqn() {
        return fqn;
    }

    public void setFqn(String fqn) {
        this.fqn = fqn;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getByteOffset() {
        return byteOffset;
    }

    public void setByteOffset(Integer byteOffset) {
        this.byteOffset = byteOffset;
    }

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }
}
