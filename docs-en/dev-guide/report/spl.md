# Raqsoft SPL (SPL)

SPL introduction video: [Open-source SPL: the diamond drill that takes on SQL jobs without an RDB](https://www.bilibili.com/video/BV18a411m7M5/)
SPL reference documentation: [SPL - Agile Computing Programming Language](http://www.raqsoft.com.cn/p/esproc-spl)

SPL integration in the Nop platform [Introduction video](https://www.bilibili.com/video/BV1Km4y1m7y2/)

![](spl.png)

To integrate SPL into the Nop platform, you only need to include the nop-report-spl module. In code, you can invoke SPL in the following ways:

1. Call the SplExecutor.executeForPath(IEvalScope scope, String path) function to execute an SPL file. path is the virtual path corresponding to an SPL or SPLX file.
2. Directly call the SplExecutor.executeSPL(IEvalScope scope, String splSource) function to execute SPL statements.
3. In the XPL template language, you can invoke it via the tag library `<spl:Execute src="xxx.splx" xpl:return="result" />`
4. In the NopReport reporting engine, you can invoke SPL statements in the following way to construct a dataset

```xml
<spl:MakeDataSet xpl:lib="/nop/report/spl/spl.xlib" dsName="ds1" src="/nop/report/demo/spl/test-data.splx" />
```
<!-- SOURCE_MD5:26a70a09058f4aeef7bb353a22216068-->
