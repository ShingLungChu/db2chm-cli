package com.db2chm;

import com.db2chm.db.ConnectionInfo;
import com.db2chm.db.DatabaseType;
import com.db2chm.db.DbAdapter;
import com.db2chm.db.DbAdapterFactory;
import com.db2chm.db.MockDbAdapter;
import com.db2chm.db.TableInfo;
import com.db2chm.generator.ChmBuilder;
import com.db2chm.generator.HtmlGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 命令行模式. 用于批量出字典文档 / 自动化测试, 不启动 GUI.
 *
 * <pre>
 * 用法一(连真实库):
 *   java -jar db2chm.jar --cli \
 *        --type DM \
 *        --driver dm.jdbc.driver.DmDriver \
 *        --url  jdbc:dm://localhost:5236 \
 *        --user dbuser \
 *        --password ****** \
 *        --charset UTF-8 \
 *        --out   ./out/dict_dm \
 *        [--title 自定义标题] [--html-only] [--max-tables 20]
 *
 * 用法二(挡板测试, 无需数据库):
 *   java -jar db2chm.jar --cli --mock --type ORACLE \
 *        [--tables 10] [--charset GBK] \
 *        --out D:/output/mock_oracle
 *
 *   --type 取值: ORACLE / MYSQL / DM / VASTBASE / KINGBASE
 * </pre>
 */
public final class CliRunner {

    private CliRunner() {}

    public static int run(String[] args) {
        ConnectionInfo ci = new ConnectionInfo();
        String out = "output";
        String title = null;
        boolean htmlOnly = false;
        boolean mock = false;
        int maxTables = -1;
        int mockTables = -1;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            String next = (i + 1 < args.length) ? args[i + 1] : null;
            switch (a) {
                case "--type":     ci.setDbType(DatabaseType.byName(next)); i++; break;
                case "--driver":   ci.setDriverClass(next); i++; break;
                case "--url":      ci.setUrl(next); i++; break;
                case "--user":     ci.setUser(next); i++; break;
                case "--password": ci.setPassword(next); i++; break;
                case "--charset":  ci.setCharset(next); i++; break;
                case "--schema":   ci.setSchema(next); i++; break;
                case "--out":      out = next; i++; break;
                case "--title":    title = next; i++; break;
                case "--html-only": htmlOnly = true; break;
                case "--mock":     mock = true; break;
                case "--tables":
                    try { mockTables = Integer.parseInt(next == null ? "-1" : next); } catch (Exception ignored) {}
                    i++;
                    break;
                case "--max-tables":
                    try { maxTables = Integer.parseInt(next == null ? "-1" : next); } catch (Exception ignored) {}
                    i++;
                    break;
                default:
                    System.err.println("未知参数: " + a);
                    return 2;
            }
        }

        if (mock) {
            if (ci.getDbType() == null) {
                System.err.println("挡板模式需要 --type 指定数据库类型(影响模拟数据的方言)");
                return 2;
            }
            if (ci.getUrl() == null || ci.getUrl().isEmpty()) {
                ci.setUrl("mock://stub/MOCK_DB");
            }
            if (ci.getUser() == null || ci.getUser().isEmpty()) {
                ci.setUser("MOCK_USER");
            }
            return runPipeline(new MockDbAdapter(ci, mockTables), ci, out, title, htmlOnly, maxTables, true);
        }

        if (ci.getDbType() == null || ci.getUrl() == null || ci.getUrl().isEmpty()) {
            System.err.println("缺少 --type 或 --url 参数");
            System.err.println("连库示例: java -jar db2chm.jar --cli --type DM --url jdbc:dm://localhost:5236 --user dbuser --password xxx --out D:/out");
            System.err.println("挡板示例: java -jar db2chm.jar --cli --mock --type ORACLE --tables 10 --out ./out-mock");
            return 2;
        }

