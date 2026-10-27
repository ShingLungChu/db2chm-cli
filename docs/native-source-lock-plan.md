# 原生库对应源码的锁定与随 npm 交付方案

2026-10-27。此文给出后续实现所需的来源锁定数据与验证步骤；**不表示这些材料已经进入 npm 包，也不改变分发审批状态。** 新发行 scope 为 `@jackiechen1994`；本轮来源研究仍引用 `2e63e20` 的旧 scope 私有产物，因为它们保存了所用工具链和来源信息。

可行方向是把准确的 CHM 修改源、实际 RTL/FCL 组件家族源、必要构建文件及完整许可压缩放入平台包。基于本机源码的保守容量估算，包含整个 RTL 与当前五个单元家族的源码仍有机会满足 15,000,000 字节。能否正式通过以准确来源重建后实际 `npm pack` 的下载链为准。

## 1. 已取得且可直接写入锁文件的数据

### Linux：使用 Ubuntu 发行版源码，不把 release tag 冒充 `+dfsg-32`

`native-linux-x64/bootstrap-packages.txt` 记录四个实际安装包均为 `3.2.2+dfsg-32`：compiler、units-base、units-fcl、units-rtl。对应第一方 Ubuntu 源描述可直接取得：

| 文件 | 固定 URL | 字节数 | SHA-256 | 本轮核查 |
| --- | --- | ---: | --- | --- |
| dsc | [fpc_3.2.2+dfsg-32.dsc](https://archive.ubuntu.com/ubuntu/pool/universe/f/fpc/fpc_3.2.2+dfsg-32.dsc) | 3,926 | `22f2b20b721e45bd0dbc4aca61a5691c0d5c0ad5394315ac13d4410bc25f6643` | 实际下载并计算 |
| 上游 DFSG 源 | [fpc_3.2.2+dfsg.orig.tar.xz](https://archive.ubuntu.com/ubuntu/pool/universe/f/fpc/fpc_3.2.2+dfsg.orig.tar.xz) | 33,357,716 | `8534a754b643779919be78b396b386982f924c0c57a0b837609d1124313b123f` | dsc 的 Checksums-Sha256；本轮未下载大文件 |
| Debian/Ubuntu 修改 | [fpc_3.2.2+dfsg-32.debian.tar.xz](https://archive.ubuntu.com/ubuntu/pool/universe/f/fpc/fpc_3.2.2+dfsg-32.debian.tar.xz) | 131,584 | `7c6490972d6b1e66f419c1ccbd5aeaff6908f383676fff107d2bbcb7936c323b` | 实际下载，哈希与 dsc 一致 |

dsc 的 Source 为 `fpc`、Version 为 `3.2.2+dfsg-32`、Format 为 `3.0 (quilt)`。本轮读取了签名封装和校验字段，未宣称执行过 OpenPGP 签名信任验证。构建流程还应使用 Ubuntu/Debian 受信 keyring 或已受信 Sources/InRelease 链核对来源身份。

实现时下载这三个固定文件，先逐字节校验大小/哈希，再执行 `dpkg-source -x` 解包并按 `debian/patches/series` 应用修改。源树根下实际 FPC 源在 `fpcsrc/`，不能在尚未应用发行版补丁前就提取 RTL/FCL。保存 `series`、补丁、`debian/rules`、构建配置和元数据，供从交付材料重建。

本轮实际读取的 series 含 `glibc2.34.patch`、`honor_SOURCE_DATE_EPOCH_in_date.patch`、`Fix-liking-with-libc-when-PIC-is-enabled.patch` 等，以及多个上游回移补丁。因此仅写 `release_3_2_2` 不充分。也不建议把 `fpc-source-3.2.2.deb` 当成完整重建材料：同一 `debian/rules` 的 `binary-indep` 会过滤源码包中的非 `.fpc/.inc/.pas/.pp` 文件，Makefile 和完整许可需要另行保留。[Ubuntu 源描述](https://archive.ubuntu.com/ubuntu/pool/universe/f/fpc/fpc_3.2.2+dfsg-32.dsc)

锁文件还应记录所用 deb 的确切 SHA-256（后续从受信 Packages 元数据读取）、安装单元 PPU/对象文件哈希与本机实际 fpc 可执行文件哈希。上表没有猜测或填入尚未查询的二进制包哈希。

### macOS：两个 formula 提交分别锁定，共用同一准确源与补丁

两个 CI 的 `bootstrap-packages.json` 保存的 formula 身份不同。本轮分别下载固定 Git 提交下的 Ruby 文件，计算所得哈希与 artifact 内 `ruby_source_checksum.sha256` 完全一致：

| 平台 | homebrew/core 提交 | formula SHA-256 | 字节数 |
| --- | --- | --- | ---: |
| Intel | [`09417d15c1d23155fd7474db26918bbaebf76235`](https://github.com/Homebrew/homebrew-core/blob/09417d15c1d23155fd7474db26918bbaebf76235/Formula/f/fpc.rb) | `a2b22e112dd86f4c7b78d939815ae7b898061e9d3af12f2c3370047e8d36052b` | 5,695 |
| Apple Silicon | [`50ecf5c5a7dc04bb5f9dc2552b072216909f5917`](https://github.com/Homebrew/homebrew-core/blob/50ecf5c5a7dc04bb5f9dc2552b072216909f5917/Formula/f/fpc.rb) | `e639b1ad7a21890bf48e5723d831c0fd175dbbff9830607f6e0c90cdfd1549b6` | 4,928 |

可直接锁定的源码与补丁：

```json
{
  "version": "3.2.2_1",
  "source": {
    "url": "https://downloads.sourceforge.net/project/freepascal/Source/3.2.2/fpc-3.2.2.source.tar.gz",
    "sha256": "d542e349de246843d4f164829953d1f5b864126c5b62fd17c9b45b33e23d2f44"
  },
  "patches": [
    {
      "url": "https://gitlab.com/freepascal.org/fpc/source/-/commit/a20a7e3497bccf3415bf47ccc55f133eb9d6d6a0.diff",
      "sha256": "7fac043022a6f3ffa91287f856c2c4959fdc374ff9169a244eaf55a3897c76d8",
      "bytes": 538,
      "strip": 1
    }
  ]
}
```

源归档哈希来自已核验的 formula 和 CI 元数据，本轮未下载该大归档；补丁已实际下载并核对哈希。该补丁只修改 `rtl/linux/aarch64/cprt0.as`。它不参与 macOS 的运行路径，但仍应保留为该 formula 实际源码配方的一部分，不能因“可能用不到”就把来源记录改成纯上游 release。

对应已安装 bottle 的内容身份为：

- Intel `sonoma`：`c5fb604a561ff84c67c6a93f2be94adf6f74b7ddbeb9cdfc1def6929ed2a8e59`，rebuild 0。
- ARM `arm64_tahoe`：`83461758109bbaece09f36f9083f5e95f9be24c2d189de5f5551c0a0b36c1b5c`，rebuild 1。

下载路径按元数据中的 `https://ghcr.io/v2/homebrew/core/fpc/blobs/sha256:<digest>` 绑定。不能把 Intel formula 中另一份旧 `arm64_tahoe` bottle 的 hash 移用到本轮 ARM。后续应保留实际 bottle 的安装收据、重定位信息、文件哈希及 SDK/部署目标；formula 源身份本身不证明已安装的库逐文件同源。

两份 formula 都记录了 `make build`、`make install` 的准确步骤，以及 macOS bootstrap DMG：URL `https://downloads.sourceforge.net/project/freepascal/Mac%20OS%20X/3.2.2/fpc-3.2.2.intelarm64-macosx.dmg`，SHA-256 `05d4510c8c887e3c68de20272abf62171aa5b2ef1eba6bce25e4c0bc41ba8b7d`。需要重建编译器时按固定 formula 的对应版本处理；只构建库也要保留实际采用的编译器和配置信息。

### Windows：锁定 814ece 源码，但不能通过 PPU CRC 倒推出该源码

当前已校验 bootstrap 的 revision 为 `3.3.1-21298-g814ece0205`，对应第一方完整提交 [`814ece0205a6b9c2c6dad74c8069ebb2ae50d1c7`](https://github.com/fpc/FPCSource/commit/814ece0205a6b9c2c6dad74c8069ebb2ae50d1c7)。源码锁可以使用 `kind: git`、仓库 `https://github.com/fpc/FPCSource.git` 和此完整提交；fetch 后强制校验 `git rev-parse HEAD`。若采用下载 tarball，还须另外计算并固定该准确归档的哈希，不在此文捏造哈希。

编译器本身与 ZIP 的固定身份仍沿用：[Windows 构建脚本](../scripts/build-native-windows.ps1)中的 ZIP `2206d8d866360844e02a2920c4827bd139ec4a8713b14be2afacc04ee3e8d66e`、编译器 `3c230fa579aaa595382b7e7e9daddd00adfd196e9743112bd551141de260b0bc`。

**不能把 ppudump 的 CRC 写成“源码文件校验通过”。** 第一方 [`compiler/fppu.pas` 的 writesourcefiles](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/compiler/fppu.pas)给源文件表写的是路径和 `getfiletime`，写此表时还临时禁用 `do_crc`；[`compiler/utils/ppuutils/ppudump.pp`](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/compiler/utils/ppuutils/ppudump.pp)也只将源文件名和时间输出。PPU 头的 checksum、interface_checksum、indirect_checksum 是编译单元与依赖身份，不是原始源文件 SHA-256。[PPU 格式实现](https://github.com/fpc/FPCSource/blob/de69d52048cf52c72ebfeef61617c04c9bc2005c/compiler/ppu.pas)

因此 ppudump 可用于枚举 include 路径、文件名/时间和 PPU 依赖，帮助建立源码闭包；它不能单独证明 ZIP 内预编译库恰来自 `814ece…`。本轮未找到当前已解包 bootstrap 中可直接运行的 ppudump 可执行文件，以上判断来自实际读取的第一方格式源码，没有声称运行过该工具。

最明确的闭合办法是从 `814ece…` 固定源重建所需 RTL/FCL，之后让 chmcmd 明确链接这些新构建库。仍可使用当前已校验 bootstrap 编译器；新的 native 必须重新绑定源文件/PPU/对象哈希及回归结果。若坚持保留原 ZIP 库，则需要上游准确库构建材料或同配置重建比对，仍不能只靠 `-iW` 批准。

## 2. 组件家族闭包如何确定

当前 [Windows 输入证据](evidence/native-inputs-win32-2e63e20.json)含 41 个预编译单元，按实际单位目录分为：

| 单元目录 | 数量 | 拟收录的源家族 |
| --- | ---: | --- |
| rtl | 18 | `rtl/`，涵盖平台、CPU、公共 include 与 build 文件 |
| rtl-objpas | 6 | `packages/rtl-objpas/` |
| rtl-generics | 6 | `packages/rtl-generics/` |
| fcl-base | 3 | `packages/fcl-base/` |
| fcl-xml | 8 | `packages/fcl-xml/` |

上表是本轮 Windows 的编译输入包络，不是完整链接映射，也不代表 Unix 必然恰为这五类。待新四平台日志生成后，按每个平台的实际输入分别计算家族集合；任何未知家族立即使源码打包检查失败，再补映射，不能静默忽略。

优先收录整个相关源家族及原许可/include/Makefile，避免只挑 41 个入口 `.pas/.pp` 丢掉传递 include、汇编、生成器或跨单元构建依赖。顶层构建文件、包注册/fpmake 文件、编译参数和所需的构建辅助程序应作为闭包补充；是否足够以只使用交付材料的重建检查确定。不能为了减少大小先删除“看起来不运行”的来源/许可文件。

`rtl/` 目前包含多个 OS/CPU。若实包超限，第二轮可从完整家族包络中按实际源文件/include/构建辅助工具闭包裁切，并保存完整的逐文件路径/哈希清单；验证每个目标的干净重建。不能只按目标 OS 目录名删除全部其他目录，因为通用 include、CPU 与平台间共享源会跨目录引用。

## 3. 15 MB 的容量判断

本轮对已有本机 `de69d5…` 源码 checkout（仅 CHM 应用了本项目补丁）执行只读 tar→内存 gzip 测量；下面采用主 agent 复核的 Python tarfile＋GzipFile(mtime=0) 结果，归档元数据规范化为 uid/gid/mtime=0，确认 gzip 文件头。未下载全部大源码、未创建分发包：

| 代理测量范围 | 压缩归档字节 |
| --- | ---: |
| 整个 `rtl/` | 9,262,686 |
| `packages/rtl-objpas`、`rtl-generics`、`fcl-base`、`fcl-xml` | 931,833 |
| 上述全部 + `packages/chm/` + 顶层 LICENSE | 10,329,152 |

同轮 [2e63e20 平台包证据](evidence/native-validation-2e63e20.json)中，选择统一入口后的 macOS Intel 安装链为 3,162,310 字节；把 10,329,152 字节源归档完整加上，保守相加约为 **13,491,462 字节**，距硬上限还有 1,508,538 字节。旧包已经带有较小的 CHM 源归档，正式替换时不应重复收录它。

这是有实测依据的可行性估算，**不是准确 `814ece…`、Ubuntu `+dfsg-32` 或 Homebrew 3.2.2 源码候选的尺寸结论**。实际代码版本、发行版补丁、完整 build 辅助文件、许可和 scope/记录变更都会影响大小；两个已经压缩的归档再进入 npm tgz 也不能假定可显著二次压缩。正式门槛必须对最终 tgz 的真实字节再次计算。

## 4. 建议落地的锁文件与构建步骤

建议增加 `release/native-sources.lock.json`，对每个平台分别保存 utility 与 runtime 的 source identity，使用上节已经核实的值；同时标示资料层级 `hash-verified-download`、`checksum-from-verified-descriptor`、`git-commit-verified`，不把未下载源归档写成实测通过。

构建和验收顺序：

1. 下载描述文件、源归档、发行版/配方补丁；逐个校验固定 URL、字节数及 SHA-256。补充第一方签名或受信仓库元数据验证，提取前拒绝绝对路径、`..`、逃逸链接。
2. 在新工作目录建立准确的源树：Linux 使用 dsc/quilt 配方，macOS 使用固定 source＋formula 补丁，Windows 使用完整 Git 提交；保存源树逐文件哈希和应用补丁序列。
3. 从该源重建相关 RTL/FCL，并在隔离配置中明确指定这些输出，阻止回落到未记录的全局预编译 units。保留实际加载 PPU、对象文件与库/链接清单。用相同配置重新构建 chmcmd，保留 utility 与 runtime 两条来源身份。
4. 生成规范化、路径安全的 `native-corresponding-source.tar.gz`：准确 CHM 修改源、需要的 runtime 家族、传递 include/汇编/构建文件、补丁、许可证/例外、修改日期和可执行的重建说明。归档顺序、mtime、uid/gid、gzip 时间戳固定，并输出逐文件哈希清单。
5. 在干净环境仅解开这份归档，使用文档声明的预装构建工具和 SDK 重建；确认未读取项目 checkout 或其他未记录源。重建缺文件时扩大闭包，不能伪造通过。复现字节与语义回归分别记录。
6. 将准确源码归档放入对应平台 npm 包，在 manifest 和 SBOM 中标示其真实许可、版本、SHA-256 与用途。无需在用户安装时编译或额外下载源码/工具。
7. 从干净源码打包，执行四平台安装→CLI→中文 CHM/导航/解包回归；同时校验入口＋平台 tgz＋默认传递依赖的总和严格小于 15,000,000 字节。源码包和依赖报告均绑定同一 native/业务 JAR/源码提交。
8. 材料和实际检查齐备后，才更新对应精确 manifest 的 `sourceDistributionReview` 和分发结论；库来源或完整重建尚未通过时，保持待验收。

这条路线让默认下载链包含可核查的对应源码，并有机会保持小于 15 MB；当前研究提供了可实现的锁定数据和验证方法，没有声称已交付源码闭包或完成四平台 native 发布。
