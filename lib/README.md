# JDBC drivers

This repository and the npm packages do not include database vendor JDBC binaries. Obtain a trusted driver from its vendor under the applicable license. For CLI use, select it explicitly with `--driver-jar PATH` or the configuration's `driverJar` field. A relative configuration path is resolved from that configuration file's directory; the driver class is fixed by database type.

The legacy GUI also recognizes these exact filenames in `lib/` beside the application JAR:

- `ojdbc7-12.1.0.2.0.jar` — Oracle
- `mysql-connector-java-8.0.17.jar` — MySQL
- `DmJdbcDriver18-8.1.2.192.jar` — Dameng
- `Vastbase-G100-2.14_pg_2026011411.jar` — Vastbase
- `kingbase8-9.0.0.jar` — KingbaseES

The build does not need these files. DriverLoader checks the approved class and loads the explicit JAR at runtime. The current working directory is not scanned for drivers. The `.gitignore` excludes JARs from this directory so vendor binaries are not accidentally published. Candidate versions, SHA-256 values and validation limits are recorded in `docs/dependencies.md`.
