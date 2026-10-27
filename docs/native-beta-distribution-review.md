# 当前 chmcmd 二进制的 beta 分发核查

核查日期：2026-10-27。对象为已验收、源自应用提交 `4de172f8d950fa9f10034b885859aebbae9c9512` 的四个平台 chmcmd；utility 上游固定在 `de69d52048cf52c72ebfeef61617c04c9bc2005c`。本记录不修改任何批准标志，也不把待补材料写为已交付。

**结论：保留现有 native 字节、补齐下面列明的源码/许可/重建材料，是可执行的 beta 分发路径；没有依据把“先从源重建全部 RTL/FCL”作为本次分发的一概前提。** 完全固定所有系统输入、逐一证明每个 PPU 的源码身份和在所有最老系统上实测，仍是原计划的复现/支持范围交付目标，不能借此结论改写为完成。

现有原型的 utility 源码完整性已核对；改动日期说明、随包可用的重建说明/脚本、准确组件许可声明和适用范围还应在可发布包中补齐。下述判断以这些材料实际交付为条件。

## 1. 第一方条款支持什么

| 第一方来源 | 相关原文/事实 | 本项目采用的分发路径 |
| --- | --- | --- |
| [FPC COPYING.FPC](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/rtl/COPYING.FPC) | 明确适用于 FPC 的 RTL 与 packages；允许将库与 independent modules 链接，并复制、分发生成的可执行程序，但须满足每个独立模块本身的许可 | 未修改 RTL/FCL 的链接例外可适用；不能由“静态链接”直接推出必须先自行重建全部运行库，也不能把例外推广成对任意第三方模块的授权 |
| [FPC 官方许可 FAQ](https://www.freepascal.org/faq.html#general-license) | “Most people can satisfy the source code requirement by mentioning that the RTL source code can be downloaded at the Free Pascal web site: if you did not modify the rtl this is considered adequate…”；并要求随产品附 modified LGPL，标明其适用部分 | 对没有修改的 FPC runtime，保留完整例外/许可并提供准确可取得的上游及发行版来源。源码精确重建尚未验证，应如实说明，不能因此冒充运行库已修改或已验证同源 |
| [chmcmd.lpr 原许可](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/src/chmcmd.lpr) | GNU Library GPL version 2 or any later version；它是已有 `program chmcmd` 的修改版 | 此工具自己的义务需要履行；不能仅因使用 FPC 就把工具当作自有闭源输出。可按兼容的 LGPL 2.1 路径提供工具源、修改和许可 |
| [LGPL 2.1 §2(b)、§4](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/rtl/COPYING.txt) | 改动文件须有显著的修改和日期声明；可执行形式须伴随完整对应机器可读源代码，或同一指定下载位置的等价源码访问 | 在每个实际修改源文件内写变更/日期；最直接的做法是源码归档与二进制同一 npm 平台包交付，不依靠尚不存在的未来外链或三年书面要约 |
| [LGPL 2.1 §6(a)](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/rtl/COPYING.txt) | `work that uses the Library` 可以以 “object code and/or source code” 提供，使用户可修改库并重新链接出可执行文件 | 完整 utility 程序源、LZX 源和可用构建/链接方法是有效材料形式；并非必须另交一套预编译 application object 才能重链。不能禁止对 native 的修改及为调试修改而进行的逆向 |
| [LGPL 2.1 源码定义与 §6](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/rtl/COPYING.txt) | 对应源包括接口文件及控制编译/安装的脚本；§6 对一般随 compiler/kernel 等主要系统组件提供的材料有例外 | 随工具源提供实际可执行的编译脚本、参数、工具/库前提和来源。没有“所有源码必须先证明逐字节复现”的许可文字；重链能力与字节级可复现是两项不同结论 |

FAQ 是 FPC 对其自有例外的第一方说明，不能用它覆盖下节的独立第三方条款。工具自己的 LGPL 源码义务仍单独履行。

## 2. 当前实际编译组件和第三方边界

已只读检查四个 native artifact 的 `build-provenance.json` 和 `chm-source.tar.gz`：**每个平台记录的 15 个 compiledUtilitySources 都逐个与同包源码归档 SHA-256 匹配。** 归档还保留 `packages/chm/` 的其他原始源、include、fpmake 和 Makefile。此核对只覆盖 utility，不把它误称为 RTL/FCL 源身份核验。

| 组件 | 实际状态及许可 | 必须保留的材料 |
| --- | --- | --- |
| chmcmd、chmbase、chmwriter、chmfilewriter、chmsitemap、chmfiftimain、htmlindexer、htmlutil、chmspecialfiles、chmtypes、lzxcompressthread | 15 个源中的主体为 LGPL-2.0-or-later；若引用 COPYING.FPC，保留该引用和原版权 | 完整修改后源、原头、LGPL 全文、实际编译方法、两份补丁及修改说明 |
| [新 chmencoding 源](../release/patches/chmcmd-encoding.patch) | db2chm 新增，现有头明确 LGPL-2.0-or-later；目前仅有年份，没有具体变更日期 | 新增/变更日期说明；不能用 Java/Node 的 UNLICENSED 文字限制此单元的 LGPL 权利 |
| [paslzxcomp.pas](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/src/paslzxcomp.pas) | 实际参与编译；文件总头为 LGPL-2.0-or-later/引用 FPC 例外，内部仍保留 Matthew T. Russotto `lzx_compress.h` 的 LGPL **2.1 only** 声明 | 保留两个原头和 Russotto 版权；按 LGPL 2.1 提供压缩库源和可修改/重链整个工具的程序源。不能笼统宣称全部 LZX 代码都只有 FPC 例外 |
| [paslznonslide.pas](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/src/paslznonslide.pas) | 实际参与编译；上游写明经原作者许可更新版权并引用 COPYING.FPC | 原版权、许可与实现完整保留 |
| [fasthtmlparser.pas](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/src/fasthtmlparser.pas) | 实际参与编译；独立宽松条款：“This code may be used and modified by anyone so long as this header and copyright information remains intact.” | James Azarja/Jazarsoft 与 Lars 的原始 header、版权、免责声明完整保留，并作为独立 LicenseRef/第三方说明记录；不能把它重新标成只有 LGPL |
| [paslzx.pas](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/src/paslzx.pas) | 解压单元；**不在四个平台当前 15 个 compiledUtilitySources 中**。它随完整 package 源归档保留，头有 LGPL-2.0-or-later 与 COPYING.LCL 引用 | 原文与 LGPL 全文保留；不编造不存在的 COPYING.LCL 例外，也不据此声称当前压缩程序链接了该解压单元。该未知附加例外不能直接当作当前 native 二进制的阻断 |

`paslzxcomp`/`paslznonslide` 均为归档内 Pascal 实现，未发现依靠未交付的闭源 LZX DLL/对象才能重链的路径。程序的完整 15 个 utility 源、所需 include 与公开 FPC 库前提齐备时，可以从源码重新编译并链接。实际调用关系也可由[上游 fpmake](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/fpmake.pp)核对：压缩线程依赖 paslzxcomp；paslzx 归属 reader 分支。

## 3. Java/Node、编译产物与许可范围

[NativeCompiler.java](../src/main/java/com/db2chm/generator/NativeCompiler.java) 使用独立 `ProcessBuilder(argv)` 调用编译器，通过 HHP/HTML 文件交换数据；Java JAR 没有把 Pascal CHM 单元静态/动态链接到自身。npm 包将独立工具放在 `native/`，是分别识别的组件。

因此，项目自身 Java/Node 的许可字段与第三方 native 的许可必须明确区分。`package.json` 的 UNLICENSED 不能被描述为覆盖或撤销 LGPL native 的修改、分发和调试重链权利。建议在包级 THIRD_PARTY_NOTICES 和 native 说明明确这个范围，并分别标示 LGPL/FPC 例外、LGPL 2.1 LZX 部分、FastHTMLParser 宽松许可。Microsoft HHC 不随包交付，不纳入这条 native 分发判断。

程序现有 `--compiler chmcmd --compiler-path PATH` 能调用用户自行重编译的工具；Java 的路径检查没有要求修改版仍具有官方二进制 SHA-256。重建工具仍应维持所需 `--db2chm-encoding` 接口，这是应用调用协议，不能被文档解释为禁止对工具进行修改。

## 4. 本次可发布包应实际补齐的清单

1. 对 `chmcmd.lpr`、`chmwriter.pas`、`chmsitemap.pas`、`htmlindexer.pas`、`chmfiftimain.pas` 及新增 `chmencoding.pas` 在文件内添加显著说明：db2chm 修改、具体日期 2026-10-27、修改目的；完整保留原作者头。两份补丁与源码注释之间的关系要写清。
2. 同包提供完整可编辑的 utility 源、include、原上游构建文件、实际采用的重建脚本/完整参数及 bootstrap/FPC/FCL 前提。若归档已应用两份补丁，重建步骤不得再重复 apply。
3. 将 LGPL 2.1 全文、FPC COPYING.FPC 原文及原始第三方版权/条款置于清楚可找到的位置；FastHTMLParser 独立列出。不要用上游顶层混合 LICENSE 中的 compiler GPL v2 作为所有 native/Java 组件的唯一许可。
4. 保留每个平台的真实 compiler/包/PPU 记录与来源链接。Windows 使用实际 3.3.1 snapshot/完整 revision；Linux 保留 Ubuntu `3.2.2+dfsg-32` 的源描述与发行版补丁来源；macOS 保留实际 Homebrew 配方与源/补丁。未修改 FPC 库采用其链接例外，运行库来源的进一步重建验证继续写成未完成，而不是伪造“精确对应源已全部证明”。
5. 对新增日期前置注释记录“去掉这些注释后与既有 compiled source 哈希逐字节相符”的映射，避免用较新上游源码冒充实际二进制源码；从交付源进行至少一次真实重建/重链检查。若 Windows 再编译与已验收 native 同 SHA，单独记录这个实测结论。它不自动扩展为 macOS/Linux 日期注释版本的逐字节复现承诺。
6. 在发布前对最终 tgz 实际检查：上述材料和通知确实存在、源码可解包、改动源可编辑、重建步骤可执行、原生字节未变化、压缩下载链仍小于 15,000,000 字节。满足这些检查后才可针对该精确包记录 beta 分发审核结论。

此路径没有要求把 FPC 编译器本体装进用户机器、随 npm 交付整个编译器，或先重新构建所有平台的 RTL/FCL。它也没有放宽未完成的原计划 stable 交付项：运行库源码身份/完全固定输入、最低系统实测、数据库账号和 SQL 审计仍应如实保留。用户明确发布请求可支持调整 beta 的项目验收策略，不能代替第三方许可材料或把未知事实写成通过。

## 5. 本次核查的原始归档身份

| 平台 | 15 个已编译 utility 源核对 | 当前 chm-source.tar.gz SHA-256 |
| --- | --- | --- |
| Windows x64 | 全部匹配 | `be67d6aeb17c9c544a3f945a7780a12927d30d1d5bae3f9e40a10b25bea8ef58` |
| Linux x64 | 全部匹配 | `0be63ecb07696c3fb4dcb523376150e646b6ffea1d23231a1c3d8f5af207ed11` |
| macOS Intel | 全部匹配 | `63609777bf9601d0ac6ba51cf9d97a0660a6c36add5ea3e935dd29ea928250d8` |
| macOS Apple Silicon | 全部匹配 | `0c6595b4900b7fec288aaf77c61ceb0791ba25e3e27e2881dbab019fb22037c4` |

这些是原始归档的身份；新增通知、源码日期注释或重建脚本后的发布归档会产生新的哈希，必须重新保存并验收，不能把上表移作新归档的哈希。
