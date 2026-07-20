package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.metadata.dao.dto.ErrorDTO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据剖析主入口结果 DTO（来源：{@code NopMetaTableBizModel.profileTable} /
 * {@code NopMetaProfilingRuleBizModel.executeProfilingRule}）。
 *
 * <p>对应原 {@code Map<String,Object>}：
 * {@code {profilingResultId, columnCount, unavailable:[...], errors:[...]}}。
 */
@DataBean
public class ProfileResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String profilingResultId;
    private int columnCount;
    private List<ProfilingColumnStatsDTO> columns = new ArrayList<>();
    private List<String> unavailable = new ArrayList<>();
    private List<ErrorDTO> errors = new ArrayList<>();

    public String getProfilingResultId() {
        return profilingResultId;
    }

    public void setProfilingResultId(String profilingResultId) {
        this.profilingResultId = profilingResultId;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(int columnCount) {
        this.columnCount = columnCount;
    }

    public List<ProfilingColumnStatsDTO> getColumns() {
        return columns;
    }

    public void setColumns(List<ProfilingColumnStatsDTO> columns) {
        this.columns = columns;
    }

    public List<String> getUnavailable() {
        return unavailable;
    }

    public void setUnavailable(List<String> unavailable) {
        this.unavailable = unavailable;
    }

    public List<ErrorDTO> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorDTO> errors) {
        this.errors = errors;
    }
}
