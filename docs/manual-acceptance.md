# 真实数据库与查看器验收

本版最低真实验收为 Oracle、达梦；Vastbase 为计划环境。自动化合成样例和解包比对已经通过，以下记录用于补齐真实环境及查看器的交互证据。

## 1. 填写本机配置

实施仓库的 `.local/databases/` 已有 oracle.json、dm.json、vastbase.json、versions.json。该目录不会进入 Git 或 npm。

- 连接文件填写 url、user、schema、driverJar；Oracle/达梦的 schema 保持数据库准确大小写。账号应为非生产、最小元数据读取权限账号。
- 核实本机 JDBC 路径、驱动版本和服务器版本；在 versions.json 记录服务器版本。确认账号权限后填写 accountPermissionsConfirmed。该确认不代替权限审计。
- 不添加 password 字段。密码留在本机对应的 DB2CHM_ORACLE_PASSWORD、DB2CHM_DM_PASSWORD、DB2CHM_VASTBASE_PASSWORD 环境变量中，不发送到聊天或写入报告。

先运行真实列表和至多三表 HTML 验证：

```sh
node scripts/check-databases.cjs --types oracle,dm
```

使用已构建的 Windows 开源编译器执行相同真实环境 CHM 验证：

```sh
node scripts/check-databases.cjs --types oracle,dm --compiler chmcmd --compiler-path ./target/native/win32-x64/chmcmd.exe
```

Vastbase 单独选择 `--types vastbase`。每次运行使用新的本机输出目录。结果可能含业务表名、注释和元数据，不提交或公开上传 target/database-validation-*。

## 2. 只读审计记录

对每个正式声明支持的驱动/服务器组合，记录驱动哈希、服务器版本、测试时段、账号权限摘要和数据库审计来源。核对实际发出的语句属于固定元数据查询/只读设置，目标数据、对象、角色和授权没有非预期变化。

Oracle 驱动的 setReadOnly 仅是客户端标志，不能据此认定数据库账号无写权限；达梦的行为也与驱动和兼容模式有关。产品不会尝试 DELETE、DDL、DCL 或提权来验证权限。准备测试表和权限的操作应由独立管理员流程完成。

本机 `.local/acceptance/database-audit.json` 已准备记录框架。只有真实证据存在时才修改 pending 状态。公开发布证据须另作脱敏。

## 3. Windows 查看器

使用实际生成且可信的 CHM，在 Windows HTML Help 查看器逐项验证：

| 项目 | 通过标准 |
| --- | --- |
| 封面和中文 | 标题、表名、注释、字段值显示正确，没有乱码或空白页 |
| 目录 | 左侧目录树列出所选表；点击目录能进入正确表页 |
| HHK 索引 | 索引项能定位对应表，点击后正文正确 |
| 全文搜索 | 搜索已知中文正文与 ASCII 表名，能返回预期页面并打开 |
| 链接 | 封面、目录和表页之间跳转正确，无不存在的页面 |
| 规模 | 1,000×20 样例的目录、索引和搜索可用，记录打开和查询表现 |

至少分别记录 Windows bundled chmcmd 与 macOS/Linux 产物。若正式支持 HHC，也记录完整可用外置 HHC 的实际结果；当前 HHC6003 不能标为通过。

本机 `.local/acceptance/viewer.json` 已准备记录框架。记录 Windows/Viewer 版本、CHM SHA-256、后端、各项结果和必要的脱敏截图。自动解包哈希匹配不能替代这些交互操作。

以上完成后，再冻结支持矩阵、审查 native 分发与对应源码，汇总候选并验证 npm 发布权限。当前没有正式 RC 或 npm 版本。
