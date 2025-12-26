package io.nop.ooxml.xlsx.parse;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

public class DebugChartTest {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void debugChartParsing() {
        IResource resource = new ClassPathResource("classpath:xlsx/test-bar-chart.xlsx");
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);

        List<ExcelSheet> sheets = wk.getSheets();
        System.out.println("Number of sheets: " + sheets.size());

        for (int i = 0; i < sheets.size(); i++) {
            ExcelSheet sheet = sheets.get(i);
            System.out.println("Sheet " + i + " name: " + sheet.getName());
            
            List<ExcelChartModel> charts = sheet.getCharts();
            System.out.println("Number of charts in sheet " + i + ": " + (charts != null ? charts.size() : "null"));
            
            if (charts != null && !charts.isEmpty()) {
                for (int j = 0; j < charts.size(); j++) {
                    ExcelChartModel chart = charts.get(j);
                    System.out.println("Chart " + j + " name: " + chart.getName());
                    System.out.println("Chart " + j + " type: " + chart.getType());
                    System.out.println("Chart " + j + " title: " + chart.getTitle());
                }
            }
        }
    }
}