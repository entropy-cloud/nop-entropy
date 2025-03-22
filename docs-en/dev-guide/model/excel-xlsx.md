# 1. Parse Excel File to Get Workbook Object

```python
ExcelWorkbook wk = new ExcelWorkbookParser().parseFromVirtualPath(path);
or
wk = new ExcelWorkbookParser().parseFromResource(new FileResource(file));
```


## 2. Parse Excel File to Get Domain Object

Based on imp.xml import settings, automatically parse Excel file into domain model object.

```python
FileResource resource = new FileResource(new File("c:/test.orm.xlsx"));
OrmModel ormModel = (OrmModel) new XlsxObjectLoader("/nop/orm/imp/orm.imp.xml").parseFromResource(resource);
```


## 3. Save Workbook Object to File

```python
new ExcelTemplate(workbook).generateToFile(file, DisabledEvalScope.INSTANCE);
```


## 4. Convert Workbook to HTML Format

```python
ITemplateOutput output = reportEngine.getRendererForExcel(workbook, XptConstants.RENDER_TYPE_HTML);
output.generateToFile(file, DisabledEvalScope.INSTANCE);

or

ITextTemplateOutput output = reportEngine.getHtmlRenderer(workbook);
String html = output.generateText(DisabledEvalScope.INSTANCE);
```


## 5. Save Domain Object to XLSX Format

Refer to GenOrmHelper.saveOrmToExcel function.

```python
IEvalScope scope = XLang.newEvalScope();
scope.setLocalValue(null, XptConstants.VAR_ENTITY, ormModel);

ExcelWorkbook workbook = reportEngine.buildXptModelFromImpModel("/nop/orm/imp/orm.imp.xml");
ITemplateOutput output = reportEngine.getRendererForXptModel(workbook, "xlsx");
output.generateToFile(outputFile, scope);
```


## 6. Parse ExcelSheet to Get Domain Object

```python
ExcelSheet configSheet = workbook.getSheet("Config");
XLangCompileTool compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);

ImportModel importModel = ImportModelHelper.getImportModel(RuleConstants.IMP_PATH_RULE);
ImportSheetModel sheetModel = importModel.getSheet(RuleConstants.SHEET_NAME_CONFIG);
RuleModel rule = ImportModelHelper.parseSheet(sheetModel, configSheet, compileTool, RuleModel.class);
```

