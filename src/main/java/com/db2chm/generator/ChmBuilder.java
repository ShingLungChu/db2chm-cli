package com.db2chm.generator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * CHM 工程文件生成 + 调 hhc.exe 编译.
 *
 * <p>工作流程:
 * <ol>
 *   <li>调用 HtmlGenerator 把页面写到 outDir</li>
 *   <li>写 outDir/database.hhp / contents.hhc / index.hhk</li>
 *   <li>调 hhc.exe outDir\\database.hhp 编译出 outDir\\database.chm</li>
 * </ol>
 *
 * <p>hhc.exe 查找顺序:
 * <ol>
 *   <li>程序目录 tools/hhc/hhc.exe (内置随程序分发)</li>
 *   <li>系统安装的 HTML Help Workshop</li>
 *   <li>PATH 环境变量</li>
 * </ol></p>
 */
public class ChmBuilder {

    private final File outDir;
    private final File hhpFile;
    private final File hhcFile;
    private final File hhkFile;
    private final File chmFile;

    private String title = "数据库字典文档";
    private String compiledFileName = "database.chm";
    private List<String> htmlFiles;
    private Map<String, String> pageMap;  // 表名 -> 文件名

    /**
     * 工程文件编码.
     * 固定系统 ANSI(中文 Windows = GBK): hhc.exe 按 ANSI 代码页读 .hhp/.hhc/.hhk,
     * UTF-8 写出会编译出乱码目录/标题.
     */
    private Charset charset = com.db2chm.util.AnsiCharsets.ansi();

    public ChmBuilder(File outDir) {
        this.outDir = outDir;
        if (!outDir.exists()) outDir.mkdirs();
        this.hhpFile = new File(outDir, "database.hhp");
        this.hhcFile = new File(outDir, "contents.hhc");
        this.hhkFile = new File(outDir, "index.hhk");
        this.chmFile  = new File(outDir, "database.chm");
    }

    public void setTitle(String title) {
        if (title == null || title.length() > 512 || title.chars().anyMatch(c -> c < 32 || c == 127)) throw new IllegalArgumentException("Invalid CHM title");
        this.title = title;
    }
    /** 仅供测试覆盖; 正常链路由 HtmlGenerator 传入 ANSI 编码. */
    public void setCharset(Charset charset) {
        if (charset != null) this.charset = charset;
    }

    public File getHhpFile()  { return hhpFile; }
    public File getHhcFile()  { return hhcFile; }
    public File getHhkFile()  { return hhkFile; }
    public File getChmFile()  { return chmFile; }

    public void setHtmlFiles(List<String> htmlFiles) { this.htmlFiles = htmlFiles; }
    public void setPageMap(Map<String, String> pageMap) { this.pageMap = pageMap; }

    /* ============ 工程文件写入 ============ */

