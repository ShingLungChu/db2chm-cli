package com.db2chm.ui;

import com.db2chm.db.ColumnInfo;
import com.db2chm.db.ConnectionInfo;
import com.db2chm.db.DatabaseType;
import com.db2chm.db.DbAdapter;
import com.db2chm.db.DbAdapterFactory;
import com.db2chm.db.MockDbAdapter;
import com.db2chm.db.TableInfo;
import com.db2chm.generator.ChmBuilder;
import com.db2chm.generator.HtmlGenerator;
import com.db2chm.util.ConfigStore;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * db2chm 主窗体(Swing). 连接表单按需求精简为 6 项:
 * <pre>
 *   ┌─────────────────────────────────────────────────────────┐
 *   │ 连接参数 (数据库类型 / 驱动类 / 链接串 / 用户名 / 密码 / 字符集) │
 *   ├──────────────────┬──────────────────────────────────────┤
 *   │ 表清单(可多选)     │ 表预览(列信息)                       │
 *   ├──────────────────┴──────────────────────────────────────┤
 *   │ 输出目录 + 生成 CHM + 进度条 + 日志                       │
 *   └─────────────────────────────────────────────────────────┘
 * </pre>
 */
public class MainFrame extends JFrame {

    private final ConfigStore configStore = new ConfigStore();

    /* ============ 连接参数控件 ============ */
    private JComboBox<DatabaseType> cbDbType;
    private JComboBox<String> cbDriver;      // 可编辑下拉
    private JTextField tfUrl;
    private JTextField tfUser;
    private JPasswordField pfPassword;
    private JComboBox<String> cbCharset;     // 可编辑下拉
    private JComboBox<ConnectionInfo> cbRecent;
    private JButton btnTestConn;
    private JButton btnLoadTables;
    private JButton btnMock;

    /* ============ 表/预览 ============ */
    private final DefaultListModel<String> tableListModel = new DefaultListModel<>();
    private JList<String> tableList;
    private DefaultTableModel previewModel;
    private JTable previewTable;

    /* ============ 输出 ============ */
    private JTextField tfOutputDir;
    private JButton btnChooseOut;
    private JButton btnGenerate;
    private JProgressBar progressBar;
    private JTextArea taLog;
    private JCheckBox cbHtmlOnly;

    /* ============ 状态 ============ */
    private DbAdapter currentAdapter;
    private List<TableInfo> currentTables;

