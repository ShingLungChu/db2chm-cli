# npm Trusted Publishing（OIDC）配置操作手册

核对日期：**2026-10-28**。本手册按当前项目 `@t20-ai/db2chm` 的五包结构填写。Windows 操作使用 CMD，无需 PowerShell 脚本，也无需创建长期 npm 发布 token。

## 一、先看当前已完成什么

| 配置项 | 本次实际状态 |
| --- | --- |
| 目标 npm 组织 | `t20-ai`，登录账号仍是 `jackiechen1994` |
| GitHub 仓库 | `ShingLungChu/db2chm-cli` |
| 发布工作流 | `.github/workflows/npm-publish.yml` |
| GitHub 发布环境 | 已创建 `npm-release`，已限制只允许 `main` 分支 |
| CI 运行环境 | 工作流已配置 Node 24、固定 npm 11.21.1、GitHub 托管 runner |
| 长期 npm 凭据 | 发布工作流不注入 npm token，使用隔离的空 npm 配置 |
| 当前 npm 包 | 2026-10-28 查询五包的公开 registry 均返回 404；如已有私有包，请登录 npm 网站另外确认 |
| 五包 Trusted Publisher | 尚未在 npm 端确认配置；GitHub 环境已创建不等于 npm 已授权 |
| 真正的 OIDC 发布验收 | 尚未完成，需要新版本实际上传成功来证明 |

本机可执行 `release.cmd oidc-status` 重新读取状态。联网需要已有 GitHub 仓库访问身份；结果仅保存公开配置摘要，不包含凭据。

### 这条 npm 提示意味着什么

2026-07-31 官方公告已确认：bypass-2FA 的 npm granular token 不能再跳过敏感账号、组织和包管理操作的交互验证；直接发包权限的进一步收紧，官方目标时间为 **2027 年 1 月**。这是针对 npm bypass-2FA token 的变更，不代表普通账号交互发布被禁止，也不影响 GitHub 自带的 `GITHUB_TOKEN`。

本项目的目标链路是：

```text
你在 GitHub 手动启动发布
  → GitHub 为 npm-publish.yml 签发短期身份
  → npm 核对五个包各自保存的 Trusted Publisher
  → npm CLI 自动交换短期发布凭据
  → 上传已经验收的原始 .tgz
```

日常发布不再依赖本机 `npm login` 会话，也不在 GitHub Secrets 中保存长期 npm 写入 token。首次创建包、维护组织权限、保存 Trusted Publisher 等账号操作，仍由有权限的账号交互完成。

## 二、首次新包有一个前提，请先选择对应路径

**npm 要求包名已经存在，才能为它配置 Trusted Publisher。** 不能对不存在的包只填一个 GitHub 仓库地址就完成首次 OIDC 发布。

| 你当前的情况 | 接下来怎么做 |
| --- | --- |
| 五个包都还没有创建 | 先按第三节交互发布真实、已验收的首次版本，再为后续版本配置 OIDC |
| 五个包都已经存在，且你有写权限 | 跳过第三节，先准备一个尚未发布的已验收候选，再配置 npm 信任 |
| 只创建了一部分包 | 先完成其余包的真实首次创建，再为五包逐一配置 |
| 包是私有的，公开查询返回 404 | 登录 npm 网站确认包、访问权限和目标可见性；不能仅凭 404 判断包名不存在 |

### 当前 `0.2.0-beta.1` 的实际边界

如果你先手动发布了五个 `0.2.0-beta.1`，再让 GitHub 重跑同一批包，脚本会跳过已经存在的相同文件。**没有实际上传，就没有验证 OIDC 发布权限。** 本项目现已明确区分这种重跑与真正的 OIDC 上传。

因此，首次手动创建完成后，需要准备**下一次真正要发布、尚未发布的版本**来验收 OIDC。当前固定 beta 基线仅覆盖 `0.2.0-beta.1`；新版本必须正常更新版本/验收基线并重新验收，不要直接改旧 `.tgz` 内的版本，也不要绕过原包校验。

新 Trusted Publisher 必须在创建后 **2 天内完成第一次真正成功的 publish**。应先准备好下一版候选，再保存 npm 信任配置，避免保存后一直没有可发布版本而过期。

