# 私有 npm 与 CHM 验证记录

更新：2026-10-27。本记录只包含合成元数据和公开构建证据，不包含真实数据库信息。私有验证包不是 RC，不能据此认定已完成正式 npm 发布。

**当前状态：** 中文导航编码修复已通过四平台自动回归和 Windows 中文夹具交互；用户进一步确认生成文档验收通过，文档功能验收关闭。[人工确认](evidence/chm-viewer-human-acceptance-20261027.json)与原始机器记录分别保留。账号权限/SQL 审计、native 分发与 npm 身份等发布门槛未由此放行。详见[交互验收报告](chm-viewer-acceptance.md)。

## 当前修复版本的证据

源码提交：`2e63e2017b12fca13bad3d334f7081414d097c65`。

本轮已下载并核对该次 CI 的四个平台原始 tgz、native 和共享 JAR，共 9 个 artifact ZIP digest。五个未改写的 tgz 汇总至本机 `target/release-review-2e63e20/`；四个平台包内 JAR 均为 `7b4ee792eb515c7422553d6491991ec5742930262ea55d59f071eb446f416d8c`，Windows/Intel/Apple Silicon/Linux 总下载链分别为 2,651,110 / 3,162,310 / 3,128,782 / 2,720,435 字节。[当前完整汇总](evidence/native-validation-2e63e20.json)和[验证矩阵](support-matrix.md)绑定这批文件。

同一 CI Windows 原包已完成三库 doctor、列表、精确过滤、三表导出及集合一致性，内部导航和 18 个正文文件解包比对通过。Windows 编译器使用当前补丁与已校验 bootstrap 另行重建，二进制与该 CI native 哈希相同；[输入记录](evidence/native-inputs-win32-2e63e20.json)含 41 个预编译单元与 15 个工具源码，仍不宣称四平台工具链和对应源码全部闭合。全部 tgz 仍为私有验证包，正式校验按预期拒绝发布。

- [共享业务 JAR、四平台 native、私有 npm 安装、导航与 Windows 解包](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40436646789)：全部通过。四平台 small/scale/installed/chinese 共 16 份 CHM，4,076 个 HTML/CSS 哈希全部匹配；深层中文 HHK 单独覆盖 200 个长关键字、BTree 深度 3。
- [四平台 × Java/Node 三组合](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40436646777)：12 组合通过，当前 13 项测试通过。
- Windows 中文查看器实际操作四平台最终中文夹具：标题、目录展开/跳转、中文 HHK 定位及打开、中英文全文搜索和结果跳转通过，共 32 个检查点；查询各 6/6 命中。
- Windows 千表场景完成封面、千表目录和末端第 1,000 个表页（20 列）3 个检查点。工具持续报告窗口最小化，恢复失败后停止继续输入，页内链接及规模索引/搜索保留 pending。
- [机器记录](evidence/chm-viewer-2e63e20.json)绑定 CHM 原件/副本和 UI 证据哈希；查看器为中文 Windows `10.0.26200.0`、`hh.exe 10.0.26100.8875`。未测试英文 Windows 交互；HHC6003 仍未通过。
- 最终本机包完成 Oracle、达梦、Vastbase 的真实 doctor、列表、精确过滤和三表导出；内部导航检查通过，新导出的 18 个正文文件解包匹配。六份原乱码文档重编译后内部导航通过，30 个正文文件解包匹配，原失败件保留。真实数据库原件 UI 与账号权限/SQL 审计仍 pending。

当前本机 Windows 平台 tgz 为 2,650,667 字节（SHA-256 `fb99c65be92d74f5df48c13b0a064c5857faf23a33c5c0946f55a0a6c2565aa9`），配合 2,471 字节入口，总下载链 **2,653,138 字节**，严格低于 15,000,000。该数字绑定本机私有验证包；没有用下方历史四平台体积表冒充最终候选。

## 历史版本的四平台自动验证（4826b39）

以下记录绑定历史源码 `4826b393c361a9deb6d0b44c80ef29025bd235d4`，其通过范围为当时的安装、生成和正文检查，不包含本轮 UI 修复验收。

