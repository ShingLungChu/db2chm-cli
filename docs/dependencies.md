# 依赖、来源与分发清单

更新：2026-10-27。所有 JDBC 二进制和微软 HHC 均未进入本仓库/npm 原型。驱动由调用者按厂商条款显式外置；三库实测范围见[支持矩阵](support-matrix.md)，不扩展到其他组合。

## 应用依赖

| 组件 | 固定版本 | 用途 | 许可 / 处理 |
| --- | --- | --- | --- |
| Java | 目标 8/17/21 | 运行应用 | 用户预装，不分发 Java |
| Node.js | 目标 22/24 | npm 启动器 | 用户预装；JS 运行代码无第三方 npm 依赖 |
| FreeMarker | 2.3.32 | HTML 模板 | Apache-2.0，随业务 JAR |
| Gson | 2.10.1 | 配置/JSON | Apache-2.0，随业务 JAR |
| 应用源码 | 0.2.0-beta.1 | CLI/GUI/适配器 | 当前 npm 元数据 UNLICENSED；未擅自授予开源许可 |

Maven 资源、编译、归档、测试、shade 插件均固定版本；归档时间戳固定。打包输出运行组件 SBOM（CycloneDX 1.6），范围包括上述两个 Maven 运行依赖及已审批 native 清单。构建工具链的完整 SBOM 和完全固定的 bootstrap 系统包仍是 T11 的后续项。

## 五种 JDBC 候选

| 类型 | 本机文件 / 识别版本 | 字节数 | 分发与兼容状态 |
| --- | --- | ---: | --- |
| Oracle | ojdbc7-12.1.0.2.0.jar / 12.1.0.2.0 | 3,698,857 | 外置；Windows/Java 8/19c 的功能验证通过，权限审计独立待补 |
| 达梦 | DmJdbcDriver18-8.1.2.192.jar / 8.1.2.192 | 1,063,595 | 外置；Windows/Java 8/8.1.4.80 功能验证通过，权限审计独立待补 |
| Vastbase | Vastbase-G100-2.14_pg_2026011411.jar / 2.14 | 981,328 | 外置；Windows/Java 8/G100 V2.2 (Build 15) 功能验证通过 |
| MySQL | mysql-connector-java-8.0.17.jar / 8.0.17 | 2,321,813 | 外置；适配器保留，本版尚未纳入最低真实验收 |
| KingbaseES | kingbase8-9.0.0.jar / manifest V009R002C016 | 1,459,652 | 外置；文件名版本不作权威依据；未验证 |

SHA-256：

```text
Oracle   0d34cddb5726232ad4c0e5db731e930c9c75d8f74b9c4aa449799cc43dd3e829
DM       afc60f332c8b79d514c1c19d2b58164c3e42112fea455eae68c9d56716834a97
Vastbase 31e9e5add56fbe65257ab40846b3e982ad6a848d9941b1fa2670a7856e7eeba2
MySQL    073c395ec325ac68dcda6f5534167ba40de5b0fe4b459f7636ac6c44e4ace5a7
Kingbase 1250ce0c47cb68da8385ed6ee62ea79258a577473c463d29f5c8dbe045321037
```

使用 --driver-jar 或配置 driverJar 显式加载；驱动类必须匹配类型。doctor 的类加载检查本身不能证明驱动/服务器兼容性；上述三库另有真实功能证据，其余组合仍未验证。驱动不随包分发，不自动下载；使用者负责取得合法驱动与授权。

GUI 的旧文件名兼容只查业务 JAR 同目录下 lib/；不扫描当前工作目录。CLI 推荐始终显式指定。

## CHM 编译器

### Windows HHC

- 正确角色：hhc.exe 编译；hh.exe 查看/解包。
- 当前项目的 tools/hhc 文件确实存在，宿主普通权限下能启动；出现过的重定位错误受执行环境影响。
- 当前实际阻断是 HHC6003：Itircl.dll 注册/匹配失败。混入现代 Windows 10.0.26100.5074 的 DLL 后仍未解决。未执行系统 DLL 注册或修改注册表。
- HHC 可返回 0 并留下 ITSF 文件，同时报告 HHC6003。CLI 会读取全部输出流的错误编号并拒绝假成功。
- 微软 EULA 中显式允许再分发的文件为 hhupd.exe；尚未建立 hhc.exe 及其完整运行文件集的分发依据。不得将现有文件目录直接装入正式 npm 包。
- 用户已确认 Windows 交付：优先已安装 HHC，缺席时使用包内 chmcmd。微软文件不进入 npm；HHC 的再分发问题不再作为默认安装链前提。已有 HHC 运行失败时不会自动切换。

依据：[Microsoft HTML Help EULA](https://learn.microsoft.com/en-us/previous-versions/windows/desktop/htmlhelp/html-help-end-user-license-agreement)。

### 四平台 chmcmd

源码固定为 FPCSource 提交 de69d52048cf52c72ebfeef61617c04c9bc2005c。macOS/Linux bootstrap 要求 FPC 3.2.2；Windows 使用官方 FPC 3.3.1 x64 交叉编译包。该提交的 chmcmd.lpr 使用新版本的 rtlString 名称，仓库记录的 chmcmd-fpc322.patch 为 3.2.2 补充等价 string 别名；Windows 3.3.1 不启用该条件分支。补丁与修改后源码归档随 native artifact 保存。

Windows 构建入口为 scripts/build-native-windows.ps1；验证官方 ZIP SHA-256 为 2206d8d866360844e02a2920c4827bd139ec4a8713b14be2afacc04ee3e8d66e，同时验证实际 bootstrap 可执行文件哈希。官方 snapshot 地址可能变化，内容不匹配时停止，不能自动接受新版本。构建工具约 79 MB 仅用于开发/CI，不进入安装包。PE 检查记录 x64 架构、导入 DLL 和二进制哈希。

入口源码许可为 GNU Library GPL v2 或更新版本；完整分发还需核对链接的 RTL/FCL、例外条款与对应 bootstrap 源码来源。因此构建 manifest 默认 redistributionApproved:false，正式打包必须有已审核的精确文件/哈希记录。

来源：[固定源码](https://github.com/fpc/FPCSource/tree/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm)。

四平台 native 已从固定源码编译，通过完整 Java 过滤导出、1000×20、私有 npm 安装和 Windows 解包；同一份共享 JAR 已下载复核。Windows 中文夹具交互通过，用户确认生成文档验收通过。最新 hosted runner 不代表全部旧系统，Linux arm64/musl 未列入首期。

Windows bootstrap 的完整版本为 `3.3.1-21298-g814ece0205`，可追溯到 `814ece0205a6b9c2c6dad74c8069ebb2ae50d1c7`。四平台 PPU/源码输入记录已通过 CI，native 与前次验收基线字节一致。编译输入清单不能代替库来源/许可/完整链接清单，[分发核查](native-distribution-review.md)和[源锁实施路线](native-source-lock-plan.md)仍有未闭合项，`redistributionApproved` 保持 false。
