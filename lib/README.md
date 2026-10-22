# JDBC drivers

This repository does not include database vendor JDBC binaries. Obtain a driver from its vendor under the applicable license, then place it in `lib/` using the filename expected by the application manifest:

- `ojdbc7-12.1.0.2.0.jar` — Oracle
- `mysql-connector-java-8.0.17.jar` — MySQL
- `DmJdbcDriver18-8.1.2.192.jar` — Dameng
- `Vastbase-G100-2.14_pg_2026011411.jar` — Vastbase
- `kingbase8-9.0.0.jar` — KingbaseES

The build does not need these files because the application loads JDBC drivers at runtime by class name. The `.gitignore` excludes JARs from this directory so vendor binaries are not accidentally published.
