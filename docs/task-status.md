# npm 发布任务状态

更新：2026-10-27。源码实施仓库为本仓库；原始参考目录不覆盖迁移。本表按实现、文档、实际验证及剩余依赖分别记录，原型不称为 RC。

## 范围决策

- npm 个人 scope @jackiechen1994，发布账号已登录并核验。用户于 2026-10-27 明确从 @nstc 改为个人 scope；新包重新构建/安装验收，旧包记录保留，不改写历史哈希。
- 统一入口 @jackiechen1994/db2chm，四个平台后缀 win32-x64、darwin-x64、darwin-arm64、linux-x64。
- 预装 Java，整个安装下载链严格小于 15,000,000 字节。
- Windows 已确定优先已安装 HHC，缺席时使用包内 chmcmd.exe；不分发微软文件。已找到的 HHC 报错保留失败，显式 HHC 不回退。
- 用户更新：本版环境优先 Oracle、达梦、Vastbase；最低真实验收为 Oracle 和达梦。MySQL/Kingbase 适配器保留，未验证组合不作正式支持承诺。
- 本机三库配置已完成；真实元数据读取、精确筛选和 CHM 生成通过，原始证据仅存本机。中文 TOC 修复已通过四平台回归及 Windows 中文夹具交互，用户进一步确认生成文档验收通过；文档功能验收关闭，权限/SQL 审计独立保留。

## 任务交付

| 任务 | 状态 | 实现与证据 / 余项 |
| --- | --- | --- |
| T00 | 部分完成 | 源码差异、包名、字节口径已冻结；Node/Java 矩阵实跑通过；三库实测版本已记录，OS/libc 下限及最终支持组合待冻结 |
| T01 | 方案与接入已交付 | 用户确认 HHC 优先、包内 chmcmd 兜底；Windows 实际 npm 安装导出和选择规则通过；不再分发微软编译器；最终 native 审查归 T02/G0 |
| T02 | 部分完成 | 四平台新记录全部通过 CI：Windows/Linux/macOS Intel/ARM 载入单元 41/53/52/52、工具源码均 15 文件；四平台 native 均与原验收基线字节相同。完整对应源/许可、Unix 输入固定和最低系统仍待收口 |
| T03 | 分发/加载方案已交付，支持范围部分验证 | 五驱动版本/哈希及显式外置加载齐备，不分发/自动下载驱动；三库 Windows/Java 8 组合已实测，其他组合不作承诺，见[矩阵](support-matrix.md) |
| T04 | 已交付 | CLI 契约、JSON/退出码、凭据与严格参数处理；12 个平台/运行时组合通过私有安装验证 |
| T05 | 功能与策略已实现，审计未验证 | 固定查询、绑定、URL 能力许可列表、只读要求、超时与脱敏通过测试及三库真实功能复验。用户明确当前没有账号/SQL 审计证据，该项保持未验证 |
| T06 | 已交付 | list/export 共用 glob，Unicode/大小写/包含排除/排序/无匹配有测试；真实环境复核属于 T09 |
| T07 | 已交付 | 2e63e20 的四平台中文标题、目录、中文 HHK、中英文搜索及结果跳转通过；千表末端与 20 列正文通过。用户人工确认生成文档验收通过；保留原始观测范围，见[交互记录](chm-viewer-acceptance.md) |
| T08 | 实现与私有验收完成 | 四平台包与统一入口安装矩阵、实际 bundled native 安装已通过，下载量均低于 15 MB；最终候选仍需 G0–G5 放行 |
| T09 | 文档功能通过，安全审计未闭合 | Oracle/DM/Vastbase 真实读取、精确过滤、导出及导航/正文自动检查通过；四平台夹具交互通过，用户确认生成文档验收通过。账号权限和真实 SQL 审计仍属 G2，不能由文档通过代替 |
| T10 | 已交付 | 独立 skills/db2chm/SKILL.md、README、CLI/安全/依赖/发布文档；skill 校验通过，离线命令示例已执行 |
| T11 | 部分完成 | 个人 scope 五个原始 tgz、9 个核心 artifact digest、四平台来源记录和审查自动汇总均通过；修复 Linux 解析 Windows 报告路径，Ubuntu 重跑通过。本机账号已核验；来源分发/Unix 固定输入/最低系统、候选与发布认证未闭合 |
| T12 | 准备中，尚无 RC | 私有审查包和发布预检已交付；所有 tgz 仍 private，不能冒充已验收候选 |
| T13 | 未开始 | 没有发布 npm；latest 未修改 |

## 已有实际证据

