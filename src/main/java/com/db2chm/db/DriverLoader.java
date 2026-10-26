package com.db2chm.db;

import com.db2chm.cli.CliFailure;
import java.io.Closeable;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Driver;
import java.util.jar.JarFile;

/** Explicit caller-trusted JDBC JAR; never searches the working directory for executable code. */
public final class DriverLoader implements Closeable {
    private URLClassLoader loader;
    public Driver load(ConnectionInfo info) throws Exception {
        String path = info.getDriverJar();
        if (path == null || path.trim().isEmpty()) {
            // Legacy GUI fallback is confined to fixed filenames beside the installed application.
            String[] filenames = {"ojdbc7-12.1.0.2.0.jar", "mysql-connector-java-8.0.17.jar", "DmJdbcDriver18-8.1.2.192.jar", "Vastbase-G100-2.14_pg_2026011411.jar", "kingbase8-9.0.0.jar"};
            try {
                Path application = Paths.get(DriverLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
                Path home = Files.isRegularFile(application) ? application.getParent() : null;
                if (home != null) {
                    int index;
                    switch (info.getDbType()) { case ORACLE: index=0; break; case MYSQL: index=1; break; case DM: index=2; break; case VASTBASE: index=3; break; default: index=4; }
                    Path candidate = home.resolve("lib").resolve(filenames[index]);
                    if (Files.isRegularFile(candidate)) path = candidate.toString();
                }
            } catch (java.net.URISyntaxException ignored) { }
        }
        ClassLoader parent = DriverLoader.class.getClassLoader();
        if (path != null && !path.trim().isEmpty()) {
            Path jar = Paths.get(path).toAbsolutePath().normalize();
            if (!Files.isRegularFile(jar) || !Files.isReadable(jar)) throw new CliFailure(3, "DRIVER_NOT_FOUND", "The selected JDBC JAR is not readable.");
            try (JarFile file = new JarFile(jar.toFile())) {
                if (file.getJarEntry(info.getDbType().driverClass().replace('.', '/') + ".class") == null) throw new CliFailure(3, "DRIVER_MISMATCH", "JDBC JAR does not contain the required driver class.");
            }
            loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, parent); parent = loader;
        }
        try { return (Driver) Class.forName(info.getDbType().driverClass(), true, parent).getDeclaredConstructor().newInstance(); }
        catch (ClassNotFoundException e) { throw new CliFailure(3, "DRIVER_NOT_FOUND", "Provide the vendor JDBC JAR using --driver-jar or config driverJar."); }
    }
    @Override public void close() { if (loader != null) try { loader.close(); } catch (Exception ignored) { } }
}
