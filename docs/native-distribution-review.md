# chmcmd 原生分发与源码核查

核查日期：2026-10-27。对象为修复版本 `2e63e2017b12fca13bad3d334f7081414d097c65` 的构建方式及当前本机 Windows native 文件。此记录只核查 native；用户确认文档人工验收通过的结果由交互报告另记。本记录没有改变 `redistributionApproved`。

**结论：现有 utility 源码、补丁和许可保留方向正确，但四个平台的正式 native 分发证据尚未闭合。** 主要欠缺是实际链接组件清单、bootstrap 库对应来源、修改文件的日期声明和可重建材料。不能把这些问题概括为“FPC 不能商用”，也不能因二进制很小或 CHM 验收通过就自动批准分发。

## 1. 已核查的第一方依据

| 依据 | 原文/事实 | 对本项目的含义 |
| --- | --- | --- |
| [chmcmd.lpr](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/src/chmcmd.lpr) 的开头许可 | GNU Library GPL version 2 or later；入口为 `program chmcmd` | 单独分发这个已有程序的修改版，不能把它当作编译器生成的自有闭源代码 |
| [chmwriter.pas](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/src/chmwriter.pas)、[chmfilewriter.pas](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/src/chmfilewriter.pas) | 保留 LGPL 许可，并引用 COPYING.FPC；uses 中含 Generics.Collections、Classes、SysUtils、Contnrs、StreamEx、AVL_Tree、DOM/SAX_HTML 等 | 除 CHM 源码外，还使用 RTL/FCL 等单元；只看动态 DLL 列表不能得到全部静态组件 |
| [RTL COPYING.FPC](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/rtl/COPYING.FPC) | 对 independent modules 提供链接例外，允许按自选条款分发结果，同时要求遵守每个独立模块的条款 | 不能将“未捆绑全部未修改 RTL 源码”直接判为违反许可；也不能把链接例外自动扩展到每个第三方片段或改写原工具的许可 |
| [paslzxcomp.pas](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/src/paslzxcomp.pas) | 文件头为 LGPL 2 or later/引用 COPYING.FPC；内部保留 Matthew T. Russotto `lzx_compress.h` 的 “version 2.1 only” 声明 | 组件清单应保留此原始第三方版权和许可，不应仅给整个 native 写一个笼统的 “FPC exception” |
| [paslznonslide.pas](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/src/paslznonslide.pas) | 明确写出 “Renewed copyright, with permission of the author”，并引用 COPYING.FPC | 上游已有例外来源线索；不应删除此版权/许可说明 |
| [paslzx.pas](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm/src/paslzx.pas) | 指明 chmlib/cabextract/unlzx 来源和经 Stuart Caie 许可的 modified LGPL，另引用 COPYING.LCL | 固定源码树内未找到该命名文件，不能凭文件名自行创造例外条款；可保留完整 LGPL 2.1 交付路径，或进一步取得对应上游例外原文 |
| [LGPL 2.1 正文](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/rtl/COPYING.txt) §2(b)、§4、§6 | 修改文件应标示变更及日期；对象码/可执行文件的源代码交付与可重新链接材料有明确路径；§4 和 §6(d) 规定指定下载地点的等价源码访问 | 分发策略必须落到实际可取得的准确材料；仅写泛用 GitHub 首页链接不足以证明已满足选择的交付路径 |

已直接读取本机归档中的上述源码与许可，并核实用于归档的原始 checkout 为 `de69d52048cf52c72ebfeef61617c04c9bc2005c`。外部引用固定到该提交，不依赖会移动的 main 分支。

## 2. 当前材料覆盖与缺口

当前 Windows `chm-source.tar.gz` 的 SHA-256 为 `c15e325367d2d0ef4f608a1c5e905367d19e7a51462a1218f60f40ae27f3363a`。它包含补丁应用后的 `packages/chm/`、顶层 `LICENSE`、`rtl/COPYING.FPC` 和 `rtl/COPYING.txt`；`chmencoding.pas` 已在归档中。两份应用补丁也作为独立文件交付。这能覆盖当前 CHM utility 的实际修改内容。

