package io.nop.datav.service.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.record.csv.CsvRecordOutput;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.datav.biz.PanelComponentMeta;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.component.IPanelComponent;
import io.nop.datav.service.component.PanelComponentRegistry;
import io.nop.datav.service.component.PanelTypeMapping;
import io.nop.datav.service.query.PanelDataBinder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS;
import static io.nop.datav.service.NopDatavErrors.ARG_DASHBOARD_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_FORMAT;
import static io.nop.datav.service.NopDatavErrors.ARG_MAX_ROWS;
import static io.nop.datav.service.NopDatavErrors.ARG_PANEL_COUNT;
import static io.nop.datav.service.NopDatavErrors.ARG_MAX_PANELS;
import static io.nop.datav.service.NopDatavErrors.ARG_REASON;
import static io.nop.datav.service.NopDatavErrors.ARG_ROW_COUNT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_FAILED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_NO_EXPORTABLE_PANELS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED;

/**
 * 面板/看板数据导出器（D3-3）。复用 D1 数据绑定管线 ({@link PanelDataBinder}) 取数，
 * 写出 CSV（UTF-8 + BOM，兼容 Excel 中文）或 xlsx（经 {@link ExcelHelper#saveExcel}）。
 *
 * <p>行数限额防 OOM：导出路径经 {@link PanelDataBinder#queryPanelData(String, NopDatavPanel, Map, Integer)}
 * 在数据集层限行（取 {@code maxRows+1} 行探测），结果超 {@code maxRows} 即抛
 * {@code ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED}（取数阶段防 OOM，非取数后校验）。</p>
 *
 * <p>看板级导出：遍历 needsDataset 面板，多 sheet xlsx（每面板一 sheet）。csv 仅支持单面板
 * （看板级 csv 请求在 BizModel 层显式拒绝）。</p>
 *
 * <p><b>AR-3 面板数上界（plan 2026-08-15-2146-2 Phase 4）</b>：exportDashboard 入口对 needsDataset
 * 面板数做前置上限校验（复用 {@code CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS}，默认 50），超限抛
 * {@code ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED}（显式失败，非静默截断），校验先于任何面板
 * 取数——与 getDashboardData / saveDashboardLayout 路径防护对称（裁定见 runtime-design.md §4.4/§十）。</p>
 */
public class PanelDataExporter {

    public static final String FORMAT_CSV = "csv";
    public static final String FORMAT_XLSX = "xlsx";

    public static final String MIME_CSV = "text/csv";
    public static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * UTF-8 BOM 三字节。Excel 用 BOM 识别 CSV 为 UTF-8，避免中文乱码。
     */
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final PanelDataBinder dataBinder;
    private final IDaoProvider daoProvider;

    public PanelDataExporter(IDaoProvider daoProvider, IJdbcTemplate jdbcTemplate) {
        this.daoProvider = daoProvider;
        this.dataBinder = new PanelDataBinder(daoProvider, jdbcTemplate);
    }

    /**
     * 检查取消标志位（D2 §312）。命中即抛 {@code ERR_DATAV_EXPORT_FAILED}（"cancelled by user"），
     * 让调用方（{@code executeTask}）的 catch 分流走 cancel 路径。{@code cancelChecker} 为 null 时为 noop
     * （向后兼容旧调用方）。
     */
    private static void checkCancelled(BooleanSupplier cancelChecker) {
        if (cancelChecker != null && cancelChecker.getAsBoolean()) {
            throw new NopException(ERR_DATAV_EXPORT_FAILED).param(ARG_REASON, "cancelled by user");
        }
    }

    /**
     * 导出单个面板数据（不带 cancel 检查；向后兼容）。
     *
     * @param panel   已加载的面板实体
     * @param params  请求参数（允许 null）
     * @param format  {@link #FORMAT_CSV} 或 {@link #FORMAT_XLSX}
     * @param maxRows 单面板最大导出行数（取数阶段 LIMIT 探测 maxRows+1，超出抛错）
     * @return 写出的导出文件（临时 IResource + 元数据 + 行数）
     */
    public ExportFile exportPanel(NopDatavPanel panel, Map<String, Object> params, String format, int maxRows) {
        return exportPanel(panel, params, format, maxRows, null);
    }

