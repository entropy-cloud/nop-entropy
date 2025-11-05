/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;
import java.util.Objects;

@DataBean
public class DialectSelector implements Comparable<DialectSelector> {
    private String dialectName;
    private String productName;
    private String productVersion;
    private String driverName;
    private int driverMinorVersion;
    private int driverMajorVersion;

    private List<String> otherProductNames;

    /**
     * 用于匹配PowerDesigner模型中设置的目标数据库类型
     */
    private String pdmTargetType;

    public int hashCode() {
        int h = dialectName.hashCode();
        h = h * 31 + productName.hashCode();
        h = h * 31 + (productVersion == null ? 0 : productVersion.hashCode());
        h = h * 31 + (driverName == null ? 0 : driverName.hashCode());
        h = h * 31 + driverMajorVersion;
        h = h * 31 + driverMinorVersion;
        return h;
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof DialectSelector))
            return false;

        return this.compareTo(((DialectSelector) o)) == 0;
    }

    public List<String> getOtherProductNames() {
        return otherProductNames;
    }

    public void setOtherProductNames(List<String> otherProductNames) {
        this.otherProductNames = otherProductNames;
    }

    // 针对具体版本的dialect排在前面
    @Override
    public int compareTo(DialectSelector o) {
        int cmp = compare(dialectName, o.dialectName);
        if (cmp != 0)
            return cmp;

        cmp = compare(productName, o.productName);
        if (cmp != 0)
            return cmp;

        cmp = compare(driverName, o.driverName);
        if (cmp != 0)
            return cmp;

        cmp = compare(driverMajorVersion, o.driverMajorVersion);
        if (cmp != 0)
            return cmp;

        cmp = compare(driverMinorVersion, o.driverMinorVersion);
        if (cmp != 0)
            return cmp;

        cmp = compare(productVersion, o.productVersion);
        if (cmp != 0)
            return cmp;

        return compare(pdmTargetType, o.pdmTargetType);
    }

    int compare(int v1, int v2) {
        if (v1 == v2)
            return 0;
        if (v1 == 0)
            return 1;
        if (v2 == 0)
            return -1;
        return Integer.compare(v1, v2);
    }

    int compare(String s1, String s2) {
        if (Objects.equals(s1, s2))
            return 0;
        if (s1 == null)
            return -1;
        if (s2 == null)
            return 1;
        return s1.compareTo(s2);
    }

    public boolean match(String productName, String productVersion, String driverName, int driverMajorVersion,
                         int driverMinorVersion) {
        if (!this.productName.equals(productName)) {
            if (this.otherProductNames == null || !this.otherProductNames.contains(productName))
                return false;
        }

        if (this.productVersion != null && !this.productVersion.equals(productVersion))
            return false;

        if (this.driverName != null && !this.driverName.equals(driverName))
            return false;

        if (this.driverMajorVersion > 0 && this.driverMajorVersion != driverMajorVersion)
            return false;

        if (this.driverMinorVersion > 0 && this.driverMinorVersion != driverMinorVersion)
            return false;

        return true;
    }

    public String getPdmTargetType() {
        return pdmTargetType;
    }

    public void setPdmTargetType(String pdmTargetType) {
        this.pdmTargetType = pdmTargetType;
    }

    public String getDialectName() {
        return dialectName;
    }

    public void setDialectName(String dialectName) {
        this.dialectName = dialectName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public void setProductVersion(String productVersion) {
        this.productVersion = productVersion;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public int getDriverMinorVersion() {
        return driverMinorVersion;
    }

    public void setDriverMinorVersion(int driverMinorVersion) {
        this.driverMinorVersion = driverMinorVersion;
    }

    public int getDriverMajorVersion() {
        return driverMajorVersion;
    }

    public void setDriverMajorVersion(int driverMajorVersion) {
        this.driverMajorVersion = driverMajorVersion;
    }
}