- [共享业务 JAR、四平台 native、npm 安装与解包流水线](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40276072365)：全部通过。
- [四平台 × Java/Node 三个组合](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40276072386)：12 个组合全部通过。
- [早期编译器可行性对照](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40276072366)：通过。
- 前一轮提交 d1ef573 的四平台原生验证也已通过：[run 40274603591](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40274603591)。

各平台均执行完整 Java→HTML/工程文件→CHM：

| 场景 | 内容 | 每个平台 Windows 解包后比对文件数 |
| --- | --- | ---: |
| small | 两表、中文、注释、链接、过滤 | 5 |
| scale | 1,000 表 × 20 列 | 1,003 |
| installed | 含实际编译器的 tgz 安装在中文/空格路径，从安装目录导出两表 | 5 |

四平台共 12 份 CHM、4,052 个 HTML/CSS 文件，解包后 SHA-256 全部匹配。`hh.exe` 只用于 Windows 解包；实际编译器是 chmcmd。

Windows 安装验证额外确认：无 HHC 时实际执行包内 chmcmd；仅定位文件的 doctor 夹具确认 HHC 优先；显式 `--compiler hhc` 缺失时退出 3，不回退。夹具不执行 HHC，不是 HHC6003 修复证据。

## 历史私有包安装体积（4826b39）

按入口 tgz＋当前平台 tgz 计量，含 JAR、实际 native、精确 utility 源码、补丁和许可证；没有第三方 JavaScript 默认依赖。用户预装的 Java 和显式外置 JDBC 不在默认下载链中。

| 平台 | 平台 tgz / 字节 | 入口 tgz / 字节 | 安装下载总量 / 字节 |
| --- | ---: | ---: | ---: |
| Windows x64 | 2,635,419 | 2,471 | **2,637,890** |
| macOS Intel | 3,147,118 | 2,483 | **3,149,601** |
| macOS Apple Silicon | 3,114,688 | 2,478 | **3,117,166** |
| Linux x64 | 2,706,516 | 2,478 | **2,708,994** |

已下载并重新计算每份 tgz 的哈希、大小和内含 JAR 哈希；[机器可读汇总](evidence/native-validation-4826b39.json)保存精确值。所有体积结论绑定具体私有 tarball，不作为尚未形成的 RC 尺寸承诺。各 runner 生成的入口归档字节可有差异；最终发布只会使用候选汇总中选定并验收的同一份入口 tgz。

## 历史 Windows 二进制（4826b39）

- 固定 utility 源码：FPCSource `de69d52048cf52c72ebfeef61617c04c9bc2005c`。
- 官方 bootstrap：3.3.1；ZIP 与实际编译器分别校验哈希。
- 产物：PE32+ x86_64，2,375,953 字节。
- SHA-256：`152c318f261c8ebcb7d505b4a2329a09efcb58fbe84508dd1540d3a0f5af6d2c`。
- 导入：kernel32.dll、oleaut32.dll、user32.dll；没有额外 FPC 运行库 DLL 安装前提。
- 本机临时构建、新构建脚本和 Windows CI 的该产物哈希一致。该证据尚不能代表所有平台/native 完全复现，也不能由 PE 头版本推断最低 Windows 版本。

## 复现边界与后续

首轮四平台独立构建时，Windows JAR 中模板、CSS、properties 和 Maven 生成描述使用 CRLF，其他平台使用 LF；业务 class 内容一致。已补充源文件换行约定，并调整流水线：在一个环境重复构建并比较业务 JAR，随后四平台复用同一份 JAR。该改进已通过，下载后对四个平台 tarball 内的 JAR 重新计算 SHA-256，均为 `d3b0d706bb74481614c66c6f87eb5f7b8658b52303529aa9e640bb89ac94e561`。这是同环境重复构建和同产物复用证据，尚不等于所有环境/工具链完全复现。

截至本次更新，三库元数据读取/精确过滤/生成、四平台中文夹具 UI 及用户文档人工验收已通过；原机器记录未执行的步骤仍按历史范围保留。仍未完成：数据库账号权限和 SQL 审计、最低 OS/libc、完整 native 分发/链接源码审查、所有构建输入的字节级固定与复现、npm 权限及 beta registry 安装。native manifest 的 `redistributionApproved` 仍为 false；正式打包和发布拒绝门槛保持生效。
