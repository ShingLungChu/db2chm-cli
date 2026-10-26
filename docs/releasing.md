# 构建、验收、发布与回滚

## 当前范围与外部依赖

正式 scope：@nstc，账号：jackiechen1994。当前机器 npm whoami 返回 ENEEDAUTH；组织创建/更新包权限尚未证实。代码不保存 npm 凭据。配置 npm trusted publisher 时绑定本仓库和 npm-publish.yml，采用有权发布五个包的身份；GitHub 的 npm-release 环境可设置保护规则。

本版真实数据库环境优先 Oracle、DM、Vastbase；最低通过标准为 Oracle 和 DM。其余适配器保留，未验证组合不作为正式支持承诺。这是用户在 2026-10-26 对原计划数据库验收范围的调整。

## 本机配置

.local/databases/ 中已创建 oracle.json、dm.json、vastbase.json 和 versions.json，整个 .local 被 Git 忽略，并且不在 npm files 清单内。连接文件字段仅 type/url/user/schema/charset/driverJar，版本记录另外保存。

填写后，在本机提供对应的 DB2CHM_ORACLE_PASSWORD、DB2CHM_DM_PASSWORD、DB2CHM_VASTBASE_PASSWORD，然后运行：

```sh
node scripts/check-databases.cjs --types oracle,dm
```

默认验证真实表清单和至多 3 张表的 HTML 导出。通过依赖文件、驱动版本/哈希、JSON 结果和生成文件保存本地证据。加 `--compiler chmcmd --compiler-path PATH` 可使用已构建的可信 chmcmd 执行 CHM 验证；完整可用的 HHC 可显式选择。数据库审计和 Windows Viewer 的目录/索引/全文搜索仍需另外记录。结果含业务元数据，只保留在 target/，不公开上传。

## 自动化流程

| 工作流 | 当前验证职责 |
| --- | --- |
| npm-validation.yml | 四平台 × 三种 Java/Node 组合；私有原型 pack、CLI、安全、真实本地 registry 全局安装、中文参数/安装目录 |
| native-toolchain.yml | 固定源代码和显式兼容补丁构建四平台编译器；记录依赖、许可、哈希；完整 Java→CHM、真实 native npm 安装；Windows 对 12 份 CHM 解包后逐页哈希 |
| chm-feasibility.yml | 早期发行包编译器的合成样例，保留作为对照 |
| npm-publish.yml | 手动选择已验收产物的 run id，仅下载和发布同一份 tarball；不重新构建 |

macOS arm64 的 Java 8 使用 Azul Zulu；Temurin 未提供对应版本。Node 从 runner cache 选择 22/24。bootstrap FPC 版本校验与包清单记录已经实现；系统包/镜像的字节级固定和最低系统实测尚未完成，不能把当前流水线称为完全可复现。

native 工作流在 Ubuntu 中重复构建业务 JAR 并比较字节，四个平台随后下载和使用同一份 shared-java-runtime artifact。四平台包内 JAR 一致性已经下载复核。各平台源码构建与 native 的完全复现仍单独验收。

## 打包与发布门槛

1. Maven 构建生成业务 JAR；在相同固定环境重复构建并比较哈希。固定 outputTimestamp 改善 JAR 复现；CHM 包含生成时间，内容相等与字节相等分开记录。
2. 私有原型使用 `node scripts/package.cjs --prototype`，永远不可用于发布。
   含真实编译器的单平台私有验证包使用 `node scripts/package.cjs --prototype --native-prototype --platform PLATFORM`；需要对应 native manifest，不会改变正式发布资格。
3. 候选打包使用 `node scripts/package.cjs`，要求干净 Git 源码、四个平台 target/native/PLATFORM/manifest.json、来源/许可审核和逐文件哈希匹配。Windows payload 为 chmcmd.exe；HHC 为可选外部优先后端。原生分发审查、最低系统和真实验收等门槛尚未闭合，尚未生成正式候选。
4. report.json 固定 sourceCommit、版本、JAR SHA-256、全部 tarball SHA-256 和入口+平台下载量。每条安装路径严格小于 15,000,000 字节。runtime SBOM 和许可证随平台包交付。
5. 将 release/acceptance.template.json 复制为候选 bundle 的 acceptance.json，只有证据实际存在才填写 passed。需绑定 exact report 哈希、源码提交、G0–G5 的文件及 SHA-256，以及 Oracle/DM 的真实验收结果。证据文件保存在 bundle 内。
6. `node scripts/verify-release.cjs BUNDLE BUNDLE/acceptance.json` 检查真实 tgz 内的包名、版本、private、os/cpu、精确依赖、文件范围、哈希、体积和全部验收证据。伪造 report 的 prototype 标志不能绕过 tgz 的 private:true。
7. 将这份完整且通过的 bundle 作为 successful workflow 的 accepted-npm-release artifact 保存；候选产物的汇总/上传仍待 G1/G3/G4 收口后接通。发布工作流只接受本仓库指定 run 的同一份文件，并核对其 head_sha。
8. 发布脚本先校验全部门槛，默认只验证。只有显式 --publish 才写入 npm；按四个平台→入口顺序发布到 beta/next，并逐个检查 registry integrity。已发布相同版本/字节可恢复中断；不同内容立即停止。

```sh
node scripts/publish.cjs BUNDLE beta
# 满足全部门槛后，受控 CI 内执行：
node scripts/publish.cjs BUNDLE beta --publish
```

此发布流程尚未连接真实受信 npm 发布身份，也没有完成 beta registry 安装。因此 T11/T12/T13 仍保留未完成项。

## latest 与回滚

beta/next 发布后，用 registry 中的原包在四个平台做完整安装/真实导出验收，保存版本、integrity 和日志。全部通过后再将同一个稳定版本提升至 latest。0.2.0-beta.1 本身是预发布号，不直接作为稳定版。

多包发布部分失败时维持旧 latest，补发缺失的相同版本，再验证入口依赖。已发布版本不可换内容；需要修复就增加版本。回滚恢复五个包的 dist-tag 到上一已验收版本，保留失败版本和取证记录；不常规删除 registry 版本。