    public void writeHhp() throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(hhpFile.toPath(), charset)) {
            w.write("[OPTIONS]\n");
            w.write("Binary TOC=Yes\n");
            w.write("Compatibility=1.1 or later\n");
            w.write("Compiled file=" + compiledFileName + "\n");
            w.write("Contents file=contents.hhc\n");
            w.write("Index file=index.hhk\n");
            w.write("Default topic=index.html\n");
            w.write("Display compile progress=No\n");
            w.write("Full-text search=Yes\n");
            w.write("Language=0x804\n");   // 简体中文
            w.write("Title=" + title + "\n");
            w.write("\n[FILES]\n");
            for (String f : htmlFiles) w.write(f + "\n");
            // 样式表也要打进 CHM, 否则打开后无样式
            if (new File(outDir, "styles.css").exists()) {
                w.write("styles.css\n");
            }
            w.write("\n[INFOTYPES]\n");
        }
    }

    public void writeHhc() throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(hhcFile.toPath(), charset)) {
            w.write("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML//EN\">\n");
            w.write("<HTML>\n<HEAD>\n");
            w.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=" + charset.name() + "\">\n");
            w.write("<meta name=\"GENERATOR\" content=\"db2chm\"></HEAD>\n<BODY>\n");
            w.write("<OBJECT type=\"text/site properties\">\n");
            w.write("  <param name=\"Window Styles\" value=\"0x800025\">\n");
            w.write("  <param name=\"ImageType\" value=\"Folder\">\n");
            w.write("</OBJECT>\n");
            w.write("<UL>\n");
            w.write(item("封面", "index.html"));
            w.write(item("表目录", "catalog.html"));
            w.write(item("表结构", null));
            w.write("<UL>\n");
            if (pageMap != null) {
                for (Map.Entry<String, String> e : pageMap.entrySet()) {
                    w.write(item(e.getKey(), e.getValue()));
                }
            }
            w.write("</UL>\n");
            w.write("</LI>\n");
            w.write("</UL>\n");
            w.write("</BODY>\n</HTML>\n");
        }
    }

    public void writeHhk() throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(hhkFile.toPath(), charset)) {
            w.write("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML//EN\">\n");
            w.write("<HTML>\n<HEAD>\n");
            w.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=" + charset.name() + "\">\n");
            w.write("<meta name=\"GENERATOR\" content=\"db2chm\"></HEAD>\n<BODY>\n");
            w.write("<UL>\n");
            if (pageMap != null) {
                for (Map.Entry<String, String> e : pageMap.entrySet()) {
                    w.write(item(e.getKey(), e.getValue()));
                }
            }
            w.write("</UL>\n");
            w.write("</BODY>\n</HTML>\n");
        }
    }

    /** 写一个 <LI><OBJECT>...</OBJECT> 项. local 为 null 视为分组. */
    private static String item(String name, String local) {
        StringBuilder sb = new StringBuilder();
        sb.append("<LI> <OBJECT type=\"text/sitemap\">\n");
        sb.append("  <param name=\"Name\" value=\"").append(esc(name)).append("\">\n");
        if (local != null) {
            sb.append("  <param name=\"Local\" value=\"").append(esc(local)).append("\">\n");
        }
        sb.append("  </OBJECT>\n");
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /* ============ 编译调用 ============ */

    /**
     * 查找 hhc.exe, 优先程序内置 tools/hhc/.
     * 返回 null 表示未找到.
     */
    public static String locateHhc() {
        // 0. 显式指定: -Ddb2chm.hhc=... 或环境变量 DB2CHM_HHC
        String override = System.getProperty("db2chm.hhc");
        if (override == null || override.isEmpty()) override = System.getenv("DB2CHM_HHC");
        if (override != null && new File(override).exists()) return override;

        // 1. 程序运行目录 / jar 所在目录 下的 tools/hhc/hhc.exe
        for (File base : appDirs()) {
            File f = new File(base, "tools" + File.separator + "hhc" + File.separator + "hhc.exe");
            if (f.exists()) return f.getAbsolutePath();
        }

        // 2. 系统安装的 HTML Help Workshop
        String[] candidates = {
                System.getenv("ProgramFiles(x86)") + "\\HTML Help Workshop\\hhc.exe",
                System.getenv("ProgramFiles") + "\\HTML Help Workshop\\hhc.exe",
                "C:\\Program Files (x86)\\HTML Help Workshop\\hhc.exe",
                "C:\\Program Files\\HTML Help Workshop\\hhc.exe"
        };
        for (String p : candidates) {
            if (p == null) continue;
            File f = new File(p);
            if (f.exists()) return p;
        }

        // 3. PATH
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (dir == null || dir.isEmpty()) continue;
                File f = new File(dir, "hhc.exe");
                if (f.exists()) return f.getAbsolutePath();
            }
        }
        return null;
    }

    /** 程序可能的基准目录: 当前工作目录 + fat jar 所在目录. */
    private static List<File> appDirs() {
        java.util.List<File> dirs = new java.util.ArrayList<>();
        dirs.add(new File(".").getAbsoluteFile());
        try {
            File jar = new File(ChmBuilder.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            dirs.add(jar.getParentFile());
        } catch (Exception ignored) { /* 忽略 */ }
        return dirs;
    }

    /**
     * 调 hhc.exe 编译. 返回 stdout/stderr 拼接结果.
     * 若未找到 hhc, 返回 null 表示"未尝试".
     */
    public String compile() throws IOException, InterruptedException {
        String hhc = locateHhc();
        if (hhc == null) {
            return null;
        }
        // 清掉旧 CHM: hhc 无法覆盖被占用的文件, 旧的残留会导致"编译成功"假象.
        // 删除失败(文件被帮助查看器锁定)时给出明确提示.
        if (chmFile.exists() && !chmFile.delete()) {
            return "旧 database.chm 被占用(可能正被 Windows 帮助查看器打开), 本次未重新编译.\n"
                 + "请关闭打开该 CHM 的窗口后重试.\n[hhc skipped]\n";
        }
        ProcessBuilder pb = new ProcessBuilder(hhc, hhpFile.getAbsolutePath());
        pb.directory(outDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, charset));
        }
        int code = p.waitFor();
        sb.append("\n[hhc exit code=").append(code).append("]\n");
        return sb.toString();
    }

    /** 列出已渲染的 HTML 文件(相对 outDir). */
    public List<String> listHtmlFiles() {
        File[] fs = outDir.listFiles((d, n) -> n.endsWith(".html"));
        if (fs == null) return java.util.Collections.emptyList();
        return Stream.of(fs).map(File::getName).sorted().collect(java.util.stream.Collectors.toList());
    }
}
