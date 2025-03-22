# Reduced

# Set Partition Language (SPL)

SPL Introduction Video: [No RDB敢揽 SQL 活的开源金刚钻 SPL](https://www.bilibili.com/video/BV18a411m7M5/)
SPL Reference Document: [Set Partition - Agile Computing Language](http://www.raqsoft.com.cn/p/esproc-spl)

Nop Platform Integration of SPL  
[Introduction Video](https://www.bilibili.com/video/BV1Km4y1m7y2/)

Integration of SPL into the Nop platform requires importing the nop-report-spl module. In the program, SPL can be called in several ways:

1. Call `SplExecutor.executeForPath(IEvalScope scope, String path)` to execute an SPL file.  
   - `path` is the virtual path corresponding to a SPL or SPLX file.

2. Directly call `SplExecutor.executeSPL(IEvalScope scope, String splSource)` to execute an SPL statement.

3. In XPL Template Language, you can use the `<spl:Execute src="xxx.splx" xpl:return="result"/>` tag to call it.

4. In NopReport reporting engine, you can use the following method to call an SPL statement and construct a dataset:

```xml
<spl:MakeDataSet xpl:lib="/nop/report/spl/spl.xlib" dsName="ds1" src="/nop/report/demo/spl/test-data.splx" />
```