## 三、如果五个包尚不存在：完成一次真实首发

这一节只用于首次建立包名；使用短期登录会话和人工二次验证，不要求创建长期 token。

### 步骤 1：打开 CMD 并进入项目

按 `Win + R`，输入 `cmd`，回车。执行：

```cmd
cd /d "D:\workspace\allFlow\db2chm-cli-publish"
```

```cmd
release.cmd status
```

确认 scope 为 `@t20-ai`，发布账号为 `jackiechen1994`。

### 步骤 2：选择本次已验收的五包目录

当前已下载的组织 scope 候选编号为 `40662896805`。执行：

```cmd
release.cmd check "D:\workspace\allFlow\db2chm-cli-publish\target\releases\40662896805\accepted-npm-release"
```

应显示“验收通过”。如果目录不存在或配置已变化，请按[发布指南](发布指南.md)重新构建、验收和下载，不要替换成旧个人 scope 的产物。

### 步骤 3：登录 npm，确认组织权限

```cmd
"C:\Program Files\nodejs\npm.cmd" login --auth-type=web --registry=https://registry.npmjs.org
```

完成浏览器登录后执行：

```cmd
"C:\Program Files\nodejs\npm.cmd" whoami --registry=https://registry.npmjs.org
```

应输出 `jackiechen1994`。请由 `t20-ai` 的组织所有者确认该账号有创建这些包以及写入版本的权限。

### 步骤 4：按顺序交互发布首次真实版本

每次只执行一条，等成功后再执行下一条：

```cmd
release.cmd publish win32-x64
```

```cmd
release.cmd publish darwin-x64
```

```cmd
release.cmd publish darwin-arm64
```

```cmd
release.cmd publish linux-x64
```

```cmd
release.cmd publish entry
```

出现 npm 浏览器验证时完成当次验证，保持 CMD 窗口打开，等待成功回执。若返回权限错误或 PUT 404，请停在该步，核对账号、组织写权限和登录会话；换成 OIDC 不会自动补足首次建包权限。

然后执行：

```cmd
release.cmd verify-registry
```

五包都确认存在后，才能为五包绑定 Trusted Publisher。此首发本身不计作 OIDC 实发验收。

> npm 目前还提供 staged 首发流程，由 registry 自动创建占位包；但 staged 和 published 共享版本唯一性，不能把同一版本先 stage，再直接用 OIDC publish。我们未验证“reject 后同版直接发布”的完整 registry 链路，因此本手册不将它作为必经操作，也不创建伪造的空首发版本。

## 四、核对 GitHub 端配置

本次已通过 API 创建 `npm-release`，并确认只允许 `main`。无需再次手工创建。

### 步骤 5：查看发布环境

打开：

<https://github.com/ShingLungChu/db2chm-cli/settings/environments>

点击 `npm-release`，检查部署分支规则仅包含 `main`。

如果在其他仓库重新实施，可以先执行：

```cmd
release.cmd oidc-setup-github
```

此工具仅针对当前配置的 `ShingLungChu/db2chm-cli`。它只创建缺失的环境；已有环境若包含不同规则，会保留原规则并提示人工处理，不会清除审核人。

### 步骤 6：确认没有给发布工作流配置 npm token

本项目发布工作流不引用 `secrets.NPM_TOKEN`，不要求添加 `NODE_AUTH_TOKEN`、`NPM_TOKEN` 或人工填写的 `NPM_ID_TOKEN`。

保留 `id-token: write`，由 GitHub 自动提供 OIDC 取证环境。不要把本机 `.npmrc` 上传到仓库或当作发布产物。

现有工作流会自动创建空的 npm user/global 配置及独立缓存，检查运行时版本和工作流身份，再执行发布。此前其他用途的 Secrets 不会被本工具自动删除；验证 OIDC 正常后，再由维护者决定哪些旧 npm 自动化凭据可以撤销。

## 五、为 npm 上的五个包逐一绑定 Trusted Publisher

**执行本节前：五个包必须已经存在，并且下一次尚未发布的已验收版本应已准备好。**

### 步骤 7：保存这一张填写表

五个包都使用相同内容：

