# 元数据访问与安全验收

## 固定查询目录

所有生产查询通过 AbstractDbAdapter.prepareMetadataQuery 进入 PreparedStatement。以下为实际适配器查询集；SQL 文本在源码中固定，schema/表名用 setString 绑定。选择器不生成 SQL。

| 适配器 | 列表/注释/字段/主键数据源 | 值参数 |
| --- | --- | --- |
| MysqlAdapter | information_schema.TABLES、COLUMNS、STATISTICS；IFNULL | schema、tableName |
| OracleAdapter | ALL_TABLES、ALL_TAB_COMMENTS、ALL_TAB_COLUMNS、ALL_COL_COMMENTS、ALL_CONSTRAINTS、ALL_CONS_COLUMNS | owner（或固定 USER）、tableName |
| DmAdapter | 同上 Oracle 系视图 | owner（或固定 USER）、tableName |
| VastbaseAdapter | pg_class、pg_namespace、pg_attribute、pg_type；obj_description、col_description、format_type、pg_get_serial_sequence；information_schema.TABLE_CONSTRAINTS/KEY_COLUMN_USAGE | schema、tableName；序列查询标识符经数据库 quote_ident 处理 |
| KingbaseAdapter | pg_class/pg_namespace；information_schema.COLUMNS、TABLE_CONSTRAINTS、KEY_COLUMN_USAGE | schema、tableName |

PG 系主键查询同时绑定 schema 和表名。数据库方言和特定视图列是否存在需要真实版本验证，尤其 Kingbase 的 COMMENT/IFNULL 与 Oracle 的 IDENTITY_COLUMN；当前没有真实数据库兼容承诺。所有表的数据行均不读取。字段默认值目前主要用于判断自增，尚未作为独立内容展示。

查询入口额外拒绝非 SELECT、分隔符和注释，这只是对固定查询的补充断言。连接接口没有接受用户 SQL 或写操作的命令。DELETE 为写 DML，同 CREATE/DROP、DCL、存储过程执行均不在产品能力中。

## 连接能力

- 固定五种数据库到认可的驱动类。URL 仅接受单个直接 host[:port]/database；Oracle 支持直接 service/SID，端口 1..65535。
- 拒绝凭据 URL、多服务器、描述符/TNS、分号初始化、任意属性。允许的属性由 JdbcPolicy 按数据库定义，含有限超时、字符集、TLS、schema 和应用名。
- multiquery、反序列化、LOCAL INFILE、任意插件/SocketFactory、初始化 SQL、角色切换均无开放入口。Oracle/DM 账号名只接受简单标识符，排除角色登录语法。
- 调用 connect 后要求 setReadOnly(true) 且 isReadOnly() 为 true，否则关闭连接并返回 READ_ONLY_REQUIRED。每条元数据语句带查询超时。只读标志的数据库实际效果仍需要真实驱动审计。
- 外置 JAR/编译器是调用者主动提供的可信代码。固定类名不等于验证文件来源，清单 SHA-256 便于核对；不要以恶意二进制测试结果宣称隔离任意 Java 代码。

## 凭据与文件

CLI 密码仅来自环境或 stdin，ConnectionInfo.password 不序列化；帮助保留弃用的旧参数兼容说明。默认诊断不回显完整 URL、用户名和底层异常。GUI ConfigStore 在加载时重写旧配置，移除密码与不符合 URL 策略的记录。

数据库注释、表名经模板及 HHC/HHK HTML 属性转义，输出页面名由序号生成，避免用户表名变成路径。标题控制字符在参数解析和工程生成两层拒绝。子进程使用独立 argv，不通过 shell。

## 真实数据库审计尚待交付

目前自动化只使用 test-classes 中的测试 JDBC 驱动；该类不会打进生产 JAR。它能验证只读调用、绑定和超时约定，不能证明五种真实驱动行为。

每个实际支持的数据库版本须用非生产最小权限账号记录：

1. 驱动版本/哈希、服务器版本、宿主平台/Java 版本。
2. list→过滤→字段→导出结果及中文/引号表名。
3. 产品账号会话实际 SQL；测试前后对象、数据和权限摘要。
4. 恶意 schema、表名、URL 属性与权限不足输入的结果。
5. 测试数据准备由独立夹具账号完成，产品账号不执行建表或授权。

本版优先 Oracle、达梦、Vastbase，最低真实验收为 Oracle 和达梦。本机配置已经创建，尚待填写；该部分是 T05/T09 的未完成验收项。