    /**
     * 导出单个面板数据（带 cancel 检查）。
     *
     * <p>取消检查点：(1) 取数后写出前；(2) 写出循环内每行。命中即抛
     * {@code ERR_DATAV_EXPORT_FAILED}("cancelled by user")。</p>
     *
     * @param cancelChecker 取消标志位供应者（允许 null = 不检查）
     */
    public ExportFile exportPanel(NopDatavPanel panel, Map<String, Object> params, String format, int maxRows,
                                  BooleanSupplier cancelChecker) {
        PanelDataResult result = dataBinder.queryPanelData(panel.getPanelId(), panel, params, maxRows + 1);
        if (result.isHasDataset() && result.getRows().size() > maxRows) {
            throw new NopException(ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED)
                    .param(ARG_ROW_COUNT, result.getRows().size())
                    .param(ARG_MAX_ROWS, maxRows);
        }
        // 取数后、写出前检查（主保护点）
        checkCancelled(cancelChecker);
        String baseName = sanitizeFileName(panel.getDisplayName() != null ? panel.getDisplayName() : panel.getPanelName());
        if (FORMAT_CSV.equalsIgnoreCase(format)) {
            return writeCsv(baseName, result.getColumns(), result.getRows(), cancelChecker);
        }
        if (FORMAT_XLSX.equalsIgnoreCase(format)) {
            return writeXlsx(baseName, toSheet(baseName, result.getColumns(), result.getRows(), cancelChecker));
        }
        throw new NopException(ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED).param(ARG_FORMAT, format);
    }

    /**
     * 导出看板下所有 needsDataset 面板（多 sheet xlsx，不带 cancel 检查；向后兼容）。
     *
     * @param dashboardId 看板 ID
     * @param params      请求参数（允许 null）
     * @param maxRows     单面板最大导出行数
     * @return 写出的导出文件（xlsx 多 sheet）
     */
    public ExportFile exportDashboard(String dashboardId, Map<String, Object> params, int maxRows) {
        return exportDashboard(dashboardId, params, maxRows, null);
    }

    /**
     * 导出看板下所有 needsDataset 面板（多 sheet xlsx，带 cancel 检查）。
     *
     * <p>取消检查点：(1) 每个面板取数前（面板间）；(2) 取数后写出前；(3) 行循环内。
     * 命中即抛 {@code ERR_DATAV_EXPORT_FAILED}("cancelled by user")。</p>
     *
     * @param cancelChecker 取消标志位供应者（允许 null = 不检查）
     */
    public ExportFile exportDashboard(String dashboardId, Map<String, Object> params, int maxRows,
                                      BooleanSupplier cancelChecker) {
        IEntityDao<NopDatavPanel> panelDao = daoProvider.daoFor(NopDatavPanel.class);
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq("dashboardId", dashboardId));
        query.addOrderField("sortOrder", true);
        @SuppressWarnings("unchecked")
        List<NopDatavPanel> panels = (List<NopDatavPanel>) panelDao.findAllByQuery(query);
        List<NopDatavPanel> exportable = new ArrayList<>();
        for (NopDatavPanel p : panels) {
            String ct = PanelTypeMapping.toComponentType(p.getPanelType());
            IPanelComponent component = PanelComponentRegistry.getInstance().requireComponent(ct);
            PanelComponentMeta meta = component.getMetadata();
            if (meta.isNeedsDataset()) {
                exportable.add(p);
            }
        }
        if (exportable.isEmpty()) {
            throw new NopException(ERR_DATAV_EXPORT_NO_EXPORTABLE_PANELS).param(ARG_DASHBOARD_ID, dashboardId);
        }

        // AR-3 修复（plan 2026-08-15-2146-2 Phase 4）：导出路径面板数前置上限——复用
        // CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS（同为性能界：防单请求放大为海量 SQL；导出每面板
        // 1 条 SQL + maxRows 行取数 + 全量内存 workbook，与 getDashboardData 防护对称）。超限
        // 结构化失败（显式报错，非静默截断），校验先于任何面板取数执行。
        int maxPanels = CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS.get();
        if (maxPanels > 0 && exportable.size() > maxPanels) {
            throw new NopException(ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED)
                    .param(ARG_PANEL_COUNT, exportable.size())
                    .param(ARG_MAX_PANELS, maxPanels);
        }

        ExcelWorkbook workbook = new ExcelWorkbook();
        long totalRows = 0;
        List<String> usedNames = new ArrayList<>();
        for (NopDatavPanel panel : exportable) {
            // 面板间检查（主保护点：exporter 内最重要的 cancel 时机）
            checkCancelled(cancelChecker);
            PanelDataResult result = dataBinder.queryPanelData(panel.getPanelId(), panel, params, maxRows + 1);
            if (result.getRows().size() > maxRows) {
                throw new NopException(ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED)
                        .param(ARG_ROW_COUNT, result.getRows().size())
                        .param(ARG_MAX_ROWS, maxRows);
            }
            // 取数后写出前检查
            checkCancelled(cancelChecker);
            totalRows += result.getRows().size();
            String sheetName = sanitizeSheetName(panel.getDisplayName() != null
                    ? panel.getDisplayName() : panel.getPanelName(), usedNames);
            workbook.addSheet(toSheet(sheetName, result.getColumns(), result.getRows(), cancelChecker));
        }

