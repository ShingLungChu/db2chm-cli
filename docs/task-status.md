# npm release task status

Updated: 2026-10-26. Implementation baseline: main at 594b32a. A task is complete only when its release-plan acceptance evidence exists.

| Task | Status | Evidence / remaining acceptance |
| --- | --- | --- |
| T00 | In progress | Source comparison complete; npm scope/ownership, oldest tested OS and real database versions still pending |
| T01 | In progress | HHC runtime is present but repository bundle fails on this host; redistribution permission not established |
| T02 | In progress | Packaged-tool feasibility passed; pinned-source native build still required |
| T03 | In progress | Five local vendor JARs inventoried; no vendor binaries approved for redistribution |
| T04 | In progress | Implementing CLI contract and machine output |
| T05 | In progress | Implementing fixed-driver, connection-policy and read-only guardrails; real database audit pending |
| T06 | In progress | Implementing common list/export glob selector |
| T07 | In progress | Implementing platform compiler interface and staged output |
| T08 | Pending | Requires platform payloads and packaging validation |
| T09 | Pending | Real database environments and Windows Viewer interactive checks required |
| T10 | Pending | Depends on stable implemented commands |
| T11 | Pending | Build/release workflow and provenance required |
| T12 | Pending | No RC accepted yet |
| T13 | Pending | npm ownership/identity and all release gates required |

## Baseline decision

The original source has 25 files; the publish source has 24. Differences in CliRunner.java, ConnectionInfo.java and DatabaseType.java are sanitized example connection values only. TestRender.java exists only in the original and is a local rendering helper, not production functionality. Continue in this Git repository; do not copy original examples or local database output into it.

## Runtime targets

Node 22/24, Java 8/17/21, win32-x64, darwin-x64, darwin-arm64 and linux-x64. These are targets until matrix runs pass. Oldest OS/libc claims require measured binaries and an actual oldest-system check. Linux arm64 and musl are excluded from the first release.

## Naming and size

Package scope is configurable during local packaging; final scope ownership is pending user input. Release candidate: 0.2.0-beta.1. The installation download budget is strictly below 15,000,000 bytes including the entry package and selected platform/dependency packages. Do not mark an unavailable compiler or restricted driver as delivered.