归档没有 RTL/FCL 的源码、精确发行版补丁、工具链配置或完整重建说明。当前构建命令从 bootstrap 的预编译单元解析 CHM 以外依赖；它们并不保证来自 utility 的 `de69d5…` 提交。`-B` 也不会在未提供库源码的情况下将所有预编译库自动变成该提交的源码构建。[Unix 构建脚本](../scripts/build-native.sh)、[Windows 构建脚本](../scripts/build-native-windows.ps1)

这里有两个不同的问题：

- **许可交付：** 先确定实际链接的单元、其许可和可适用例外，再按选定的 LGPL 路径提供对应材料。未修改、可适用例外的 RTL 不因静态链接就一概要求捆绑全量源码；有不同许可或无例外的第三方部分需要单独处理。
- **本项目交付标准：** T02/T11 承诺固定输入、来源与可复现构建。即使某个库的链接例外使分发较宽松，缺少实际单元来源/配置仍不能把该承诺标为完成。

现有 `FPC-LICENSE.txt` 是上游顶层混合说明，其中还带编译器 GPL v2；`RTL-COPYING.txt` 实际为完整 LGPL 2.1。不能把前者当成 chmcmd 唯一许可，也不能把编译器本身的 GPL 自动套到 Java JAR。当前 new unit `chmencoding.pas` 标为 LGPL-2.0-or-later；其余被改动的上游文件主要仍保留旧头。建议在每个实际修改文件中加上清楚的 db2chm 修改说明及日期，保留原版权和许可，并随源码加入补丁/构建说明。

本机 Windows 可执行文件仅导入 kernel32.dll、oleaut32.dll、user32.dll 的报告，说明不需要额外 FPC 动态运行库；它不证明二进制没有静态 RTL/FCL。[PE 检查脚本](../scripts/inspect-pe.cjs)

## 3. Windows 3.3.1 snapshot 已找到来源线索

现有 bootstrap ZIP 固定哈希为 `2206d8d866360844e02a2920c4827bd139ec4a8713b14be2afacc04ee3e8d66e`，编译器文件固定哈希为 `3c230fa579aaa595382b7e7e9daddd00adfd196e9743112bd551141de260b0bc`。[Windows 构建脚本](../scripts/build-native-windows.ps1)

对本机同一已校验编译器读取版本：

```text
ppcrossx64.exe -iW  →  3.3.1-21298-g814ece0205
ppcrossx64.exe -iD  →  2026/10/12
```

