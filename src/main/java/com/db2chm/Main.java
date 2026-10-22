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
        if (args.length > 0 && "--cli".equals(args[0])) {
            String[] rest = new String[args.length - 1];
            System.arraycopy(args, 1, rest, 0, rest.length);
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
