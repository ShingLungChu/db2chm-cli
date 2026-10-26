package com.db2chm.generator;

import com.db2chm.db.ColumnInfo;
import com.db2chm.db.ConnectionInfo;
import com.db2chm.db.TableInfo;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HTML 生成器. 把表清单渲染成 封面 + 表目录 + 每表一个详情页.
 *
 * <p>输出到 outDir 下的相对路径结构:
 * <pre>
 *   outDir/index.html
 *   outDir/catalog.html
 *   outDir/table_1.html, table_2.html, ...
 *   outDir/styles.css
 * </pre></p>
 */
public class HtmlGenerator {

    private final Configuration cfg;

    /**
     * 输出文件编码.
     * 固定使用系统 ANSI 编码(中文 Windows = GBK): hhc.exe/hh.exe 是 ANSI 程序,
     * UTF-8 输出会导致 CHM 目录树/标题乱码. 用户在界面选择的字符集仅用于数据库
     * 连接, 不影响 CHM 产物编码.
     */
    private Charset outputCharset = com.db2chm.util.AnsiCharsets.ansi();

    public HtmlGenerator() {
        cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(this.getClass().getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocale(Locale.CHINA);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
    }

    public void setOutputCharset(String charsetName) {
        if (charsetName != null && !charsetName.trim().isEmpty()) {
            try {
                this.outputCharset = Charset.forName(charsetName.trim());
            } catch (Exception ignored) { /* 非法名保持默认 */ }
        }
    }

    public Charset getOutputCharset() { return outputCharset; }

    /** 把 CSS 也复制到输出目录(已存在则覆盖, 保证重复导出不崩溃). */
    public void copyAssets(File outDir) throws IOException {
        File css = new File(outDir, "styles.css");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("templates/styles.css")) {
            if (in != null) {
                Files.copy(in, css.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * 渲染所有页面. 返回页面文件名映射(表名 -> 文件名), 给 CHM 编译器用.
     */
    public Map<String, String> renderAll(File outDir, ConnectionInfo conn,
                                        List<TableInfo> tables) throws Exception {
        if (!outDir.exists()) outDir.mkdirs();
        copyAssets(outDir);
        // 注意: 不再用 conn.getCharset() 决定输出编码 —— CHM 输出恒为 ANSI(GBK),
        // 用户选择的字符集只作用于数据库连接.
        // 输出编码若被显式改过(测试用), 这里不覆盖.

        String dbName = conn.getDisplayName();
        String dbType = conn.getDbType() == null ? "" : conn.getDbType().displayName();
        String connInfo = conn.describe();
        String generatedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String version = "0.2.0";
        String charsetName = outputCharset.name();

        // 1. 封面
        Map<String, Object> coverModel = new HashMap<>();
        // 封面专用字段: 标题固定, 仅暴露日期 + 数据库类型, 不含 dbName/connInfo.
        coverModel.put("title", "全量数据字典");
        // 下方保留 dbName/connInfo/tableCount 给模板需要时备用, 但 cover.ftl 不再引用.
        coverModel.put("dbName", dbName);
        coverModel.put("dbType", dbType);
        coverModel.put("connInfo", connInfo);
        coverModel.put("tableCount", tables.size());
        coverModel.put("generatedAt", generatedAt);
        coverModel.put("version", version);
        coverModel.put("charset", charsetName);
        writePage(outDir, "index.html", "cover.ftl", coverModel);

        // 2. 目录页(表名 -> pageFile 映射)
        List<Map<String, Object>> catRows = new ArrayList<>();
        Map<String, String> pageMap = new LinkedHashMap<>();
        int seq = 0;
        for (TableInfo t : tables) {
            seq++;
            String fileName = "table_" + seq + ".html";
            pageMap.put(t.getName(), fileName);
            Map<String, Object> row = new HashMap<>();
            row.put("seq", seq);
            row.put("name", t.getName());
            row.put("comment", t.getComment());
            row.put("pageFile", fileName);
            catRows.add(row);
        }
        Map<String, Object> catModel = new HashMap<>();
        catModel.put("dbName", dbName);
        catModel.put("tableCount", tables.size());
        catModel.put("tables", catRows);
        catModel.put("charset", charsetName);
        writePage(outDir, "catalog.html", "catalog.ftl", catModel);

        // 3. 表详情
        int idx = 0;
        for (TableInfo t : tables) {
            idx++;
            String fileName = "table_" + idx + ".html";

            List<Map<String, Object>> colRows = new ArrayList<>();
            for (ColumnInfo c : t.getColumns()) {
                Map<String, Object> r = new HashMap<>();
                r.put("seq", c.getSeq());
                r.put("name", c.getName());
                r.put("dataType", c.getDataType());
                r.put("length", c.getLength() == null ? "" : c.getLength());
                r.put("isPk", c.isPk());
                r.put("pkSeq", c.getPkSeq());
                r.put("isAutoIncrement", c.isAutoIncrement());
                r.put("nullable", c.isNullable());
                r.put("comment", c.getComment());
                colRows.add(r);
            }
            Map<String, Object> tplTable = new HashMap<>();
            tplTable.put("name", t.getName());
            tplTable.put("comment", t.getComment());
            tplTable.put("columnCount", t.getColumnCount());
            tplTable.put("columns", colRows);

            Map<String, Object> tm = new HashMap<>();
            tm.put("dbName", dbName);
            tm.put("dbType", dbType);
            tm.put("connInfo", connInfo);
            tm.put("generatedAt", generatedAt);
            tm.put("table", tplTable);
            tm.put("charset", charsetName);
            writePage(outDir, fileName, "table.ftl", tm);
        }

        return pageMap;
    }

    private void writePage(File outDir, String fileName, String tplName, Map<String, Object> model)
            throws Exception {
        Template tpl = cfg.getTemplate(tplName);
        File outFile = new File(outDir, fileName);
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(outFile.toPath()), outputCharset.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT))) {
            tpl.process(model, w);
        }
    }
}