- 个人 scope 完整回归：提交 `4de172f8d950fa9f10034b885859aebbae9c9512` 的[四平台原生/安装/导航/解包](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40445394922)、[12 运行组合](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40445395011)通过；16 个 CHM 场景、4,076 个正文文件解包一致。修复路径后的[Ubuntu 自动汇总](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40446815947)通过。本机 16 项测试通过；CI 各运行组合为修复汇总路径前的 15 项。
- 个人 scope 同批 Windows CI 原包在三库完成 doctor/列表/精确筛选/三表导出及集合一致性，导航与 18 个正文解包检查通过。五个 tgz 在本机 `target/release-review-4de172f/`；四条下载链为 2,667,040 / 3,181,117 / 3,147,303 / 2,739,103 字节，全部低于 15 MB。[本轮证据](evidence/personal-scope-acceptance-4de172f.json)绑定新包与旧基线的 JAR/native 字节一致性。
- 用户表示“目前没有这些审计证据”；本机数据库审计状态已改为 `unverified-no-evidence-available`，不由文档验收或 npm 登录代填通过。
- 同一 2e63e20 CI 的五个原始 tgz 已复制至本机 `target/release-review-2e63e20/`。四条入口＋平台下载链分别为 Windows 2,651,110、macOS Intel 3,162,310、Apple Silicon 3,128,782、Linux 2,720,435 字节，全部低于 15 MB。[精确汇总](evidence/native-validation-2e63e20.json)保留全部包/JAR/native 和 9 个 artifact 的哈希。
- 使用该批 Windows CI 原包重跑三库，doctor/列表/精确过滤/三表导出及集合一致性通过；导航检查和 18 文件解包匹配。此前本机独立构建包证据保留，不混作相同 tgz。
- 新增 `record-native-inputs.cjs` 与两平台构建入口的输入追溯；Windows 固定源码＋已校验 bootstrap 重建与 CI native SHA-256 相同。工具记录加载单元，不冒充完整链接映射；[源码输入证据](evidence/native-inputs-win32-2e63e20.json)与[分发核查](native-distribution-review.md)分别记录已知和缺口。
- 2026-10-27 用户人工确认：“生成的文档验收发现是已经通过了，可以继续执行”。[人工确认](evidence/chm-viewer-human-acceptance-20261027.json)据此关闭文档验收；不补造原先未执行的 agent 步骤，不扩展为安全审计或发布资格通过。
- 2026-10-27：修复提交 `2e63e2017b12fca13bad3d334f7081414d097c65` 的[四平台构建/安装/导航/解包](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40436646789)和[12 组合运行矩阵](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40436646777)全部成功；16 份 CHM 的 4,076 个 HTML/CSS 哈希匹配。
- 2026-10-27：Windows HTML Help 对四平台最终中文夹具完成 32 个交互检查点，标题、目录、中文索引、中英文全文查询各返回 6 页且可打开。千表样例完成封面、目录和第 1,000 个表页（20 列）3 个检查点；其余项目因窗口最小化、恢复失败而保留 pending。[机器记录](evidence/chm-viewer-2e63e20.json)绑定源提交、CI、每份 CHM 和证据文件哈希。
- 最终本机 Windows 包安装下载链为 2,653,138 字节，严格低于 15,000,000 字节。真实三库新导出的 18 个正文文件和六份重编译缓存的 30 个正文文件均匹配；原失败件保留。本轮不将历史 glob/exclude 验证或合成 UI 结果移用到真实数据库原件。
- 2026-10-26 新增失败证据：用户截图和六份本机既有 CHM 的内部流检查确认中文导航编码不一致。既有解包/运行矩阵结果不等于 Windows 完整阅读验收通过。原因与后续标准见[编码修复计划](chm-navigation-encoding-plan.md)。

- 源码对比：原项目 25 个源码文件，公开基线 24 个；差异是脱敏连接示例，原项目独有 TestRender 为本地助手，没有生产功能遗漏。
- 提交 d1ef573 的 [CLI/npm 12 个运行组合](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40274603589)全部成功。
- 同一提交的[四平台 native、实际 npm 安装和解包](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40274603591)全部成功；12 份 CHM 解包后，4,052 个 HTML/CSS 的 SHA-256 与原始文件全部匹配。
- Windows 实际安装证据确认 bundled chmcmd、HHC 优先、显式 HHC 缺失不回退。HHC 优先使用仅定位文件的 doctor 夹具，不代表 HHC6003 已修复。本机新构建脚本和中文目录 npm 安装导出也通过。
- 本地自动化 12 项通过，覆盖真实本地 npm registry 安装、JDBC 测试夹具、Unicode 参数、旧文件拒绝、全操作超时、晚出现的 HHC 错误和原型发布拒绝。夹具不等于真实数据库审计。
- HHC 当前宿主日志证据：HHC6003，即使返回 0 且生成 CHM 也按失败处理。没有注册 DLL、修改系统或提升数据库权限。
- Windows native 为 PE32+ x64、2,375,953 字节，只导入三种系统 DLL。本机与 CI 的二进制 SHA-256 一致；这不能代表所有 native 的完全复现。
- 提交 4826b39 的[共享 JAR 与四平台完整验证](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40276072365)、[12 组合运行矩阵](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40276072386)全部成功；下载后重新计算 tgz 与内含 JAR 哈希，四平台 JAR 完全一致。公开[汇总证据](evidence/native-validation-4826b39.json)绑定该提交。
- 含真实编译器的下载体积与可发布资格分别记录，详见[验证记录](validation-evidence.md)；当前没有正式 RC 体积结论。

## 下一步

1. 按[准确源锁与重建路线](native-source-lock-plan.md)落实对应 RTL/FCL 源、发行版补丁、隔离重建及源码交付，实测最终大小；个人 scope 包与自动汇总已通过。
2. 冻结实际最低系统并验证固定构建输入；现有[矩阵](support-matrix.md)保留已实测环境与二进制下限的区别。
3. 数据库审计当前没有证据，保持未验证；后续具备材料时按[验收说明](manual-acceptance.md)补齐。
4. 上述门槛通过后形成正式候选并核验发布认证/2FA 或 trusted publisher，再进行 beta registry 安装验收；当前不发布 npm。
