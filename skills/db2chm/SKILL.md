---
name: db2chm
description: 使用 db2chm CLI 预览数据库表清单，按表名筛选并导出 HTML 或 CHM 数据字典。适用于数据库结构文档任务。
---

# db2chm

通过实际安装的 `db2chm` 命令完成操作。首次使用先读 `db2chm --help`，以本机版本的参数为准。CLI 执行数据库访问和安全检查；本 skill 提供操作流程。

## 工作流程

1. 从用户任务确定数据库类型、已授权的 schema、表名范围和输出位置。使用现有连接配置和可信 JDBC 驱动路径；缺少连接信息时只询问缺失项。
2. 运行 `db2chm doctor --config ./connection.json --json`。检查 Java、编译器和驱动。doctor 检查依赖文件，不建立数据库连接，也不保证编译器的全部功能正常。仅列清单或导出 HTML 时不依赖编译器，可在驱动就绪后继续。
3. 用 `tables list` 预览真实表清单，将 glob 包在引号内。例如：

   ```sh
   db2chm tables list --config ./connection.json --include "BIZ_*" --exclude "*_BAK" --json
   ```

4. 复用相同的筛选条件导出到一个新的目录：

   ```sh
   db2chm export --config ./connection.json --include "BIZ_*" --exclude "*_BAK" --out ./out/dictionary --json
   ```

5. 检查退出码及 JSON 的 `ok`。从 `data.chm` 或 `data.html` 取得实际交付路径，报告选中的表数和编译器。CHM 的 Windows 查看器目录、索引和搜索体验需要单独验收。

## 凭据与执行约束

- 密码由用户在执行环境中提供 `DB2CHM_PASSWORD`，或经 `--password-stdin` 输入一行 UTF-8。连接配置只含 type、url、user、schema、charset、driverJar。保持密码远离聊天、命令参数、日志和配置文件。
- 使用具备最小元数据读取权限的账号。调用固定的 list/export 接口；不生成用户 SQL、不添加连接初始化、角色切换或插件参数。
- JDBC JAR 和显式编译器路径指向调用者信任的可执行依赖。依赖缺失时报告缺项；依赖的下载、系统安装和数据库授权遵循当前用户任务的授权范围。
- `*` 匹配任意长度、`?` 匹配一个 Unicode 码点；重复 include 取并集，exclude 优先。默认区分大小写。反斜线和方括号是普通字符；首期没有转义通配符语法。
- list 无匹配成功返回空数组；export 无匹配返回 `NO_TABLES_MATCHED`。输出已存在时换新目录；不要删除用户已有结果或自动扩大表范围。
- 标准输出的 JSON 是唯一机器结果；标准错误是诊断。遇到策略拒绝、权限错误或持续失败时，保留错误码并说明缺少的前提，避免扩大权限重试。

## 离线演示

用户只要求演示且未授权数据库访问时，可使用：

```sh
db2chm tables list --mock --include "*ORDER*" --json
db2chm export --mock --include "*ORDER*" --html-only --out ./out/mock-dictionary --json
```

演示结果明确标为 mock；不作为真实数据库或 CHM 编译兼容性的证据。