第一方 [FPCSource commits API](https://api.github.com/repos/fpc/FPCSource/commits/814ece0205) 将短号解析为完整提交 [`814ece0205a6b9c2c6dad74c8069ebb2ae50d1c7`](https://github.com/fpc/FPCSource/commit/814ece0205a6b9c2c6dad74c8069ebb2ae50d1c7)，提交日期为 2026-10-12；该提交的 [RTL 例外文件](https://github.com/fpc/FPCSource/blob/814ece0205a6b9c2c6dad74c8069ebb2ae50d1c7/rtl/COPYING.FPC)也可直接取得。

因此，**不必仅因 snapshot 名称就立即换成 Windows 3.2.2。** 优先保留已经验收的编译器链，补录 `-iW/-iD`、完整源码提交和 ZIP/编译器哈希。随后还要证明 ZIP 内使用的预编译 RTL/FCL 与这个源码基线一致，例如保留 PPU 中的源文件校验信息，或直接从该固定提交重建所需库并保存单元/链接清单。编译器版本串本身不能证明 ZIP 内每个库的源码身份。

若无法建立 ZIP 库的来源，才考虑统一使用固定的正式 FPC 3.2.2 工具链。现有兼容补丁已帮助 Unix 3.2.2 构建，但不能据此宣布 Windows 替换已通过；替换后必须重跑四平台包/CHM 回归及 Windows 验收。工具链改变产生的是新 native，不应复用当前二进制的通过记录。

## 4. Unix 3.2.2 还需要发行版层的身份

第一方 [release_3_2_2 ref API](https://api.github.com/repos/fpc/FPCSource/git/ref/tags/release_3_2_2) 当前解析到 `0d122c49534b480be9284c21bd60b53d99904346`；其 [RTL 例外](https://github.com/fpc/FPCSource/blob/0d122c49534b480be9284c21bd60b53d99904346/rtl/COPYING.FPC)可取得。

不过 Linux 的 `apt-get install fp-compiler-3.2.2 fp-units-fcl-3.2.2` 和 macOS 的 `brew install fpc` 都会读取仓库当前版本。[native 工作流](../.github/workflows/native-toolchain.yml)虽保存 dpkg/brew 信息，`3.2.2` 检查仍不能代替发行版修订号、包/瓶哈希及源码补丁。

| 平台 | 应补齐的确切材料 |
| --- | --- |
| Linux | 所用 deb 的完整版本/架构/SHA-256，对应 `.dsc`、上游源归档及 Debian/Ubuntu patch 归档和校验；实际安装单元清单；基础镜像与链接器版本 |
| macOS Intel/arm64 | 实际 formula 提交、bottle tag/重定位信息及 SHA-256、其源码归档与补丁；编译器完整版本/CPU；SDK/链接器版本及部署目标；实际链接单元清单 |
| Windows | 上节中的完整 compiler revision、ZIP 与 PPU/库来源映射；编译器/链接器参数；实际静态单元与系统 DLL 清单 |

最务实的统一方案是将“用于编译的 bootstrap 工具”和“实际链接进 chmcmd 的库源码”分别锁定：前者固定下载与哈希，后者从明确提交/发行版补丁重建。无需把 FPC 安装进最终用户机器；这些材料属于构建和源码分发流程。

## 5. 在 15 MB 目标内的实施顺序

1. 为每个平台输出 `build-provenance.json`：utility 提交/补丁哈希、compiler `-iW/-iD` 和可执行文件哈希、库来源、系统包/瓶哈希、SDK/链接器信息、编译参数与静态单元清单。实际构建日志使用可追溯单元加载/链接输出，避免只记录动态依赖。
2. 为修改过的 CHM 文件补日期声明，保留每个第三方版权和许可；源码包包含准确修改源、所选链接组件源码/补丁和构建脚本。保留 LGPL 2.1 全文与 FPC 例外原文，明确哪些组件适用例外。尚不能确认的第三方来源不做肯定性例外声明。
3. 源码可设计为独立 GitHub Release 附件，npm 包带完整许可、组件/哈希清单和指向同一版本对应材料的固定链接，默认安装不额外下载该附件。但这只是待实现的交付方案：当前附件不存在；必须按 §4/§6 所选方式核实源码等价访问、长期保留和可重建性，不能仅凭一个外部链接自动放行。
4. 默认 npm 包仍计入其实际携带的许可、清单和必要文件；源码附件若不是默认安装依赖，不计入默认下载链，但须公开报告源码附件大小。不要靠安装时再下载编译器来规避 15,000,000 字节门槛。
5. 在固定输入和干净环境重复 native 构建，对比二进制与源码归档。对时间戳、文件顺序和构建路径产生的差异做规范化或明确记录；JAR 已重复构建不等于 native 已复现。
6. 在实际最低系统运行，再冻结支持矩阵。PE 头、Mach-O 部署字段和 ELF 动态依赖是最低要求线索，不能替代最老声明系统上的运行证据。
7. 上述材料齐备并由正式预检核对后，再更新该精确 native manifest 的分发结论；不通过改布尔值跳过缺口。

这轮核查已给出可执行的补齐路线，但没有证明四平台静态依赖来源完全一致、完整 native 字节复现、最低 OS 或对应源码附件已交付，故不将 T02/T11 标记为全部完成。

## 6. 本轮已落实的追溯改进

两个构建脚本已增加 `-viu` 编译记录及 `scripts/record-native-inputs.cjs`，生成 `build-provenance.json`。Windows 使用固定 utility 源码、当前两份补丁和已校验 bootstrap 重新编译，捕获 41 个预编译 PPU 输入及 15 个实际工具源码；新二进制 SHA-256 与原 2e63e20 CI 完全相同，见[输入及字节证据](evidence/native-inputs-win32-2e63e20.json)。这补充了本平台输入身份和字节复现证据，没有改变既有源码归档或分发批准标志。

记录中的 objectCandidates 是相邻对象文件身份，PPU 列表是编译输入；两者均不等于完整实际链接映射。后续个人 scope 的四平台 CI 已全部完成来源记录，native 与原基线全部相同，见[当前验收](evidence/personal-scope-acceptance-4de172f.json)。发行版补丁与 PPU 的准确源码映射、完整许可/例外、源码附件与最低系统继续按[源锁实施路线](native-source-lock-plan.md)收口。
