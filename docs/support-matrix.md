# 当前验证范围与安装前提

更新：2026-10-27。当前个人 scope 包的构建/安装/实际三库验证绑定提交 `4de172f8d950fa9f10034b885859aebbae9c9512`；[本轮验收](evidence/personal-scope-acceptance-4de172f.json)记录精确结果。旧 @nstc 的 2e63e20 记录保留。两批的业务 JAR 和四个平台 native 均逐字节一致。尚未形成正式 RC 或 npm registry 版本，已实测环境与最低要求线索分别记录。

## 平台与运行时

| 平台包 | 实测系统/runner | Node / Java | 原生依赖线索 | 当前边界 |
| --- | --- | --- | --- | --- |
| `@jackiechen1994/db2chm-win32-x64` | Windows Server 2025 CI；本机 Windows 11，内核 10.0.26200 | Node 22 / Java 8；Node 24 / Java 17、21；本机另有 Node 24 / Java 8 | PE32+ x64；kernel32、oleaut32、user32 系统 DLL | 更老 Windows 未实测；预装 Java，不要求 FPC/WSL/Workshop |
| `@jackiechen1994/db2chm-darwin-x64` | macOS 15.7.9 Intel | 同上三组 CI 运行时 | libSystem、libiconv；Mach-O 声明 10.8 | 10.8 是二进制字段，不是产品最低系统承诺；还受 Node/Java 版本约束 |
| `@jackiechen1994/db2chm-darwin-arm64` | macOS 26.6.2 Apple Silicon | 同上；Java 8 使用 Azul Zulu | libSystem、libiconv；Mach-O minos 11.0 | macOS 11 到当前实测版本之间未补齐真实运行验证 |
| `@jackiechen1994/db2chm-linux-x64` | Ubuntu 24.04 x64 | 同上三组 CI 运行时 | ELF 动态链接 libc；最高版本需求 GLIBC_2.34 | glibc 2.34 是该 native 的符号需求线索，尚未验证全部 glibc 2.34 发行版；musl/Alpine、arm64 未列入本版 |

Node 22/24、Java 8/17/21 为本轮验证组合，不表示这些运行时之间所有版本都逐一测试。Node 和 Java 由用户预装；JDBC 驱动显式外置。四个平台的实际私有安装/导出及同一份 JAR 已验证。

## 真实数据库组合

| 数据库 | 实测服务器 | 驱动 | 本机组合与结果 |
| --- | --- | --- | --- |
| Oracle | 19c Enterprise Edition，19.0.0.0.0 | ojdbc7 12.1.0.2.0 | Windows x64 / Java 8；CI 原始 Windows tgz 的 doctor、列表、精确过滤、三表 CHM 导出通过 |
| 达梦 | 8.1.4.80 | 8.1.2.192 | 同上，通过 |
| Vastbase | G100 V2.2 (Build 15) | 2.14 | 同上，通过 |
| MySQL | 未提供真实环境 | 候选 8.0.17 | 保留适配器；合成夹具不能作为真实 MySQL 验收 |
| KingbaseES | 未提供真实环境 | 候选 V009R002C016 | 保留适配器；不作真实支持承诺 |

其他操作系统/Java/驱动/服务器组合未由这些记录自动获得真实数据库兼容结论。三库文档功能已由用户人工确认通过；账号最小权限、服务端实际 SQL 和对象/数据/授权前后审计仍待独立补齐。

## 当前下载体积

统一入口选择同批 Windows runner 的 2,481 字节入口包，各平台实际总下载链如下；均含业务 JAR、实际 native 和新增构建来源记录，不含用户预装 Java 与显式外置 JDBC。

| 平台 | 平台包 / 字节 | 入口＋平台 / 字节 |
| --- | ---: | ---: |
| Windows x64 | 2,664,559 | **2,667,040** |
| macOS Intel | 3,178,636 | **3,181,117** |
| macOS Apple Silicon | 3,144,822 | **3,147,303** |
| Linux x64 | 2,736,622 | **2,739,103** |

[精确包/原生/JAR/归档哈希](evidence/native-validation-4de172f.json)绑定同一 CI run。该批仍是 `private: true` 的验证包，不用于发布。后续候选新增来源/许可材料后需重新计算大小并安装验收，严格门槛保持为 `< 15,000,000` 字节。
