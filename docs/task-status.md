# npm 发布任务状态

更新：2026-10-26。源码实施仓库为本仓库；原始参考目录不覆盖迁移。本表按实现、文档、实际验证及剩余依赖分别记录，原型不称为 RC。

## 范围决策

- npm 组织 @nstc，发布账号 jackiechen1994；本机尚未登录 npm，组织写入权限待核验。
- 统一入口 @nstc/db2chm，四个平台后缀 win32-x64、darwin-x64、darwin-arm64、linux-x64。
- 预装 Java，整个安装下载链严格小于 15,000,000 字节。
- Windows 已确定优先已安装 HHC，缺席时使用包内 chmcmd.exe；不分发微软文件。已找到的 HHC 报错保留失败，显式 HHC 不回退。
- 用户更新：本版环境优先 Oracle、达梦、Vastbase；最低真实验收为 Oracle 和达梦。MySQL/Kingbase 适配器保留，未验证组合不作正式支持承诺。
- 本机三库配置已完成；Windows 真实元数据读取、筛选和 CHM 生成已实测，原始证据仅存本机。已发现中文 TOC 乱码，当前完整阅读功能未通过。

## 任务交付

| 任务 | 状态 | 实现与证据 / 余项 |
| --- | --- | --- |
| T00 | 部分完成 | 源码差异、包名、字节口径已冻结；Node/Java 矩阵实跑通过；OS/libc 下限与真实数据库版本待定 |
| T01 | 方案与接入已交付 | 用户确认 HHC 优先、包内 chmcmd 兜底；Windows 实际 npm 安装导出和选择规则通过；不再分发微软编译器；最终 native 审查归 T02/G0 |
| T02 | 部分完成 | 固定源码、补丁、Windows bootstrap ZIP/EXE 哈希、四平台构建、源码/许可/依赖报告已交付；最低系统、完整链接组件分发和工具链复现待收口 |
| T03 | 部分完成 | 五驱动版本/文件哈希和显式加载已交付；外置；真实兼容与厂商分发权不作已验证声明 |
| T04 | 已交付 | CLI 契约、JSON/退出码、凭据与严格参数处理；12 个平台/运行时组合通过私有安装验证 |
| T05 | 部分完成 | 固定查询、绑定、URL 能力许可列表、只读要求、超时、脱敏已实现并有夹具测试；三库环境已就绪，账号权限确认和真实 SQL 审计仍待完成 |
| T06 | 已交付 | list/export 共用 glob，Unicode/大小写/包含排除/排序/无匹配有测试；真实环境复核属于 T09 |
| T07 | 重新打开，导航缺陷 | 编译与正文解包检查通过；六份真实样例内部导航均为 UTF-8/CP936 不一致，Windows TOC 中文乱码；按编码修复计划处理 |
| T08 | 实现与私有验收完成 | 四平台包与统一入口安装矩阵、实际 bundled native 安装已通过，下载量均低于 15 MB；最终候选仍需 G0–G5 放行 |
| T09 | 部分完成，G4 阻断 | Oracle/DM/Vastbase 的 Windows 真实读取、过滤及 CHM 生成通过；TOC 中文显示已失败，HHK/标题/全文搜索需补齐验收。权限与 SQL 审计仍待完成 |
| T10 | 已交付 | 独立 skills/db2chm/SKILL.md、README、CLI/安全/依赖/发布文档；skill 校验通过，离线命令示例已执行 |
| T11 | 部分完成 | 固定 Maven 插件/时间戳、runtime SBOM、哈希/体积/发布拒绝门槛、受控同产物发布；四平台共享重复构建一致的同一 JAR 已通过。native 完全复现、候选汇总和 npm 身份待收口 |
| T12 | 未开始 | 没有已验收 RC；全部前置门槛收口后形成 beta |
| T13 | 未开始 | 没有发布 npm；latest 未修改 |

## 已有实际证据

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

1. 按[编码修复计划](chm-navigation-encoding-plan.md)完成最小中文样例、编码边界修复和四平台回归。
2. 按[人工验收说明](manual-acceptance.md)完成 Windows Viewer 目录/索引/全文搜索互动验收和真实只读审计；三库的元数据读取与基础生成验证已完成。
3. 收口 native 对应源码、链接组件与复现证据，冻结实际 OS/驱动版本范围。
4. 形成四平台候选、检查全部证据及 npm 权限，再进入 beta/registry 验收。