| npm 表单字段 | 必须填写/选择的值 |
| --- | --- |
| Provider / Select your publisher | `GitHub Actions` |
| Organization or user | `ShingLungChu` |
| Repository | `db2chm-cli` |
| Workflow filename | `npm-publish.yml` |
| Environment name | `npm-release` |
| Allow npm publish | 勾选，允许当前工作流直接发布 |
| Allow npm dist-tag | 勾选，允许恢复流程修复 beta/next 标签 |

“Organization or user” 填的是 GitHub 仓库所有者 `ShingLungChu`。npm 包的 scope 是 `t20-ai`，它对应另一项信息。

Workflow filename 仅填 `npm-publish.yml`，不填写 `.github/workflows/` 前缀，也不填写工作流页面显示的标题。大小写、扩展名、环境名要与表中一致。

### 步骤 8：配置 Windows 包

登录 npm 网站，打开：

<https://www.npmjs.com/package/@t20-ai/db2chm-win32-x64>

依次进入 **Settings → Trusted publishing / Trusted Publisher → Add trusted publisher → GitHub Actions**。如果是第一次添加，可能直接显示 Select your publisher。

按上表填写，勾选直接 publish 和 dist-tag 权限，保存并完成账号二次验证。

如果只保留默认的 stage 权限，当前直接发布工作流不能工作。publish 与 dist-tag 是独立权限，允许 publish 不会自动允许标签修改。

### 步骤 9：对其余四个包重复相同配置

依次打开，每个都填写同一张表并保存：

| 包 | npm 页面 |
| --- | --- |
| macOS Intel | <https://www.npmjs.com/package/@t20-ai/db2chm-darwin-x64> |
| macOS Apple Silicon | <https://www.npmjs.com/package/@t20-ai/db2chm-darwin-arm64> |
| Linux x64 | <https://www.npmjs.com/package/@t20-ai/db2chm-linux-x64> |
| 统一入口 | <https://www.npmjs.com/package/@t20-ai/db2chm> |

配置是按包保存的；仅配置统一入口不会自动授权四个平台包。页面显示保存成功，只表示配置记录存在，npm 会在真正发布时核对身份。

若包已有其他有效 Trusted Publisher，不要为添加本项目而删除它。当前 npm 支持每包多个配置；只有本项目这条配置过期或字段错误时，才处理对应记录。现有连接字段不能原地编辑，需按 npm 提示删除那条记录后重新建立。

## 六、先做不会上传的 CI 预检

### 步骤 10：执行配置预检

回到 CMD，执行：

```cmd
release.cmd oidc-preflight 40662896805
```

也可以在已有候选选择记录时执行 `release.cmd oidc-preflight`。

这个命令启动 GitHub 的发布工作流，但固定 `dry_run=true`，不会执行 npm publish。它使用 GitHub `main` 上已经提交的配置，不会把本机未提交的 `package.json` 改动打包上传。

打开：

<https://github.com/ShingLungChu/db2chm-cli/actions/workflows/npm-publish.yml>

点击最新的 **Publish accepted npm artifacts** 运行，确认下列步骤成功：

- Isolate npm configuration without long-lived credentials
- Install pinned npm with OIDC publish and dist-tag support
- Verify token-free OIDC runtime
- Verify gates, checksums and complete installation size
- Record configuration-only validation

此处的绿色只证明工作流、运行时和验收包检查通过。它没有尝试上传，不证明五个 npm Trusted Publisher 已生效，也不会完成 2 天内首次发布的验证。

这一节不依赖包已经存在；因此可以先完成预检，再处理首次建包和 npm 网页授权。

## 七、真正通过 OIDC 发布一个尚未发布的已验收版本

### 步骤 11：从 GitHub 手动启动真实发布

确认本次候选已经完整验收、版本尚未上传、五个信任配置尚未过期，然后打开：

<https://github.com/ShingLungChu/db2chm-cli/actions/workflows/npm-publish.yml>

点击 **Run workflow**，填写：

| 项目 | 选择内容 |
| --- | --- |
| Branch | `main` |
| artifact_run_id | **本次新版本**产生 accepted-npm-release 的成功构建编号 |
| dist_tag | `beta` |
| dry_run | **取消勾选**，才会真正发布 |

确认后点击 Run workflow。