    public MainFrame() {
        setTitle("db2chm - 数据库字典 CHM 生成器  v0.2.0");
        setSize(1180, 760);
        setMinimumSize(new Dimension(1100, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initComponents();
        layoutComponents();
        wireEvents();
        prefillFromConfig();
    }

    private void initComponents() {
        cbDbType = new JComboBox<>(DatabaseType.values());

        // 驱动类: 可编辑下拉, 切换数据库类型时预填默认值
        cbDriver = new JComboBox<>();
        cbDriver.setEditable(true);
        for (DatabaseType t : DatabaseType.values()) cbDriver.addItem(t.driverClass());

        tfUrl = new JTextField(46);
        tfUser = new JTextField(12);
        pfPassword = new JPasswordField(12);

        cbCharset = new JComboBox<>(new String[]{"UTF-8", "GBK", "GB2312"});
        cbCharset.setEditable(true);

        cbRecent = new JComboBox<>();
        cbRecent.setEditable(false);
        cbRecent.addItem(null);
        for (ConnectionInfo ci : configStore.getRecent()) cbRecent.addItem(ci);

        btnTestConn = new JButton("测试连接");
        btnLoadTables = new JButton("载入表清单");
        btnMock = new JButton("挡板测试(Mock)");

        tableList = new JList<>(tableListModel);
        tableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        Vector<String> cols = new Vector<>();
        cols.add("序号"); cols.add("列名"); cols.add("数据类型");
        cols.add("长度"); cols.add("主键"); cols.add("自增"); cols.add("允许空"); cols.add("列说明");
        previewModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        previewTable = new JTable(previewModel);
        previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        previewTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        previewTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        previewTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        previewTable.getColumnModel().getColumn(3).setPreferredWidth(70);
        previewTable.getColumnModel().getColumn(4).setPreferredWidth(50);
        previewTable.getColumnModel().getColumn(5).setPreferredWidth(50);
        previewTable.getColumnModel().getColumn(6).setPreferredWidth(60);
        previewTable.getColumnModel().getColumn(7).setPreferredWidth(280);

        String lastOut = configStore.getLastOutputDir();
        tfOutputDir = new JTextField(lastOut != null && !lastOut.isEmpty()
                ? lastOut : new File(".").getAbsolutePath() + "\\output", 32);
        btnChooseOut = new JButton("选择…");
        btnGenerate = new JButton("生成 CHM");
        cbHtmlOnly = new JCheckBox("仅生成 HTML（跳过编译）", false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        taLog = new JTextArea(6, 60);
        taLog.setEditable(false);
        taLog.setBackground(new Color(248, 250, 253));
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(8, 8));

        JPanel connPanel = buildConnPanel();
        add(connPanel, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        JScrollPane listPane = new JScrollPane(tableList);
        listPane.setBorder(BorderFactory.createTitledBorder("表清单（可多选，按住 Ctrl/Shift）"));
        listPane.setPreferredSize(new Dimension(360, 360));
        center.add(listPane, BorderLayout.WEST);

        JScrollPane previewPane = new JScrollPane(previewTable);
        previewPane.setBorder(BorderFactory.createTitledBorder("表结构预览"));
        center.add(previewPane, BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);

        JPanel south = buildSouthPanel();
        add(south, BorderLayout.SOUTH);
    }

    private JPanel buildConnPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("连接参数"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        c.gridy = row;
        c.gridx = 0; p.add(new JLabel("数据库:"), c);
        c.gridx = 1; p.add(cbDbType, c);
        c.gridx = 2; p.add(new JLabel("驱动类:"), c);
        c.gridx = 3; c.weightx = 1.0; p.add(cbDriver, c);
        c.weightx = 0;

        row++;
        c.gridy = row;
        c.gridx = 0; p.add(new JLabel("链接串:"), c);
        c.gridx = 1; c.gridwidth = 3; c.weightx = 1.0; p.add(tfUrl, c);
        c.gridwidth = 1; c.weightx = 0;

        row++;
        c.gridy = row;
        c.gridx = 0; p.add(new JLabel("用户名:"), c);
        c.gridx = 1; p.add(tfUser, c);
        c.gridx = 2; p.add(new JLabel("密码:"), c);
        c.gridx = 3; c.weightx = 1.0; p.add(pfPassword, c);
        c.weightx = 0;

        row++;
        c.gridy = row;
        c.gridx = 0; p.add(new JLabel("连接字符集:"), c);
        c.gridx = 1; p.add(cbCharset, c);
        c.gridx = 2; p.add(new JLabel("最近连接:"), c);
        c.gridx = 3; c.weightx = 1.0;
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        btnRow.add(cbRecent);
        btnRow.add(btnTestConn);
        btnRow.add(btnLoadTables);
        btnRow.add(btnMock);
        p.add(btnRow, c);
        c.weightx = 0;

        return p;
    }

    private JPanel buildSouthPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("生成 CHM"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridy = 0;
        p.add(new JLabel("输出目录:"), c);
        c.gridx = 1; c.weightx = 1.0; p.add(tfOutputDir, c);
        c.weightx = 0;
        c.gridx = 2; p.add(btnChooseOut, c);
        c.gridx = 3; p.add(cbHtmlOnly, c);
        c.gridx = 4; p.add(btnGenerate, c);

        c.gridy = 1;
        c.gridx = 0; c.gridwidth = 5;
        p.add(progressBar, c);
        c.gridwidth = 1;

        c.gridy = 2;
        c.gridx = 0; c.gridwidth = 5;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1.0;
        p.add(new JScrollPane(taLog), c);

        return p;
    }

    private void wireEvents() {
        // 数据库类型切换 → 预填默认驱动类与示例链接串
        cbDbType.addActionListener(e -> {
            DatabaseType t = (DatabaseType) cbDbType.getSelectedItem();
            if (t != null && cbDriver.getSelectedItem() == null) {
                cbDriver.setSelectedItem(t.driverClass());
            }
        });

        // 最近连接回填
        cbRecent.addActionListener(e -> {
            ConnectionInfo ci = (ConnectionInfo) cbRecent.getSelectedItem();
            if (ci != null) fillConn(ci);
        });

        btnTestConn.addActionListener(e -> doTestConn());
        btnLoadTables.addActionListener(e -> doLoadTables());
        btnChooseOut.addActionListener(e -> doChooseOutputDir());
        btnGenerate.addActionListener(e -> doGenerate());
        btnMock.addActionListener(e -> doMockLoad());

        // 选中表 → 预览
        tableList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = tableList.getSelectedValue();
                if (sel != null) loadTablePreview(sel);
            }
        });
    }