        String baseName = "dashboard-" + sanitizeFileName(dashboardId);
        return writeXlsx(baseName, workbook, totalRows);
    }

    // ==================== 写出实现 ====================

    private ExportFile writeCsv(String baseName, List<String> columns, List<Map<String, Object>> rows,
                                BooleanSupplier cancelChecker) {
        // 写到临时 IResource；先写 UTF-8 BOM，再用 CsvRecordOutput 写表头+数据
        IResource resource = ResourceHelper.getTempResource("datav-export");
        OutputStream os = null;
        CsvRecordOutput<Map<String, Object>> output = null;
        try {
            os = resource.getOutputStream();
            os.write(UTF8_BOM);
            Writer writer = new OutputStreamWriter(os, StringHelper.ENCODING_UTF8);
            output = new CsvRecordOutput<>(writer, org.apache.commons.csv.CSVFormat.DEFAULT);
            output.setHeaders(columns);
            for (Map<String, Object> row : rows) {
                // 写出循环内检查
                checkCancelled(cancelChecker);
                output.write(row);
            }
            output.flush();
            writer.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(output);
            IoHelper.safeCloseObject(os);
        }
        return new ExportFile(resource, baseName + ".csv", MIME_CSV, rows.size());
    }

    private ExportFile writeXlsx(String baseName, ExcelSheet singleSheet) {
        ExcelWorkbook workbook = new ExcelWorkbook();
        workbook.addSheet(singleSheet);
        return writeXlsx(baseName, workbook, -1);
    }

    private ExportFile writeXlsx(String baseName, ExcelWorkbook workbook, long rowCountOverride) {
        IResource resource = ResourceHelper.getTempResource("datav-export");
        try {
            ExcelHelper.saveExcel(resource, workbook);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        long rowCount = rowCountOverride >= 0 ? rowCountOverride : countRows(workbook);
        return new ExportFile(resource, baseName + ".xlsx", MIME_XLSX, rowCount);
    }

    private long countRows(ExcelWorkbook workbook) {
        long total = 0;
        for (ExcelSheet sheet : workbook.getSheets()) {
            int rows = sheet.getTable().getRowCount();
            // 减去表头行
            if (rows > 0) {
                total += rows - 1;
            }
        }
        return total;
    }

    /**
     * 构造单 sheet（表头 + 数据行）。采用 {@code table.setCell(rowIndex, colIndex, cell)}
     * 的成熟写法（参考 {@code TestExcelHelper.testXlsxToCsv}），避免 addRow 触发 freeze 检查问题。
     */
    private ExcelSheet toSheet(String sheetName, List<String> columns, List<Map<String, Object>> rows,
                               BooleanSupplier cancelChecker) {
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName(sheetName);
        ExcelTable table = sheet.getTable();

        // 表头行（rowIndex=0）
        for (int c = 0; c < columns.size(); c++) {
            ExcelCell cell = new ExcelCell();
            cell.setValue(columns.get(c));
            table.setCell(0, c, cell);
        }

        // 数据行（rowIndex 从 1 开始）
        for (int r = 0; r < rows.size(); r++) {
            // 写出循环内检查
            checkCancelled(cancelChecker);
            Map<String, Object> dataRow = rows.get(r);
            for (int c = 0; c < columns.size(); c++) {
                ExcelCell cell = new ExcelCell();
                cell.setValue(dataRow.get(columns.get(c)));
                table.setCell(r + 1, c, cell);
            }
        }
        return sheet;
    }

    private static String sanitizeFileName(String name) {
        String s = name == null ? "export" : name;
        s = StringHelper.safeFileName(s);
        return s.isEmpty() ? "export" : s;
    }

    private static String sanitizeSheetName(String name, List<String> used) {
        String s = sanitizeFileName(name);
        if (s.length() > 31) {
            s = s.substring(0, 31);
        }
        String base = s;
        int suffix = 1;
        while (used.contains(s)) {
            suffix++;
            String num = String.valueOf(suffix);
            int max = 31 - num.length();
            s = (base.length() > max ? base.substring(0, max) : base) + num;
        }
        used.add(s);
        return s;
    }

    /**
     * 导出文件产物：临时 IResource（内容已写好）+ 文件名 + mimeType + 行数。
     * 调用方负责经 IFileStore.saveFile 落盘后删除临时资源。
     */
    public static final class ExportFile {
        private final IResource resource;
        private final String fileName;
        private final String mimeType;
        private final long rowCount;

        public ExportFile(IResource resource, String fileName, String mimeType, long rowCount) {
            this.resource = resource;
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.rowCount = rowCount;
        }

        public IResource getResource() {
            return resource;
        }

        public String getFileName() {
            return fileName;
        }

        public String getMimeType() {
            return mimeType;
        }

        public long getRowCount() {
            return rowCount;
        }
    }
}
