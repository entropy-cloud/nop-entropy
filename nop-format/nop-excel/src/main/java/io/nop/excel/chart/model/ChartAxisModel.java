package io.nop.excel.chart.model;

import io.nop.excel.chart.IChartStyleSupportModel;
import io.nop.excel.chart.model._gen._ChartAxisModel;

public class ChartAxisModel extends _ChartAxisModel implements IChartStyleSupportModel {
    public ChartAxisModel(){

    }

    public ChartTicksModel makeTicks(){
        ChartTicksModel ticks = getTicks();
        if(ticks == null){
            ticks = new ChartTicksModel();
            setTicks(ticks);
        }
        return ticks;
    }
}
