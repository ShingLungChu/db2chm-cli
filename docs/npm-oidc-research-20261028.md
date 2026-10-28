# npm Trusted Publishing 第一方核查

核查日期：2026-10-28（Asia/Shanghai）。用途：为当前 `@t20-ai` 五包建立 GitHub Actions OIDC 发布路径提供事实依据。本文仅记录官方文档、公告和 npm CLI 源码；没有创建/删除 npm 信任、权限、包或版本，没有读取账号凭据，也不表示当前账号配置已经完成。

## 1. 用户看到的限制是什么意思

2026-07-08 的预告提出两阶段调整；2026-07-31 的实际公告确认，bypass-2FA granular access token 已不能执行敏感账号、组织和包管理操作，包括维护者、访问权限及 Trusted Publisher 配置。这些操作应交互完成 2FA。该公告同时把禁止此类 token 直接发包的时间表定为 **目标 2027 年 1 月**；截至本次核查，不应把未来限制写成已经全面生效，也不应把公告解释为 GitHub PAT、GitHub App token 或 Actions `GITHUB_TOKEN` 被禁用。[2026-07-08 预告](https://github.blog/changelog/2026-07-08-npm-install-time-security-and-gat-bypass2fa-deprecation/)、[2026-07-31 实施公告](https://github.blog/changelog/2026-07-31-restricting-npm-bypass-2fa-granular-access-tokens/)

## 2. 本项目应采用的精确配置

以下是从现有仓库身份与 `npm-publish.yml` 得到的目标填写值；GitHub 仓库所有者与 npm 组织不是同一个字段：

| npm Trusted Publisher 字段 | 本项目填写值 |
| --- | --- |
| Provider | GitHub Actions |
| Organization or user | `ShingLungChu` |
| Repository | `db2chm-cli` |
| Workflow filename | `npm-publish.yml`，只填文件名 |
| Environment name | `npm-release`，与工作流 job.environment 一致 |
| Allow npm publish | 开启，现有流程使用 direct publish |
| Allow npm dist-tag | 开启，现有恢复流程可能更新 beta/next 标签 |

字段应逐字、大小写一致。以 npm 包 Settings → Trusted publishing → Add trusted publisher 配置；每个 npm 包独立保存关系，不是配置一次组织就自动覆盖五包。[Trusted Publishing 文档](https://docs.npmjs.com/trusted-publishers/)

需分别处理：

- `@t20-ai/db2chm-win32-x64`
- `@t20-ai/db2chm-darwin-x64`
- `@t20-ai/db2chm-darwin-arm64`
- `@t20-ai/db2chm-linux-x64`
- `@t20-ai/db2chm`

## 3. 运行时与权限下限

| 能力 | 第一方规定 | 对本项目的含义 |
| --- | --- | --- |
| npm OIDC publish | npm >=11.5.1、Node >=22.14.0 | 现有 Node 24 适用，但仍需确认 npm 的真实版本 |
| OIDC dist-tag | npm 11.x >=11.21.0，或 npm 12.x >=12.2.0；另需 Allow npm dist-tag | 建议固定 npm 11.21.1，以覆盖当前恢复逻辑；npm 11.19.0 不能直接当作完整 dist-tag OIDC 支持 |
| Runner | GitHub-hosted runner；暂不支持 self-hosted | 现有 `ubuntu-24.04` 合适 |
| 工作流权限 | `id-token: write` | 保留 job/workflow 的该权限；`contents: read` 用于检出源码 |
| CLI 管理 trust | npm >=11.15.0；包写权限；账号 2FA；包须存在 | 网站配置更适合本次逐包操作，不能拿 bypass-2FA token 无交互配置 trust |

依据：[运行时和 runner 要求](https://docs.npmjs.com/trusted-publishers/)、[2026-09-30 dist-tag OIDC 公告](https://github.blog/changelog/2026-09-30-opt-in-dist-tag-permissions-for-npm-trusted-publishing/)、[npm trust 前提与权限](https://docs.npmjs.com/cli/v11/commands/npm-trust/)。

`Allow npm publish` 和 `Allow npm dist-tag` 是独立权限；只有前者不保证标签恢复可用。新配置默认可 stage，直接 publish 必须另外选择。[dist-tag 权限公告](https://github.blog/changelog/2026-09-30-opt-in-dist-tag-permissions-for-npm-trusted-publishing/)、[2026-09-03 多配置公告](https://github.blog/changelog/2026-09-03-multiple-trusted-publishing-configurations-for-npm/)

## 4. 五个新包的首次发布：不能假定 OIDC 可以直接创建信任

`npm trust` 官方前提仍要求包已经存在；网页配置也从具体包的设置页进入。因此对 HTTP 404 的新名字，不能把“写完工作流”当作已经建立了 npm 信任。[npm trust 前提](https://docs.npmjs.com/cli/v11/commands/npm-trust/)

当前确认的常规路径是：账号持有人用交互式登录与 2FA 发布 **真实、已经验收的 tarball**，使包存在；随后逐包创建 Trusted Publisher，后续尚未发布且已验收版本由 OIDC 发布。不能重新发布同一个已公开版本，也不能将“跳过已经存在的版本”当作一次 OIDC 成功。[公开包发布](https://docs.npmjs.com/creating-and-publishing-scoped-public-packages/)、[npm publish 版本唯一性](https://docs.npmjs.com/cli/commands/npm-publish/)

### 4.1 2026-10 的 staged bootstrap 是真实功能，但同版号有冲突

官方 2026-10-02 公告新增了 `npm stage publish` 创建包的能力；可以使用本地 session 或 GAT（含 stage-only）上传真实 tarball，不需要先手工 direct publish。创建后可管理包设置并配置 Trusted Publisher。[新增功能公告](https://github.blog/changelog/2026-10-02-npm-staged-publishing-now-supports-creating-new-packages/)

新包 staged 时，registry 自行创建公开的 `0.0.0-stage` 占位版本；待审核的实际版本和内容仍未公开。这是服务端行为，不是本项目应手工伪造的空发行版。[staged 流程](https://docs.npmjs.com/staged-publishing/)

但 staged 和已发布版本共享 semver 唯一索引。因此，若真实 `0.2.0-beta.1` 已在队列中，**不能直接再由 OIDC publish 同一个版本**。现行 CLI 文档明确说明此限制，并说明更换同版 staged 标签前，须 reject 后再 stage。[npm stage 版本及标签规则](https://docs.npmjs.com/cli/v11/commands/npm-stage/)

本次未找到第一方明确承诺“reject 最后一个 stage 后占位包一定保留，且可立刻同版 direct publish”的完整端到端规则，也没有在真实账户执行这些变更。故不能把 `stage → 配 trust → reject → OIDC 同版 publish` 写成已经验证的傻瓜式必经流程；如果维护者选择该实验路线，必须先明确批准 stage 的永久删除，并独立核查剩余包、信任与目标版本状态，再决定下一步。不能改造假版本或把未批准 stage 当作已发布业务包。

批准 stage 也需要账号持有人交互 2FA，OIDC 不支持 `stage approve`、`stage reject` 等管理命令；这是一条保留人工批准的发布模式。[staged 发布文档](https://docs.npmjs.com/staged-publishing/)、[OIDC 命令范围](https://docs.npmjs.com/trusted-publishers/)

## 5. 不存长期 token 的实现边界

npm v11.21.1 的实现会在 publish 中先尝试 OIDC，失败时仍可能保留传统凭据回退；因此“设置过 token 一定覆盖 OIDC”不是当前实现的普遍规则。为使 CI 的无 token 状态可审查，发布步骤不应注入 `NODE_AUTH_TOKEN`、`NPM_TOKEN`，也不应自行提供 `NPM_ID_TOKEN`。后者会覆盖 GitHub 内置的 request-based 取证路径。不要删掉 GitHub 提供的 `ACTIONS_ID_TOKEN_REQUEST_URL` / `ACTIONS_ID_TOKEN_REQUEST_TOKEN`。[npm v11.21.1 OIDC 实现](https://github.com/npm/cli/blob/v11.21.1/lib/utils/oidc.js)、[publish 调用位置](https://github.com/npm/cli/blob/v11.21.1/lib/commands/publish.js)

OIDC 交换出的 npm 凭据按包处理，写入非持久化 CLI 配置；多包时不能自行把上一个包的临时凭据当作另一个包的通用 token。[OIDC 状态清理与 package-specific exchange](https://github.com/npm/cli/blob/v11.21.1/lib/utils/oidc.js)

`GITHUB_TOKEN` 读取本仓库 Actions artifact，与 npm 发布认证是两件事；本项目保留它用于下载已验收产物，不把它传作 npm registry token。包是公开包时，发布前后的 `npm view` 元数据读取可匿名完成；`npm whoami` 不构成 OIDC 权限验证。[OIDC 命令范围与 whoami 说明](https://docs.npmjs.com/trusted-publishers/)

## 6. Provenance 与制品来源不能混淆

公开 GitHub 仓库通过 OIDC 发布公开 npm 包，会自动生成 provenance，通常不需要额外 `--provenance`；私有仓库、CircleCI 的行为不同。源码 `repository.url` 应与 GitHub 仓库精确匹配。[npm provenance 文档](https://docs.npmjs.com/generating-provenance-statements/)

当前“下载已验收 tarball，再由另一次工作流发布”的模式应继续逐包核对哈希、候选 run/commit、验收 report；自动 attestation 不代替真实数据库与文档验收，也不能把发布工作流的提交号冒充原始应用构建提交。

## 7. 信任配置有 48 小时验证窗口

2026-10-02 起，未验证的新信任在创建 48 小时后失效；第一次实际成功发布才验证该配置并绑定不可变仓库身份。过期后需重建关系；已有有效关系不受另一条过期关系影响。`issue_comment` 与 `pull_request_target` 不能用于该发布，现有 `workflow_dispatch` 是允许事件。[48 小时规则公告](https://github.blog/changelog/2026-10-02-unvalidated-npm-trusted-publishing-configurations-now-expire/)

因此：

- 五包都已经存在并跳过上传的工作流，可以完成制品/标签核对，但不能宣称五个 OIDC publish 已验证。
- 创建信任时应准备好尚未发布、已完成验收的版本，并安排 48 小时内实际发包。
- 仅 `npm whoami`、`npm publish --dry-run`、看到设置表单保存成功，都不能作为真实 OIDC 发包验收。

## 8. 一处官方文档的过时信息

`npm-trust` CLI 页面仍保留“每包仅一个配置”的文字，但 2026-09-03 官方发布公告及现行主文档已明确支持多个配置，主文档当前上限为每包 10 个。本次采用较新的功能公告及主文档，不据过时段落删除已有信任。连接的固定字段不能直接编辑；修改身份时删除旧连接、另建新连接，操作前应核对已有发布者，避免误删其他有效工作流。[较新的多配置公告](https://github.blog/changelog/2026-09-03-multiple-trusted-publishing-configurations-for-npm/)、[主文档管理说明](https://docs.npmjs.com/trusted-publishers/)、[仍含旧段落的 CLI 文档](https://docs.npmjs.com/cli/v11/commands/npm-trust/)

## 9. 本项目建议的交付检查

配置阶段记录五包各自的仓库/工作流/环境/允许动作及保存时间，不能仅标记“组织已开启 OIDC”。仓库侧固定兼容 npm，保留 GitHub-hosted runner、id-token 权限与 `npm-release` 环境；环境的 main 分支限制、必要审核与环境保护由有权限的仓库维护者设置。

发布仍按四个平台包先、入口最后执行；每次仅接受已有候选报告绑定的精确 tarball，registry integrity 不同即停止。部分成功可以只补发缺失的同字节包；保留五包实际 publish 是否发生的记录。最终以真实 OIDC 交换/发布成功、完整 integrity 核对和发布后的四平台安装运行作为证据；没有实际发生的步骤写为待完成。
