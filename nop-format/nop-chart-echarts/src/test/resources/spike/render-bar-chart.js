/**
 * WI0 spike 渲染脚本：平台注入 → init → setOption。
 * 被测试类 eval 执行，执行完成经 globalThis.__spikeChartReady 回传 Java 侧。
 */
(function () {
    'use strict';

    echarts.setPlatformAPI({
        createCanvas: function () {
            return __spikeCreateCanvas(300, 150);
        },
        loadImage: function (src, onload, onerror) {
            return __spikeLoadImage(src, onload, onerror);
        }
    });

    var WIDTH = 600;
    var HEIGHT = 400;
    var root = __spikeCreateCanvas(WIDTH, HEIGHT);
    var chart = echarts.init(root, null, { renderer: 'canvas', width: WIDTH, height: HEIGHT });

    chart.setOption({
        animation: false,
        backgroundColor: '#ffffff',
        title: { text: '中文柱状图 PoC', left: 'center' },
        xAxis: { type: 'category', data: ['一月', '二月', '三月', '四月', '五月'] },
        yAxis: { type: 'value' },
        series: [{
            name: '销量',
            type: 'bar',
            data: [150, 230, 224, 218, 135],
            itemStyle: { color: '#5470c6' },
            label: { show: true }
        }]
    });

    globalThis.__spikeChartReady = true;
})();
