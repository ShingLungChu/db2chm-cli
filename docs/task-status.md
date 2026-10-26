# npm 发布任务状态

更新：2026-10-26。源码实施仓库为本仓库；原始参考目录不覆盖迁移。本表按实现、文档、实际验证及剩余依赖分别记录，原型不称为 RC。

## 范围决策

- npm 组织 @nstc，发布账号 jackiechen1994；本机尚未登录 npm，组织写入权限待核验。
- 统一入口 @nstc/db2chm，四个平台后缀 win32-x64、darwin-x64、darwin-arm64、linux-x64。
- 预装 Java，整个安装下载链严格小于 15,000,000 字节。
- 用户更新：本版环境优先 Oracle、达梦、Vastbase；最低真实验收为 Oracle 和达梦。MySQL/Kingbase 适配器保留，未验证组合不作正式支持承诺。
- 已创建本机 .local/databases/oracle.json、dm.json、vastbase.json、versions.json；等待填写，Git/npm 均排除。

## 任务交付

| 任务 | 状态 | 实现与证据 / 余项 |
| --- | --- | --- |
| T00 | 部分完成 | 源码差异、包名、字节口径已冻结；Node/Java 矩阵实跑通过；OS/libc 下限与真实数据库版本待定 |
| T01 | 阻断 | HHC 能启动但有 HHC6003，不能视为完整成功；同源运行文件集和再分发依据未解决 |
| T02 | 部分完成 | 固定 FPC 源码+记录补丁，三种原生架构构建与完整应用样例通过；最低系统与分发审批仍待收口 |
| T03 | 部分完成 | 五驱动版本/文件哈希和显式加载已交付；外置；真实兼容与厂商分发权不作已验证声明 |
| T04 | 已交付 | CLI 契约、JSON/退出码、凭据与严格参数处理；12 个平台/运行时组合通过私有安装验证 |
| T05 | 部分完成 | 固定查询、绑定、URL 能力许可列表、只读要求、超时、脱敏已实现并有夹具测试；Oracle/DM 真实审计待配置 |
| T06 | 已交付 | list/export 共用 glob，Unicode/大小写/包含排除/排序/无匹配有测试；真实环境复核属于 T09 |
| T07 | 部分完成 | 三架构真实 chmcmd 接入、HHC 错误拒绝、事务输出、超时/旧文件/长日志错误检查已实现；Windows 完整 HHC 仍阻断 |
| T08 | 部分完成 | 四平台私有 tarball+入口安装全矩阵通过，中文参数/安装目录修复；含真实 native 的三个私有原型正在 CI 验证；Windows payload 未就绪 |
| T09 | 待真实环境 | 用户将填写 Oracle/DM/Vastbase 配置；Oracle/DM 为最低门槛。Windows Viewer 目录/索引/全文搜索互动验收未完成 |
| T10 | 已交付 | 独立 skills/db2chm/SKILL.md、README、CLI/安全/依赖/发布文档；skill 校验通过，离线命令示例已执行 |
| T11 | 部分完成 | 固定 Maven 插件与时间戳、runtime SBOM、校验/体积/发布拒绝门槛、受控同产物发布流程；bootstrap 完全固定、候选汇总与 npm 身份待接通 |
| T12 | 未开始 | 没有已验收 RC；全部前置门槛收口后形成 beta |
| T13 | 未开始 | 没有发布 npm；latest 未修改 |

## 已有实际证据

- 源码对比：原项目 25 个源码文件，公开基线 24 个；差异是脱敏连接示例，原项目独有 TestRender 为本地助手，没有生产功能遗漏。
- CLI/npm 矩阵通过：[run 40265430362](https://github.com/ShingLungChu/db2chm-cli/actions/runs/40265430362)，提交 c5522ab。
- 三种固定源码 native 构建和 Java 小样例/1000×20 均通过；同一 run 的 Windows 解包曾因 hh.exe 将参数引号作为字面量而失败。该原因已用本机对照实验证实，修复后正在重新验收。
- 本地下载 macOS arm64 实际生成 CHM，小样例 5 个 HTML/CSS 与规模样例 1003 个 HTML/CSS 的 SHA-256 全部与 Windows 解包内容一致。
- 本地自动化 12 项通过，覆盖真实本地 npm registry 安装、JDBC 测试夹具、Unicode 参数、旧文件拒绝、全操作超时、晚出现的 HHC 错误和原型发布拒绝。夹具不等于真实数据库审计。
- HHC 当前宿主日志证据：HHC6003，即使返回 0 且生成 CHM 也按失败处理。没有注册 DLL、修改系统或提升数据库权限。
- 当前体积报告在 target/npm/；编译器缺席的私有原型约 1.94 MB。含真实 native 的私有 tgz 将单独报告；无正式 RC 体积结论。

## 下一步

1. 获取最新 native 私有 tgz安装与九份 CHM 的 Windows 解包结果，修复实际失败。
2. 用户填好 .local 配置后运行真实 Oracle/DM 验证；Vastbase 按本版计划补测。
3. 收口 Windows HHC 的完整运行和分发方案；冻结实际 OS/驱动版本范围。
4. 形成四平台候选、检查全部证据及 npm 权限，再进入 beta/registry 验收。