    private void prefillFromConfig() {
        DatabaseType t = (DatabaseType) cbDbType.getSelectedItem();
        if (t != null) {
            cbDriver.setSelectedItem(t.driverClass());
            tfUrl.setText(t.exampleUrl());
        }
    }

    /* ============ 行为 ============ */

    private ConnectionInfo collectConn() {
        DatabaseType dt = (DatabaseType) cbDbType.getSelectedItem();
        ConnectionInfo ci = new ConnectionInfo();
        ci.setDbType(dt);
        Object drv = cbDriver.getSelectedItem();
        ci.setDriverClass(drv == null ? null : String.valueOf(drv).trim());
        ci.setUrl(tfUrl.getText().trim());
        ci.setUser(tfUser.getText().trim());
        ci.setPassword(new String(pfPassword.getPassword()));
        Object cs = cbCharset.getSelectedItem();
        ci.setCharset(cs == null ? "UTF-8" : String.valueOf(cs).trim());
        return ci;
    }

    private void fillConn(ConnectionInfo ci) {
        if (ci.getDbType() != null) cbDbType.setSelectedItem(ci.getDbType());
        if (ci.getDriverClass() != null) cbDriver.setSelectedItem(ci.getDriverClass());
        tfUrl.setText(ci.getUrl() == null ? "" : ci.getUrl());
        tfUser.setText(ci.getUser() == null ? "" : ci.getUser());
        pfPassword.setText(ci.getPassword() == null ? "" : ci.getPassword());
        if (ci.getCharset() != null) cbCharset.setSelectedItem(ci.getCharset());
    }

