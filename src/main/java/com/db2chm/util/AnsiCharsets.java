package com.db2chm.util;

import java.nio.charset.Charset;

/**
 * CHM 输出编码工具.
 *
 * <p>背景: hhc.exe / hh.exe (HTML Help) 是 ANSI 程序, 读取 .hhp/.hhc/.hhk 工程文件
 * 以及目录树字符串时按系统 ANSI 代码页(简体中文 Windows = GBK)解析.
 * 若工程文件以 UTF-8 写出, 编译进 CHM 的目录条目/窗口标题会变成乱码
 * (例如 "[挡板]" 编译后显示为 "[鎸℃澘]"). 因此所有 CHM 相关输出
 * (工程文件 + HTML 页面) 必须统一使用系统 ANSI 编码.</p>
 *
 * <p>HTML 正文页虽然带 meta charset 时 hh.exe 也能渲染 UTF-8, 但老版本渲染器
 * 行为不一致, 中文 CHM 的业界标准做法是全部 GBK, 故一并统一.</p>
 */
public final class AnsiCharsets {

    private AnsiCharsets() {}

    /**
     * 取系统 ANSI 编码.
     * 解析顺序:
     * <ol>
     *   <li>-Ddb2chm.ansi-charset=... 显式指定</li>
     *   <li>JVM 默认编码(file.encoding), 中文 Windows 的 JDK8 默认即 GBK</li>
     *   <li>兜底 GBK / GB18030</li>
     * </ol>
     */
    public static Charset ansi() {
        String prop = System.getProperty("db2chm.ansi-charset");
        if (prop != null && !prop.trim().isEmpty()) {
            try {
                return Charset.forName(prop.trim());
            } catch (Exception ignored) { /* 非法名, 继续往后找 */ }
        }

        Charset def = Charset.defaultCharset();
        String name = def.name();
        boolean unicodeOrAscii = name.contains("UTF") || "US-ASCII".equals(name);
        if (!unicodeOrAscii) {
            return def; // 典型: 中文 Windows JDK8 的 GBK
        }
        // JVM 默认是 UTF-8/ASCII (无法代表 ANSI 代码页) 时兜底中文 GBK
        try {
            return Charset.forName("GBK");
        } catch (Exception e) {
            return Charset.forName("GB18030");
        }
    }
}
