# CLI 契约 v1

结果 schemaVersion 为 1，当前版本 0.2.0-beta.1。

## 命令

| 命令 | 作用与前提 |
| --- | --- |
| help / --help / -h | 显示帮助；npm 入口无需 Java |
| version / --version / -V | 显示版本；npm 入口无需 Java |
| doctor | 检查 Java、平台、编译器文件和所选 JDBC 驱动；不连接数据库 |
| tables list | 连接数据库，列出经过相同过滤器处理的表 |
| export | 取选中表的列，生成新目录中的 HTML/工程文件/CHM |

参数以当前 `--help` 为完整清单。不支持 `--key=value`、任意 SQL、额外编译器参数或任意驱动类。非重复型参数出现两次、未知参数、缺值和非法整数均返回 2。仅 include/exclude 可重复。list 拒绝 export 专用参数。help/version 只接受附加 --json。

配置文件最大 64 KiB，仅允许 type、url、user、schema、charset、driverJar 字符串字段。CLI 字段覆盖文件字段，driverJar 相对配置文件解析。密码只能用环境或 stdin；旧 --password 保留并向 stderr 提示迁移。未提供密码时允许驱动使用空密码，产品不发起交互询问。--timeout 覆盖读取密码、连接、元数据、渲染和编译的整个操作；底层 JDBC 另外设置连接/查询超时。进程退出终止未响应的后台操作。

## 过滤

- glob 支持 *、?，匹配整个表名。模糊包含使用 `"*order*"`。
- 默认区分大小写；--ignore-case 使用 Locale.ROOT 的 Unicode 大小写转换，不依赖宿主 locale。
- include 取并集，exclude 再扣除；未给 include 表示 schema 下全部基础表。
- 反斜线、方括号、点和 SQL 特殊字符均为普通字符；没有通配符转义语法。
- 按原始表名 Java String 顺序排序，再应用 --max-tables。list/export 复用同一集合。
- Oracle/DM 的显式 schema 以数据库元数据的准确大小写传入，例如 SCOTT；表名保持原样，支持引号创建的混合大小写表。
- MySQL 默认 schema 从 URL 数据库名取得；PG 系默认 public，可通过 --schema 或 URL currentSchema 指定。
- list 无匹配：0、空数组；export 无匹配：5、NO_TABLES_MATCHED，不创建成果。

## JSON 与错误

--json 时 stdout 恰好一个 UTF-8 JSON 对象，诊断写到 stderr：

```json
{
  "schemaVersion": 1,
  "ok": true,
  "command": "tables list",
  "data": {
    "tableCount": 1,
    "tables": [{"name": "BIZ_ORDER", "comment": "订单主表"}]
  },
  "error": null
}
```

失败时 data 为 null，error 有 code、message。doctor 依赖缺失时可同时返回检查明细 data 和 error，退出 3。export 的 data 包含 outputDir、html、chm、compiler 和表清单；--html-only 的 chm/compiler 为 null。doctor 的编译器检查只证明文件存在和平台执行权限，不证明完整运行或全文搜索可用。

| 退出码 | 含义 / 代表错误码 |
| --- | --- |
| 0 | 成功 |
| 2 | INVALID_ARGUMENT |
| 3 | DRIVER_NOT_FOUND、DRIVER_MISMATCH、DRIVER_INCOMPATIBLE、COMPILER_NOT_FOUND、COMPILER_PROFILE_UNSUPPORTED、JAVA_NOT_FOUND |
| 4 | CONNECTION_FAILED |
| 5 | METADATA_FAILED、METADATA_EMPTY、NO_TABLES_MATCHED |
| 6 | EXPORT_FAILED、COMPILER_FAILED、STALE_CHM、ENCODING_UNREPRESENTABLE |
| 7 | OUTPUT_EXISTS、IO_FAILED |
| 8 | CONNECTION_POLICY_REJECTED、READ_ONLY_REQUIRED |
| 124 | OPERATION_TIMEOUT、COMPILER_TIMEOUT |
| 130 | CANCELLED / 启动器收到终止信号 |

完整 JDBC 错误和编译器原始日志不输出，以免其包含连接凭据或本地内容。编译失败保留退出码与 HHC 诊断编号。

## 文件与兼容性

export 在同级随机临时目录生成完整成果，成功后移动到新输出目录。已有目录、符号链接、并发创建的目标均拒绝覆盖。失败只清理本次拥有的临时目录。中途强制终止可能残留 `.db2chm-*` 工作目录，不自动删除其他运行的目录。

当前 CHM 使用简体中文配置：LCID=0x804、导航代码页 CP936。HHC 使用 GBK 正文和工程输入；chmcmd 使用 UTF-8 正文与工程输入，并在编译时将导航、标题、文本 HHC/HHK 及搜索词项写成 CP936，将二进制关键字写成 UTF-16LE。正文编码不决定导航流的编码。不可表示的导航文字返回 ENCODING_UNREPRESENTABLE，不发布半成品；--html-only 可保留 Unicode HTML。

包内 chmcmd 必须支持 `db2chm-utf8-cp936-v1` 配置。通过 --compiler-path 使用未经本包修改的上游 chmcmd 会返回 COMPILER_PROFILE_UNSUPPORTED，应使用平台包内的编译器。JSON schemaVersion 仍为 1；compiler 中保留 charset，增加 htmlCharset、projectCharset、navigationCodepage、localeId 和 encodingProfile。

全文搜索按中文字符记录位置，ASCII 标识符按单词记录；支持范围以阅读器实际查询验收为准，不承诺自然语言词典分词。编译返回码和 ITSF 文件头仅为基本校验；二进制结构与 Windows Viewer 验收单独记录。

Windows 的 `--compiler auto` 优先 HHC，只有没有找到 HHC 文件时才选择包内 chmcmd。显式 `--compiler hhc`、显式编译器路径或环境变量配置均不在失败后切换后端。已找到的 HHC 报错时保留编译失败；需要改用开源后端时指定 `--compiler chmcmd`。HHC 不随 npm 分发。

旧 --tables 仍表示 mock 表数量，推荐 --mock-tables；规模夹具可用 --mock-columns 20。Java 无参数 GUI 仅保留原入口，npm 主交付范围为 CLI。GUI 外置驱动可使用 JAR 所在目录下 lib/ 中固定的旧文件名，或在代码提供的连接配置中指定路径；不从当前工作目录扫描 JAR。