        DbAdapter adapter = null;
        try {
            adapter = DbAdapterFactory.create(ci.getDbType(), ci);
            return runPipeline(adapter, ci, out, title, htmlOnly, maxTables, false);
        } finally {
            if (adapter != null) adapter.close();
        }
    }

    /** 连接之后的公共流水线: 拉表 → 渲染 HTML → 编译 CHM. */
    private static int runPipeline(DbAdapter adapter, ConnectionInfo ci, String out,
                                   String title, boolean htmlOnly, int maxTables, boolean mock) {
        try {
            if (mock) {
                System.out.println("[挡板模式] " + ci.getDbType().displayName()
                        + " | 模拟数据, 不连接数据库");
            } else {
                System.out.println("[1/5] 连接数据库: " + ci.describe());
                adapter.connect();
                System.out.println("      连接成功");
            }

            System.out.println("[2/5] 拉取表清单 ...");
            List<TableInfo> tables = adapter.listTables();
            if (maxTables >= 0 && tables.size() > maxTables) {
                tables = new ArrayList<>(tables.subList(0, maxTables));
                System.out.println("      (限制前 " + maxTables + " 张)");
            }
            System.out.println("      共 " + tables.size() + " 张表");

            System.out.println("[3/5] 读取表结构(逐表取列) ...");
            for (int i = 0; i < tables.size(); i++) {
                TableInfo raw = tables.get(i);
                TableInfo full;
                try {
                    full = adapter.getTableColumns(raw.getName());
                } catch (Exception e) {
                    throw new Exception("读取表「" + raw.getName() + "」的字段失败: "
                            + e.getMessage(), e);
                }
                if (full == null) {
                    throw new Exception("读取表「" + raw.getName() + "」的字段失败: 数据库未返回表结构。");
                }
                if (full.getColumnCount() == 0) {
                    throw new Exception("表「" + raw.getName() + "」未读取到任何字段，已取消生成。"
                            + "请检查 schema 配置和元数据查询权限。");
                }
                full.setComment(raw.getComment() == null || raw.getComment().isEmpty()
                        ? full.getComment() : raw.getComment());
                tables.set(i, full);
                if ((i + 1) % 50 == 0) System.out.println("      ... " + (i + 1) + "/" + tables.size());
            }

            File outDir = new File(out);
            System.out.println("[4/5] 渲染 HTML → " + outDir.getAbsolutePath());
            HtmlGenerator gen = new HtmlGenerator();
            Map<String, String> pageMap = gen.renderAll(outDir, ci, tables);

            if (htmlOnly) {
                System.out.println("[5/5] 跳过编译(--html-only), 完成。");
                return 0;
            }

            System.out.println("[5/5] 编译 CHM ...");
            ChmBuilder cb = new ChmBuilder(outDir);
            cb.setTitle(title != null ? title
                    : (mock ? "[挡板] " : "") + "全量数据字典");
            cb.setCharset(gen.getOutputCharset());
            cb.setPageMap(pageMap);
            cb.setHtmlFiles(cb.listHtmlFiles());
            cb.writeHhp();
            cb.writeHhc();
            cb.writeHhk();

            String hhc = ChmBuilder.locateHhc();
            if (hhc == null) {
                System.out.println("      ⚠ 未找到 hhc.exe, 仅输出 HTML + 工程文件。");
                System.out.println("      可将 tools/hhc 目录放到运行目录后重试, 或安装 HTML Help Workshop。");
                return 1;
            }
            System.out.println("      hhc: " + hhc);
            String log = cb.compile();
            if (log != null) System.out.println("      " + log.replace("\n", "\n      "));

            if (cb.getChmFile().exists()) {
                System.out.println("✓ CHM 已生成: " + cb.getChmFile().getAbsolutePath()
                        + "  (" + (cb.getChmFile().length() / 1024) + " KB)");
                return 0;
            } else {
                System.out.println("✗ CHM 未生成, 请查看上方 hhc 输出。");
                return 1;
            }
        } catch (Exception e) {
            System.err.println("✗ 失败: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }
}
