# db2chm CLI

需要逐步更换 npm 组织、build 四个平台发布包并手动发布时，请按[发布指南](发布指南.md)执行；Windows 入口为项目根目录的 `release.cmd`。

采用 GitHub Actions Trusted Publishing、免长期 npm token 发布时，请按[OIDC 发布配置手册](OIDC发布配置手册.md)操作；当前组织为 `@t20-ai`。首次建包、逐包授权、配置预检与真正的 OIDC 发布验收分别记录。

提供 Oracle、MySQL、达梦、Vastbase、KingbaseES 元数据适配器，按表名过滤并生成 HTML/CHM 数据字典。真实兼容范围以版本验收记录为准，本版最低真实验收为 Oracle 和达梦。业务逻辑使用 Java，npm 提供平台选择和启动入口；用户预装 Java。

## 当前交付状态

`0.2.0-beta.1` 的公开 beta 候选包已完成四平台实际安装、CHM 导出和 Windows 解包验收，最终 Windows 原包的 Oracle/达梦/Vastbase 复验也通过。**npm 发布已交由账号持有人在自己的终端按发布指南执行；实际完成状态以 registry 校验和发布后验收记录为准。** JDBC 驱动外置，不分发 Java 或 Microsoft HHC。候选来源及完整包哈希见[验收证据](docs/evidence/beta-candidate-7507078.json)。

正式命名采用个人 scope `@jackiechen1994`，发布账号已在本机登录并核验。用户在 2026-10-27 将原计划的 `@nstc` 改为个人 scope；新名称的四平台构建、私有安装、自动汇总与三库功能复验已通过，旧记录保留。目标包：

| 包 | 平台 |
| --- | --- |
| @jackiechen1994/db2chm | 统一入口，自动选择当前平台依赖 |
| @jackiechen1994/db2chm-win32-x64 | Windows x64，优先已安装 HHC，缺席时用包内 chmcmd |
| @jackiechen1994/db2chm-darwin-x64 | macOS Intel |
| @jackiechen1994/db2chm-darwin-arm64 | macOS Apple Silicon |
| @jackiechen1994/db2chm-linux-x64 | Linux x64/glibc |

安装压缩下载总量严格小于 15,000,000 字节，计入入口、所选平台包和默认依赖。Java 不随包分发；目前五种 JDBC 驱动均外置。Linux arm64、Alpine/musl 进入后续版本。目标运行环境为 Node 22/24、Java 8/17/21；操作系统最低版本和各数据库版本仍待实测冻结。

实施进度见 [任务状态](docs/task-status.md)，完整验收标准见 [发布计划](docs/npm-release-plan.md)。

当前最终 beta 候选安装链约 2.70–3.22 MB，全部小于 15 MB，已包含完整 native 工具源、许可和重建说明。文档功能已由用户人工确认通过；数据库账号/服务端 SQL 审计、更老系统测试及全部 runtime 源复现继续保持未验证或未完成。具体 beta 范围见[发布决定](docs/beta-release-decision.md)；历史 private 原型证据保留，不改写成已发布包。

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

Windows 自动模式优先查找已安装的 `hhc.exe`，找不到时选择包内 `chmcmd.exe`。macOS/Linux 自动选择包内 chmcmd。Windows 不要求另装 Workshop，也不捆绑微软 HHC 文件。

可用 `--compiler chmcmd` 明确选择开源后端；`--compiler hhc` 只接受 HHC，缺失即报错。已找到的 HHC 如果启动或编译失败，会保留该错误，不自动改用另一后端。`--compiler-path PATH`、`DB2CHM_COMPILER` 指向明确选择的编译器；路径本身不能用于自动推断类型，指向 chmcmd 时同时指定 `--compiler chmcmd`。这是真实可执行依赖，应来自可信来源。

HHC 是编译器 `hhc.exe`；Windows 的 `hh.exe` 是查看器/解包工具。项目参考目录中的 HHC 在当前宿主能启动，但出现 HHC6003 搜索组件错误；生成了文件也会被 CLI 拒绝。HHC 再分发权和无需注册的完整运行条件尚未解决，因此它没有进入 npm 原型。

四个平台的 chmcmd 使用固定 FPC 源码构建。构建成功、Windows 解包成功、查看器目录/索引/全文搜索通过，分别是不同的验收证据。[验证记录](docs/validation-evidence.md)及[依赖与许可清单](docs/dependencies.md)记录当前结果与限制。

## 独立 skill

[skills/db2chm/SKILL.md](skills/db2chm/SKILL.md) 随仓库交付，调用同一 CLI。将整个 `skills/db2chm` 目录安装到客户端支持的 skill 目录，例如 Codex 的 `~/.codex/skills/db2chm`。skill 安装与 npm 安装分别进行，不自动修改客户端配置。

更多文档：

- [CLI 契约与退出码](docs/cli-contract.md)
- [安全查询与权限边界](docs/security.md)
- [构建、发布与回滚](docs/releasing.md)
- [真实数据库与查看器验收](docs/manual-acceptance.md)
