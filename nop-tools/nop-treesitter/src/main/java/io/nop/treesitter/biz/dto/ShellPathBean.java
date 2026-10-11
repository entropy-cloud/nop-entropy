package io.nop.treesitter.biz.dto;

import io.nop.api.core.annotations.data.DataBean;

/**
 * One candidate file path, with the two facts a consumer cannot reconstruct from the
 * string: whether it is read or written, and how much is known about it.
 *
 * <p>{@code confidence} distinguishes a path taken from a proven script from one taken
 * from a partial harvest. Mixing the two reads "this script mentions /data somewhere"
 * as "this script provably touches /data".</p>
 */
@DataBean
public class ShellPathBean {

    private String path;
    private String access;
    private String confidence;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /** {@code READ} or {@code WRITE} (a redirect destination is always a write). */
    public String getAccess() {
        return access;
    }

    public void setAccess(String access) {
        this.access = access;
    }

    /** {@code PROVEN} or {@code HARVESTED}. */
    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }
}
