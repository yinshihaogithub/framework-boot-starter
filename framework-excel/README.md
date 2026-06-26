# framework-excel

> Excel 模块：基于 EasyExcel 提供导入、导出、模板生成和行级错误收集。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-excel</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 配置

配置前缀：`framework.excel`。

```yaml
framework:
  excel:
    enabled: true
    default-sheet-name: Sheet1
    max-rows: 100000
```

`max-rows` 必须大于 0；导出服务的 `default-sheet-name` 不能为空，且必须符合 Excel 工作表名限制（不超过 31 个字符，不包含 `[]:*?/\\` 或控制字符）。配置错误会在启动期快速抛出 `IllegalArgumentException`，避免导入导出运行中才暴露。

## 导出

导出会校验 `headClass`、`sheetName`、`rows` 和 `max-rows`，超出限制或 sheet 名非法时抛出清晰的 `IllegalArgumentException`。

```java
@Autowired
private ExcelExportService excelExportService;

byte[] bytes = excelExportService.export(UserExcelRow.class, rows);
```

## 导入

导入会先校验输入流是否为常见 Excel 文件头（xlsx/xls），并按 `rowClass` 上的 `@ExcelProperty` 校验模板表头；表头比较前会去除首尾空格，列名本身仍必须匹配。
无效文件、空输入流、表头不匹配和行解析异常都会进入 `ExcelImportResult.errors`，不直接抛给业务主流程。

```java
@Autowired
private ExcelImportService excelImportService;

ExcelImportResult<UserExcelRow> result =
        excelImportService.importExcel(inputStream, UserExcelRow.class);

if (result.hasErrors()) {
    return result.getErrors();
}
```
