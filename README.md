# db2chm

Java application that reads database metadata and generates HTML/CHM database dictionaries. It currently includes GUI and command-line entry points for Oracle, MySQL, Dameng, Vastbase, and KingbaseES.

## Current platform status

- The Java source and Maven build are platform-neutral and target Java 8.
- The current `ChmBuilder` integration locates Microsoft's `hhc.exe`; the Java application does not yet select `chmcmd` on macOS or Linux.
- The Actions workflow tests `chmcmd` directly with a synthetic HHP fixture on native Linux and macOS runners, then checks the resulting CHMs with Windows `hh.exe`. This is a feasibility harness, not yet proof that the Java CLI generates CHM files on macOS/Linux.
- The workflow also compiles the Java application without redistributing database vendor drivers.

## Build

Requirements: Java 8 and Maven 3.6 or later.

```sh
mvn --batch-mode -DskipTests package
```

The result is `target/db2chm.jar`. JDBC drivers are loaded at runtime by class name and are not included in this repository. See [`lib/README.md`](lib/README.md) for the expected local filenames. Add only drivers you are licensed to use; do not commit driver JARs or connection details.

## Run

Run a mock export without connecting to a database:

```sh
java -jar target/db2chm.jar --cli --mock --type MYSQL --tables 10 --html-only --out ./out/mock
```

For a real database, provide the matching JDBC driver in `lib/` and pass connection settings to `--cli`. The current command-line interface accepts a password argument; avoid putting real credentials in shell history or shared logs.

```sh
java -jar target/db2chm.jar --cli \
  --type MYSQL \
  --url jdbc:mysql://localhost:3306/example \
  --user dbuser \
  --password "$DB2CHM_PASSWORD" \
  --out ./out/example
```

Supported `--type` values: `ORACLE`, `MYSQL`, `DM`, `VASTBASE`, `KINGBASE`.

## CHM compiler feasibility workflow

Use **Actions → CHM native-runner feasibility → Run workflow**. It builds a small synthetic CHM on Linux x64, macOS Intel, and macOS Apple Silicon, uploads the results as short-lived artifacts, and asks Windows HTML Help to read them. The fixture covers a Chinese title, Simplified Chinese locale, TOC, index, and full-text search. It contains no project database documentation.

Passing this workflow establishes compatibility for the fixture and the packaged `chmcmd` version used by those runners. It does not establish compatibility with every real project HHP, nor does it build a reproducible `chmcmd` binary for npm distribution.
