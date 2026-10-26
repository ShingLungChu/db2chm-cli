package com.db2chm;

import com.db2chm.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 程序入口.
 *
 * <p>无参数 → 启动 Swing GUI;
 * 第一个参数为 --cli → 命令行模式(批量/自动化), 用法见 {@link CliRunner}.</p>
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0) {
            int skip = "--cli".equals(args[0]) ? 1 : 0;
            String[] rest = new String[args.length - skip];
            System.arraycopy(args, skip, rest, 0, rest.length);
            try {
                System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
                System.setErr(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err), true, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException impossible) { throw new IllegalStateException(impossible); }
            System.exit(CliRunner.run(rest));
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { /* keep default */ }

        SwingUtilities.invokeLater(() -> {
            MainFrame f = new MainFrame();
            f.setVisible(true);
        });
    }
}
