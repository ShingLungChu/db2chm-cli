# 私有 npm 与 CHM 验证记录

日期：2026-10-26。本记录只包含合成元数据和公开构建证据，不包含真实数据库信息。私有验证包不是 RC，不能据此认定已完成正式 npm 发布。

## 四平台功能通过

当前完整验证的源码提交：`4826b393c361a9deb6d0b44c80ef29025bd235d4`。

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

## 安装体积

按入口 tgz＋当前平台 tgz 计量，含 JAR、实际 native、精确 utility 源码、补丁和许可证；没有第三方 JavaScript 默认依赖。用户预装的 Java 和显式外置 JDBC 不在默认下载链中。

| 平台 | 平台 tgz / 字节 | 入口 tgz / 字节 | 安装下载总量 / 字节 |
| --- | ---: | ---: | ---: |
| Windows x64 | 2,635,419 | 2,471 | **2,637,890** |
| macOS Intel | 3,147,118 | 2,483 | **3,149,601** |
| macOS Apple Silicon | 3,114,688 | 2,478 | **3,117,166** |
| Linux x64 | 2,706,516 | 2,478 | **2,708,994** |

已下载并重新计算每份 tgz 的哈希、大小和内含 JAR 哈希；[机器可读汇总](evidence/native-validation-4826b39.json)保存精确值。所有体积结论绑定具体私有 tarball，不作为尚未形成的 RC 尺寸承诺。各 runner 生成的入口归档字节可有差异；最终发布只会使用候选汇总中选定并验收的同一份入口 tgz。

## Windows 二进制

- 固定 utility 源码：FPCSource `de69d52048cf52c72ebfeef61617c04c9bc2005c`。
- 官方 bootstrap：3.3.1；ZIP 与实际编译器分别校验哈希。
- 产物：PE32+ x86_64，2,375,953 字节。
- SHA-256：`152c318f261c8ebcb7d505b4a2329a09efcb58fbe84508dd1540d3a0f5af6d2c`。
- 导入：kernel32.dll、oleaut32.dll、user32.dll；没有额外 FPC 运行库 DLL 安装前提。
- 本机临时构建、新构建脚本和 Windows CI 的该产物哈希一致。该证据尚不能代表所有平台/native 完全复现，也不能由 PE 头版本推断最低 Windows 版本。

## 复现边界与后续

首轮四平台独立构建时，Windows JAR 中模板、CSS、properties 和 Maven 生成描述使用 CRLF，其他平台使用 LF；业务 class 内容一致。已补充源文件换行约定，并调整流水线：在一个环境重复构建并比较业务 JAR，随后四平台复用同一份 JAR。该改进已通过，下载后对四个平台 tarball 内的 JAR 重新计算 SHA-256，均为 `d3b0d706bb74481614c66c6f87eb5f7b8658b52303529aa9e640bb89ac94e561`。这是同环境重复构建和同产物复用证据，尚不等于所有环境/工具链完全复现。

以下尚未通过：Oracle/DM 真实数据库及权限审计、Vastbase 实测、Windows 查看器目录/索引/搜索交互、最低 OS/libc、完整 native 分发/链接源码审查、所有构建输入的字节级固定与复现、npm 权限及 beta registry 安装。native manifest 的 `redistributionApproved` 仍为 false；正式打包和发布拒绝门槛保持生效。
