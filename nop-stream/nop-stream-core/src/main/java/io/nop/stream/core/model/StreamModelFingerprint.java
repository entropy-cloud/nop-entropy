/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.model;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class StreamModelFingerprint implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String FINGERPRINT_VERSION = "1.0";

    private final String version;
    private final Map<String, String> componentHashes;
    private final String dagTopologyHash;
    private final String requirementsHash;
    private final String checkpointParticipantsHash;

    public StreamModelFingerprint(String version,
                                  Map<String, String> componentHashes,
                                  String dagTopologyHash,
                                  String requirementsHash,
                                  String checkpointParticipantsHash) {
        this.version = version != null ? version : FINGERPRINT_VERSION;
        this.componentHashes = componentHashes != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(componentHashes))
                : Collections.emptyMap();
        this.dagTopologyHash = dagTopologyHash;
        this.requirementsHash = requirementsHash;
        this.checkpointParticipantsHash = checkpointParticipantsHash;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, String> getComponentHashes() {
        return componentHashes;
    }

    public String getDagTopologyHash() {
        return dagTopologyHash;
    }

    public String getRequirementsHash() {
        return requirementsHash;
    }

    public String getCheckpointParticipantsHash() {
        return checkpointParticipantsHash;
    }

    public boolean isCompatibleWith(StreamModelFingerprint other) {
        if (other == null) return false;
        if (!Objects.equals(this.version, other.version)) return false;
        if (!Objects.equals(this.dagTopologyHash, other.dagTopologyHash)) return false;
        if (!Objects.equals(this.requirementsHash, other.requirementsHash)) return false;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StreamModelFingerprint that = (StreamModelFingerprint) o;
        return Objects.equals(version, that.version)
                && Objects.equals(componentHashes, that.componentHashes)
                && Objects.equals(dagTopologyHash, that.dagTopologyHash)
                && Objects.equals(requirementsHash, that.requirementsHash)
                && Objects.equals(checkpointParticipantsHash, that.checkpointParticipantsHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, componentHashes, dagTopologyHash, requirementsHash, checkpointParticipantsHash);
    }

    @Override
    public String toString() {
        return "StreamModelFingerprint{" +
                "version='" + version + '\'' +
                ", dagTopologyHash='" + dagTopologyHash + '\'' +
                ", componentCount=" + componentHashes.size() +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String version = FINGERPRINT_VERSION;
        private final Map<String, String> componentHashes = new LinkedHashMap<>();
        private String dagTopologyHash;
        private String requirementsHash;
        private String checkpointParticipantsHash;

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder addComponentHash(String componentId, String hash) {
            this.componentHashes.put(componentId, hash);
            return this;
        }

        public Builder dagTopologyHash(String hash) {
            this.dagTopologyHash = hash;
            return this;
        }

        public Builder requirementsHash(String hash) {
            this.requirementsHash = hash;
            return this;
        }

        public Builder checkpointParticipantsHash(String hash) {
            this.checkpointParticipantsHash = hash;
            return this;
        }

        public StreamModelFingerprint build() {
            return new StreamModelFingerprint(version, componentHashes, dagTopologyHash, requirementsHash, checkpointParticipantsHash);
        }
    }

    static String computeSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