本机不需要再执行 `npm login`。不要把 npm token 粘贴进 GitHub，也不要用旧 `0.2.0-beta.1` 的构建编号冒充新版本候选。

工作流会下载同一批已验收原件，先校验，再按四个平台包、统一入口的顺序发布；并发运行会排队，避免两次发布互相干扰。

### 步骤 12：确认真正使用了 OIDC

除了工作流成功，还要打开该次运行的 Artifacts，下载 `npm-oidc-publish-result`，查看 `publish-result.json`。

五包首次完整 OIDC 验收应满足：

```json
{
  "authenticationMode": "oidc-only",
  "actualPublishCount": 5,
  "allFivePackagesExercisedOidc": true
}
```

每个包的 action 应为 `published-via-oidc-job`。报告还记录包版本、完整 integrity 和原包源提交。

如果全部版本已经存在，工作流会给出 `OIDC_NOT_EXERCISED`，明确说明这次没有验证发布权限。部分恢复时，已存在的包会标为 `already-present-same-integrity`；这只能与此前真正上传的记录结合核验，不能单独宣称本次五包都完成了 OIDC 验证。

对于本公开仓库、公开 npm 包，npm 会生成 provenance 来源证明。它不替代原始 tarball 摘要、候选验收和实际数据库/CHM 验证。

### 步骤 13：执行发布后安装验收

按[发布指南](发布指南.md)选择本次候选目录，再执行：

```cmd
release.cmd verify-registry
```

```cmd
release.cmd test-registry
```

确认五个 registry integrity、beta 标签以及四平台真实下载/CHM 导出/Windows 解包通过，再回写发布状态。

OIDC 验证成功后，可由维护者进一步把各包 Publishing access 设置为 **Require two-factor authentication and disallow tokens**。按官方迁移顺序先验证 OIDC，再收紧传统 token 访问；这项设置不禁用已授权的 OIDC 发布。本次工具不会自动撤销账号凭据或修改其他发布渠道。

## 八、常见失败如何处理

| 错误/现象 | 检查内容 |
| --- | --- |
| npm 包页面 404 | 先确认包已创建及账号是否可访问；未建包不能配置 Trusted Publisher |
| npm 保存时要求 2FA | 正常的管理操作要求；由账号持有人完成，不需要创建 bypass token |
| ENEEDAUTH / OIDC exchange 404 | 核对五包是否均已绑定；owner、repo、文件名、环境名必须精确匹配；确认连接未过期 |
| stage 可以，npm publish 不可以 | 检查是否勾选 Allow npm publish |
| 上传成功，修复标签失败 | 勾选独立的 Allow npm dist-tag；CI 固定 npm 11.21.1，旧 11.19 不支持 OIDC 标签修改 |
| Job 因分支/环境规则跳过 | Branch 选择 main，环境仅允许 main；不要选择 tag 或其他分支 |
| 全是 Already verified | 版本已经上传；不能用本次重跑证明 OIDC。按正常版本流程准备新的候选 |
| OIDC_NOT_EXERCISED | 没有发生任何真正 publish；报告未将它计为配置成功 |
| 新配置超过两天仍未首发 | 删除本项目这条过期记录，重新建立并及时实发；不要删除其他有效发布者 |
| 本机 npm whoami 正确 | 只表示本机登录身份，不能验证 GitHub OIDC 信任 |
| 自建 runner 无法使用 | 本项目使用 GitHub-hosted ubuntu-24.04；当前 npm 不支持 self-hosted runner 的这条路径 |

## 九、官方来源与项目配置位置

- [npm Trusted Publishing 官方文档](https://docs.npmjs.com/trusted-publishers/)
- [npm trust 命令及首次配置前提](https://docs.npmjs.com/cli/v11/commands/npm-trust/)
- [2026-07-31 bypass-2FA token 限制公告](https://github.blog/changelog/2026-07-31-restricting-npm-bypass-2fa-granular-access-tokens/)
- [本次第一方资料核查](docs/npm-oidc-research-20261028.md)
- [配置参数](release/trusted-publisher.json)
- [GitHub 发布工作流](.github/workflows/npm-publish.yml)
- [原有构建、下载和手动发布指南](发布指南.md)

OIDC 配置完成不会改变本版已披露的 beta 范围、数据库审计状态或最低系统支持范围。
