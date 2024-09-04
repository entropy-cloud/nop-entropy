package io.nop.security.rsa;

import io.nop.api.core.annotations.data.DataBean;

import java.time.LocalDate;

@DataBean
public class CertInfo {
    private String commonName; // 通用名称，通常指证书持有者的名字或者主机名。
    private String organizationUnit; // 组织单位，指的是证书持有者所在的组织内的部门或单位
    private String organization; // 组织，指的是证书持有者所在的组织的名称。
    private String locality; // 地点，通常指证书持有者所在的城市或地区。
    private String state; // 州或省，指证书持有者所在的州或省。
    private String country; // 国家，指证书持有者所在的国家，通常使用ISO 3166-1 alpha-2国家代码

    private LocalDate beginDate;
    private LocalDate endDate;

    public String toX500Name() {
        return "CN=" + getCommonName() + ", " +
                "OU=" + getOrganizationUnit() + ", " +
                "O=" + getOrganization() + ", " +
                "L=" + getLocality() + ", " +
                "ST=" + getState() + ", " +
                "C=" + getCountry();
    }

    public LocalDate getBeginDate() {
        return beginDate;
    }

    public void setBeginDate(LocalDate beginDate) {
        this.beginDate = beginDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getOrganizationUnit() {
        return organizationUnit;
    }

    public void setOrganizationUnit(String organizationUnit) {
        this.organizationUnit = organizationUnit;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