    private void doTestConn() {
        ConnectionInfo ci = collectConn();
        if (ci.getUrl().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写 JDBC 链接串", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        btnTestConn.setEnabled(false);
        appendLog(">> 测试连接: " + ci.describe());
        new SwingWorker<Boolean, Void>() {
            String error;
            @Override protected Boolean doInBackground() {
                try {
                    DbAdapter a = DbAdapterFactory.create(ci.getDbType(), ci);
                    a.connect();
                    currentAdapter = a;
                    return true;
                } catch (Exception e) {
                    error = e.getMessage();
                    return false;
                }
            }
            @Override protected void done() {
                btnTestConn.setEnabled(true);
                try {
                    if (get()) {
                        appendLog("   ✓ 连接成功");
                        configStore.pushRecent(ci);
                        refreshRecentCombo();
                    } else {
                        appendLog("   ✗ 连接失败: " + error);
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "连接失败:\n" + error, "测试连接",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    private void doLoadTables() {
        if (currentAdapter == null) {
            JOptionPane.showMessageDialog(this, "请先点击「测试连接」", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        btnLoadTables.setEnabled(false);
        appendLog(">> 拉取表清单 ...");
        new SwingWorker<List<TableInfo>, Void>() {
            String error;
            @Override protected List<TableInfo> doInBackground() {
                try {
                    return currentAdapter.listTables();
                } catch (Exception e) {
                    error = e.getMessage();
                    return null;
                }
            }
            @Override protected void done() {
                btnLoadTables.setEnabled(true);
                try {
                    List<TableInfo> ts = get();
                    if (ts == null) {
                        appendLog("   ✗ 拉取失败: " + error);
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "拉取表清单失败:\n" + error, "错误",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    currentTables = ts;
                    tableListModel.clear();
                    for (TableInfo t : ts) tableListModel.addElement(t.getName());
                    appendLog("   ✓ 共 " + ts.size() + " 张表");
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    private void loadTablePreview(String tableName) {
        if (currentAdapter == null) return;
        final DbAdapter adapter = currentAdapter;
        new SwingWorker<TableInfo, Void>() {
            @Override protected TableInfo doInBackground() {
                try {
                    synchronized (adapter) {
                        return adapter.getTableColumns(tableName);
                    }
                } catch (Exception e) {
                    return null;
                }
            }
            @Override protected void done() {
                try {
                    TableInfo t = get();
                    if (t == null) return;
                    previewModel.setRowCount(0);
                    for (ColumnInfo c : t.getColumns()) {
                        previewModel.addRow(new Object[]{
                                c.getSeq(),
                                c.getName(),
                                c.getDataType(),
                                c.getLength(),
                                c.isPk() ? (c.getPkSeq() > 1 ? "PK(" + c.getPkSeq() + ")" : "PK") : "",
                                c.isAutoIncrement() ? "Y" : "",
                                c.isNullable() ? "Y" : "N",
                                c.getComment()
                        });
                    }
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    /** 挡板测试: 不连数据库, 用模拟表结构替换当前适配器, 走完整预览/导出流程. */
    private void doMockLoad() {
        DatabaseType dt = (DatabaseType) cbDbType.getSelectedItem();
        if (dt == null) {
            JOptionPane.showMessageDialog(this, "请先选择数据库类型", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 关闭旧连接, 换挡板适配器
        if (currentAdapter != null) {
            try { currentAdapter.close(); } catch (Exception ignored) { }
        }
        ConnectionInfo ci = collectConn();
        if (ci.getUrl() == null || ci.getUrl().isEmpty()) ci.setUrl("mock://stub/MOCK_DB");
        if (ci.getUser() == null || ci.getUser().isEmpty()) ci.setUser("MOCK_USER");
        MockDbAdapter mock = new MockDbAdapter(ci, 0);
        currentAdapter = mock;

        try {
            List<TableInfo> ts = mock.listTables();
            currentTables = ts;
            tableListModel.clear();
            for (TableInfo t : ts) tableListModel.addElement(t.getName());
            appendLog(">> [挡板模式] 已载入 " + dt.displayName() + " 模拟数据 " + ts.size()
                    + " 张表(含复合主键/自增/特殊字符注释样本), 可直接点「生成 CHM」验证全链路");
        } catch (Exception ex) {
            appendLog("   ✗ 挡板数据载入失败: " + ex.getMessage());
        }
    }

    private void doChooseOutputDir() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setCurrentDirectory(new File(tfOutputDir.getText()));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            tfOutputDir.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void doGenerate() {
        if (currentTables == null || currentTables.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先载入表清单", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<String> selected = tableList.getSelectedValuesList();
        List<TableInfo> toExport;
        if (selected == null || selected.isEmpty()) {
            toExport = currentTables;
        } else {
            toExport = new ArrayList<>();
            for (String n : selected) {
                for (TableInfo t : currentTables) {
                    if (t.getName().equals(n)) { toExport.add(t); break; }
                }
            }
        }
        if (toExport.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有可导出的表", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        File outDir = new File(tfOutputDir.getText().trim());
        if (!outDir.exists() && !outDir.mkdirs()) {
            JOptionPane.showMessageDialog(this, "输出目录创建失败: " + outDir, "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        configStore.setLastOutputDir(outDir.getAbsolutePath());

        final List<TableInfo> finalList = new ArrayList<>(toExport);
        final DbAdapter exportAdapter = currentAdapter;
        final ConnectionInfo connInfo = collectConn();
        final File fOut = outDir;
        final boolean htmlOnly = cbHtmlOnly.isSelected();

        btnGenerate.setEnabled(false);
        progressBar.setValue(0);
        appendLog(">> 开始生成 CHM ...");

        new SwingWorker<Void, String>() {
            String error;
            String compileLog;
            @Override protected Void doInBackground() {
                try {
                    publish("读取表结构（逐表取列）...");
                    List<TableInfo> tablesWithColumns = new ArrayList<>(finalList.size());
                    for (int i = 0; i < finalList.size(); i++) {
                        TableInfo raw = finalList.get(i);
                        TableInfo full;
                        try {
                            synchronized (exportAdapter) {
                                full = exportAdapter.getTableColumns(raw.getName());
                            }
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
                        if (raw.getComment() != null && !raw.getComment().isEmpty()) {
                            full.setComment(raw.getComment());
                        }
                        tablesWithColumns.add(full);
                        if ((i + 1) % 50 == 0 || i + 1 == finalList.size()) {
                            publish("已读取表结构 " + (i + 1) + "/" + finalList.size());
                        }
                    }

                    publish("渲染 HTML ...");
                    HtmlGenerator gen = new HtmlGenerator();
                    Map<String, String> pageMap = gen.renderAll(fOut, connInfo, tablesWithColumns);
                    publish("HTML 已写入: " + fOut.getAbsolutePath() + "  (" + tablesWithColumns.size() + " 张表)");
                    setProgress(60);

                    if (htmlOnly) {
                        publish("已选择仅生成 HTML, 跳过编译。");
                        return null;
                    }

                    publish("写 HHP / HHC / HHK 工程文件 ...");
                    ChmBuilder cb = new ChmBuilder(fOut);
                    cb.setTitle("全量数据字典");
                    cb.setCharset(gen.getOutputCharset());
                    cb.setPageMap(pageMap);
                    cb.setHtmlFiles(cb.listHtmlFiles());
                    cb.writeHhp();
                    cb.writeHhc();
                    cb.writeHhk();
                    setProgress(75);

                    String hhc = ChmBuilder.locateHhc();
                    if (hhc == null) {
                        publish("未检测到 hhc.exe, 跳过 CHM 编译。可将 tools/hhc 放到程序目录后重试。");
                        return null;
                    }

                    publish("调 hhc.exe 编译: " + hhc);
                    compileLog = cb.compile();
                    publish(compileLog == null ? "(无输出)" : compileLog);
                    if (cb.getChmFile().exists()) {
                        publish("✓ CHM 已生成: " + cb.getChmFile().getAbsolutePath());
                    } else {
                        publish("⚠ 未生成 " + cb.getChmFile().getName() + ", 请查看 hhc 输出排查问题。");
                    }
                    setProgress(100);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                return null;
            }

            @Override protected void process(List<String> chunks) {
                for (String s : chunks) appendLog("   " + s);
            }

            @Override protected void done() {
                btnGenerate.setEnabled(true);
                try {
                    get();
                    if (error != null) {
                        appendLog("   ✗ 失败: " + error);
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "生成失败:\n" + error, "错误",
                                JOptionPane.ERROR_MESSAGE);
                    } else {
                        appendLog(">> 完成");
                    }
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    private void refreshRecentCombo() {
        cbRecent.removeAllItems();
        cbRecent.addItem(null);
        for (ConnectionInfo ci : configStore.getRecent()) cbRecent.addItem(ci);
    }

    private void appendLog(String s) {
        SwingUtilities.invokeLater(() -> {
            taLog.append(s + "\n");
            taLog.setCaretPosition(taLog.getDocument().getLength());
        });
    }
}
