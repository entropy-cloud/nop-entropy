package io.nop.ib.index;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-class facts recorded by the unit indexer (design 05 §6.2).
 */
public class ClassInfo {
    private String className;
    /** sha256 of the class file bytes */
    private String contentHash;
    /** digest over the accessible surface (non-private members + header + annotations) */
    private String abiDigest;
    /** source file path (module-relative) from the SourceFile attribute, may be null */
    private String sourceFile;
    /** class-level access flags */
    private int access;
    /** referenced class binary names (constant-pool-derived upper bound) */
    private List<String> refs = new ArrayList<>();
    /** static final primitive/String constants: name -> canonical value string */
    private Map<String, String> inlineConstants = new LinkedHashMap<>();

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("class", className);
        m.put("hash", contentHash);
        m.put("abi", abiDigest);
        m.put("source", sourceFile);
        m.put("access", access);
        m.put("refs", refs);
        if (!inlineConstants.isEmpty())
            m.put("consts", inlineConstants);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static ClassInfo fromMap(Map<String, Object> m) {
        ClassInfo info = new ClassInfo();
        info.className = (String) m.get("class");
        info.contentHash = (String) m.get("hash");
        info.abiDigest = (String) m.get("abi");
        info.sourceFile = (String) m.get("source");
        Object access = m.get("access");
        info.access = access instanceof Number n ? n.intValue() : 0;
        Object refs = m.get("refs");
        if (refs instanceof Iterable<?> iter) {
            for (Object o : iter)
                info.refs.add(String.valueOf(o));
        }
        Object consts = m.get("consts");
        if (consts instanceof Map<?, ?> cm) {
            for (Map.Entry<?, ?> e : cm.entrySet())
                info.inlineConstants.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        return info;
    }

    public String getClassName() {
        return className;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public void setAbiDigest(String abiDigest) {
        this.abiDigest = abiDigest;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void setAccess(int access) {
        this.access = access;
    }

    public void setRefs(List<String> refs) {
        this.refs = refs;
    }

    public String getAbiDigest() {
        return abiDigest;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public int getAccess() {
        return access;
    }

    public List<String> getRefs() {
        return refs;
    }

    public Map<String, String> getInlineConstants() {
        return inlineConstants;
    }
}
