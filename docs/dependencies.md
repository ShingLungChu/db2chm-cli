# 依赖、来源与分发清单

更新：2026-10-26。所有 JDBC 二进制和微软 HHC 均未进入本仓库/npm 原型。原始文件位于调用者本机，下面的哈希用于识别候选，不能代替许可或真实兼容验收。

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
| Oracle | ojdbc7-12.1.0.2.0.jar / 12.1.0.2.0 | 3,698,857 | 外置；本版必须真实验收 |
| 达梦 | DmJdbcDriver18-8.1.2.192.jar / 8.1.2.192 | 1,063,595 | 外置；本版必须真实验收 |
| Vastbase | Vastbase-G100-2.14_pg_2026011411.jar | 981,328 | 外置；本版计划环境，待版本核验 |
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

使用 --driver-jar 或配置 driverJar 显式加载；驱动类必须匹配类型。doctor 能检查 JAR 可读性、对应类和加载错误，不能证明某驱动/服务器版本已受支持。Java 最低版本还需结合所选 JAR 实测；本次没有将候选版本称为已验证支持版本。

GUI 的旧文件名兼容只查业务 JAR 同目录下 lib/；不扫描当前工作目录。CLI 推荐始终显式指定。

## CHM 编译器

### Windows HHC

- 正确角色：hhc.exe 编译；hh.exe 查看/解包。
- 当前项目的 tools/hhc 文件确实存在，宿主普通权限下能启动；出现过的重定位错误受执行环境影响。
- 当前实际阻断是 HHC6003：Itircl.dll 注册/匹配失败。混入现代 Windows 10.0.26100.5074 的 DLL 后仍未解决。未执行系统 DLL 注册或修改注册表。
- HHC 可返回 0 并留下 ITSF 文件，同时报告 HHC6003。CLI 会读取全部输出流的错误编号并拒绝假成功。
- 微软 EULA 中显式允许再分发的文件为 hhupd.exe；尚未建立 hhc.exe 及其完整运行文件集的分发依据。不得将现有文件目录直接装入正式 npm 包。
- 外置已安装 Workshop 可供验证；正式简易安装目标仍需要满足运行/许可门槛，或冻结经过验证的开源 Windows 备选。当前尚未把外置前提算作 T01 完成。

依据：[Microsoft HTML Help EULA](https://learn.microsoft.com/en-us/previous-versions/windows/desktop/htmlhelp/html-help-end-user-license-agreement)。

### macOS / Linux chmcmd

源码固定为 FPCSource 提交 de69d52048cf52c72ebfeef61617c04c9bc2005c，bootstrap 要求 FPC 3.2.2。该提交的 chmcmd.lpr 使用新版本的 rtlString 名称，仓库记录的 chmcmd-fpc322.patch 为 3.2.2 补充等价 string 别名。补丁与修改后源码归档随 native artifact 保存。

入口源码许可为 GNU Library GPL v2 或更新版本；完整分发还需核对链接的 RTL/FCL、例外条款与对应 bootstrap 源码来源。因此构建 manifest 默认 redistributionApproved:false，正式打包必须有已审核的精确文件/哈希记录。

来源：[固定源码](https://github.com/fpc/FPCSource/tree/de69d52048cf52c72ebfeef61617c04c9bc2005c/packages/chm)。

三种 native runner 已从固定源码编译，并运行完整 Java 过滤导出和 1000×20 样例。Windows 自动解包和 Viewer 交互验收分别记录在任务状态中。最新 hosted runner 通过不代表旧版 macOS/glibc 已通过；Linux arm64/musl 未列入首期。
