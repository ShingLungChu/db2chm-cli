# db2chm CLI

读取 Oracle、MySQL、达梦、Vastbase、KingbaseES 元数据，按表名过滤并生成 HTML/CHM 数据字典。业务逻辑使用 Java，npm 提供平台选择和启动入口；用户预装 Java。

## 当前交付状态

本仓库处于 npm 发布实施阶段。CLI、只读访问约束、表过滤、HHC/chmcmd 后端和私有 npm 原型已实现。**尚未发布 npm RC 或正式版。** 当前原型不含编译器和 JDBC 驱动，不代表安装后可直接生成 CHM。

正式命名采用组织 `@nstc`，发布账号为 `jackiechen1994`。组织发布权限尚未核验。目标包：

| 包 | 平台 |
| --- | --- |
| @nstc/db2chm | 统一入口，自动选择当前平台依赖 |
| @nstc/db2chm-win32-x64 | Windows x64，默认 HHC |
| @nstc/db2chm-darwin-x64 | macOS Intel |
| @nstc/db2chm-darwin-arm64 | macOS Apple Silicon |
| @nstc/db2chm-linux-x64 | Linux x64/glibc |

安装压缩下载总量严格小于 15,000,000 字节，计入入口、所选平台包和默认依赖。Java 不随包分发；目前五种 JDBC 驱动均外置。Linux arm64、Alpine/musl 进入后续版本。目标运行环境为 Node 22/24、Java 8/17/21；操作系统最低版本和各数据库版本仍待实测冻结。

实施进度见 [任务状态](docs/task-status.md)，完整验收标准见 [发布计划](docs/npm-release-plan.md)。

## 开发构建与离线使用

开发需要 JDK 8+、Maven 3.6.3+、Node 22+；普通用户无需 Maven 或 FPC。

```sh
mvn --batch-mode --no-transfer-progress -DskipTests package
java -jar target/db2chm.jar --help
java -jar target/db2chm.jar tables list --mock --include "*ORDER*" --json
java -jar target/db2chm.jar export --mock --html-only --out ./out/mock-dictionary --json
node scripts/package.cjs --prototype
node --test tests/*.test.cjs
```

原型 tarball 与大小报告位于 `target/npm/`，其 package.json 含 `private:true`。测试使用隔离的本地 registry 和临时全局安装目录，不修改用户的全局 npm 安装。

Java 无参数仍打开旧 GUI；npm 无参数显示帮助。旧 `--cli --mock --tables N` 继续兼容。帮助与版本可由 npm 启动器直接显示，无需 Java。

## 真实数据库调用

从数据库厂商获取有权使用的 JDBC JAR，显式指定路径。以下配置仅为占位示例：

```json
{
  "type": "MYSQL",
  "url": "jdbc:mysql://localhost:3306/example",
  "user": "metadata_reader",
  "driverJar": "/trusted/drivers/mysql-connector-java-8.0.17.jar"
}
```

driverJar 的相对路径以配置文件所在目录为基准。密码通过已设置的 `DB2CHM_PASSWORD` 或 `--password-stdin` 提供，不写入 JSON。然后在已安装 CLI 的环境执行：

```sh
db2chm doctor --config ./connection.json --json
db2chm tables list --config ./connection.json --include "BIZ_*" --exclude "*_BAK" --json
db2chm export --config ./connection.json --include "BIZ_*" --exclude "*_BAK" --out ./out/dictionary --json
```

源码运行时将开头的 `db2chm` 替换为 `java -jar target/db2chm.jar`。仅生成 HTML 时加 `--html-only`；默认拒绝已有输出目录。

## CHM 编译器

Windows 自动选择 HHC，macOS/Linux 自动选择 chmcmd。可显式指定 `--compiler hhc|chmcmd --compiler-path PATH`；这是真实可执行依赖，应来自可信来源。

HHC 是编译器 `hhc.exe`；Windows 的 `hh.exe` 是查看器/解包工具。项目参考目录中的 HHC 在当前宿主能启动，但出现 HHC6003 搜索组件错误；生成了文件也会被 CLI 拒绝。HHC 再分发权和无需注册的完整运行条件尚未解决，因此它没有进入 npm 原型。

三种 macOS/Linux 原生构建使用固定 FPC 源码。构建成功、Windows 解包成功、查看器目录/索引/全文搜索通过，分别是不同的验收证据。[依赖与许可清单](docs/dependencies.md)记录当前限制。

## 独立 skill

[skills/db2chm/SKILL.md](skills/db2chm/SKILL.md) 随仓库交付，调用同一 CLI。将整个 `skills/db2chm` 目录安装到客户端支持的 skill 目录，例如 Codex 的 `~/.codex/skills/db2chm`。skill 安装与 npm 安装分别进行，不自动修改客户端配置。

更多文档：

- [CLI 契约与退出码](docs/cli-contract.md)
- [安全查询与权限边界](docs/security.md)
- [构建、发布与回滚](docs/releasing.md)
