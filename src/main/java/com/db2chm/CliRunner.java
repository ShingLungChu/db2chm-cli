package com.db2chm;

import com.db2chm.cli.*;
import com.db2chm.db.*;
import com.db2chm.generator.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Non-interactive CLI; the GUI continues to share metadata and rendering modules. */
public final class CliRunner {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private CliRunner() { }
    public static String version() {
        Properties properties = new Properties();
        try (InputStream in = CliRunner.class.getResourceAsStream("/version.properties")) { if (in != null) properties.load(in); } catch (IOException ignored) { }
        return properties.getProperty("version", "0.2.0-beta.1");
    }
    public static String help() {
        try (InputStream in = CliRunner.class.getResourceAsStream("/cli-help.txt")) {
            if (in == null) return "db2chm: help resource missing";
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) { return "db2chm: help resource unavailable"; }
    }
    public static int run(String[] args) {
        PrintStream output = System.out, errors = System.err;
        // Third-party JDBC libraries cannot pollute the machine-readable stdout channel.
        try { System.setOut(errors); return run(args, output, errors, System.in, System.getenv()); }
        finally { System.setOut(output); }
    }
    public static int run(String[] args, PrintStream output, PrintStream errors, InputStream input, Map<String, String> environment) {
        boolean json = Arrays.asList(args).contains("--json");
        String command = "unknown";
        try {
            CliOptions options = CliOptions.parse(args); json = options.json; command = options.command;
            if ("help".equals(command) || "version".equals(command)) {
                String value = "help".equals(command) ? help() : version();
                if (json) emit(output, true, command, map("value", value), null); else output.println(value);
                return 0;
            }
            if ("doctor".equals(command)) {
                Map<String, Object> data = doctor(options);
                boolean ready = Boolean.TRUE.equals(data.get("ready"));
                if (json) emit(output, ready, command, data, ready ? null : map("code", "DEPENDENCIES_MISSING", "message", "Install the reported dependencies before exporting."));
                else output.println(JSON.toJson(data));
                return ready ? 0 : 3;
            }
            if (Arrays.asList(args).contains("--password")) errors.println("Deprecated: use DB2CHM_PASSWORD or --password-stdin; command arguments can appear in process listings.");
            AtomicBoolean cancelled = new AtomicBoolean();
            ExecutorService executor = Executors.newSingleThreadExecutor(task -> { Thread t = new Thread(task, "db2chm-export"); t.setDaemon(true); return t; });
            Future<Map<String, Object>> future = executor.submit(() -> {
                if (options.passwordStdin) options.connection.setPassword(readPassword(input));
                else if (options.connection.getPassword() == null) options.connection.setPassword(environment.get("DB2CHM_PASSWORD"));
                return execute(options, errors, cancelled);
            });
            Map<String, Object> data;
            try { data = future.get(options.timeout, TimeUnit.SECONDS); }
            catch (TimeoutException e) { cancelled.set(true); future.cancel(true); throw new CliFailure(124, "OPERATION_TIMEOUT", "Operation timed out."); }
            catch (InterruptedException e) { cancelled.set(true); future.cancel(true); Thread.currentThread().interrupt(); throw new CliFailure(130, "CANCELLED", "Operation was cancelled."); }
            catch (ExecutionException e) {
                Throwable failure = e.getCause();
                if (failure instanceof CliFailure) throw (CliFailure) failure;
                if (failure instanceof LinkageError) throw new CliFailure(3, "DRIVER_INCOMPATIBLE", "JDBC driver is not compatible with this Java runtime.");
                if (failure instanceof IOException) throw new CliFailure(7, "IO_FAILED", "Could not read or write the selected files.");
                throw new CliFailure(6, "EXPORT_FAILED", "Export failed; verify dependencies and the selected output path.");
            } finally { executor.shutdownNow(); }
            if (json) emit(output, true, command, data, null);
            else if ("tables list".equals(command)) {
                for (Object table : (List<?>) data.get("tables")) output.println(JSON.toJson(table));
            } else output.println("Generated " + data.get("tableCount") + " tables: " + data.get("outputDir"));
            return 0;
        } catch (CliFailure failure) {
            if (json) emit(output, false, command, null, map("code", failure.code(), "message", failure.getMessage()));
            else errors.println(failure.code() + ": " + failure.getMessage());
            return failure.exitCode();
        } catch (Exception failure) {
            if (json) emit(output, false, command, null, map("code", "INVALID_ARGUMENT", "message", "Invalid configuration or arguments."));
            else errors.println("INVALID_ARGUMENT: Invalid configuration or arguments.");
            return 2;
        }
    }
    private static Map<String, Object> execute(CliOptions options, PrintStream errors, AtomicBoolean cancelled) throws Exception {
        NativeCompiler compiler = null;
        if ("export".equals(options.command) && !options.htmlOnly) compiler = NativeCompiler.resolve(options.compiler, options.compilerPath);
        DbAdapter adapter;
        if (options.mock) {
            options.connection.setUrl("mock://fixture/dictionary");
            adapter = new MockDbAdapter(options.connection, options.mockTables, options.mockColumns);
        } else {
            JdbcPolicy.validate(options.connection);
            adapter = DbAdapterFactory.create(options.connection.getDbType(), options.connection);
        }
        try {
            errors.println("Reading database metadata" + (options.mock ? " (mock)." : "."));
            try { adapter.connect(); } catch (CliFailure e) { throw e; } catch (Exception e) { throw new CliFailure(4, "CONNECTION_FAILED", "Database connection or read-only session setup failed."); }
            List<TableInfo> tables;
            try { tables = new TableFilter(options.includes, options.excludes, options.ignoreCase).select(adapter.listTables(), options.maxTables); }
            catch (CliFailure e) { throw e; } catch (Exception e) { throw new CliFailure(5, "METADATA_FAILED", "Could not read the authorized table list."); }
            List<Map<String, Object>> selected = new ArrayList<>();
            for (TableInfo table : tables) selected.add(map("name", table.getName(), "comment", table.getComment()));
            if ("tables list".equals(options.command)) return map("tableCount", tables.size(), "tables", selected);
            if (tables.isEmpty()) throw new CliFailure(5, "NO_TABLES_MATCHED", "No tables matched; no output was generated.");
            for (int i = 0; i < tables.size(); i++) {
                checkCancelled(cancelled);
                TableInfo listed = tables.get(i), full;
                try { full = adapter.getTableColumns(listed.getName()); } catch (Exception e) { throw new CliFailure(5, "METADATA_FAILED", "Could not read columns for a selected table."); }
                if (full == null || full.getColumnCount() == 0) throw new CliFailure(5, "METADATA_EMPTY", "A selected table has no readable columns.");
                if (!listed.getComment().isEmpty()) full.setComment(listed.getComment());
                tables.set(i, full);
            }
            checkCancelled(cancelled);
            try (OutputTransaction transaction = new OutputTransaction(options.out)) {
                HtmlGenerator renderer = new HtmlGenerator();
                renderer.setOutputCharset(compiler == null ? "UTF-8" : compiler.charset.name());
                Map<String, String> pages = renderer.renderAll(transaction.workspace.toFile(), options.connection, tables);
                ChmBuilder project = new ChmBuilder(transaction.workspace.toFile());
                project.setTitle(options.title); project.setCharset(renderer.getOutputCharset()); project.setPageMap(pages); project.setHtmlFiles(project.listHtmlFiles());
                project.writeHhp(); project.writeHhc(); project.writeHhk();
                checkCancelled(cancelled);
                Map<String, Object> backend = compiler == null ? null : compiler.compile(project.getHhpFile().toPath(), project.getChmFile().toPath(), options.timeout);
                checkCancelled(cancelled);
                transaction.publish(cancelled);
                return map("tableCount", tables.size(), "tables", selected, "outputDir", transaction.destination.toString(),
                    "html", transaction.destination.resolve("index.html").toString(), "chm", compiler == null ? null : transaction.destination.resolve("database.chm").toString(), "compiler", backend);
            }
        } finally { adapter.close(); }
    }
    private static void checkCancelled(AtomicBoolean cancelled) throws CliFailure {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CliFailure(130, "CANCELLED", "Operation was cancelled.");
    }
    private static String readPassword(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); int c;
        while ((c = input.read()) >= 0 && c != '\n') {
            if (bytes.size() >= 4096) throw CliFailure.usage("Password input is too long.");
            bytes.write(c);
        }
        byte[] result = bytes.toByteArray();
        int size = result.length > 0 && result[result.length - 1] == '\r' ? result.length - 1 : result.length;
        return new String(result, 0, size, StandardCharsets.UTF_8);
    }
    private static Map<String, Object> doctor(CliOptions options) {
        Map<String, Object> checks = new LinkedHashMap<>(); boolean ready = true;
        checks.put("java", map("ok", true, "version", System.getProperty("java.version")));
        checks.put("platform", map("os", System.getProperty("os.name"), "arch", System.getProperty("os.arch")));
        try { checks.put("compiler", NativeCompiler.resolve(options.compiler, options.compilerPath).describe()); }
        catch (CliFailure e) { checks.put("compiler", map("ok", false, "code", e.code())); ready = false; }
        if (options.connection.getDbType() != null) {
            try (DriverLoader loader = new DriverLoader()) {
                loader.load(options.connection);
                checks.put("driver", map("ok", true, "type", options.connection.getDbType().name(), "class", options.connection.getDbType().driverClass()));
            } catch (Throwable e) { checks.put("driver", map("ok", false, "code", "DRIVER_UNAVAILABLE")); ready = false; }
        }
        return map("ready", ready, "version", version(), "checks", checks);
    }
    private static void emit(PrintStream out, boolean ok, String command, Object data, Object error) {
        out.println(JSON.toJson(map("schemaVersion", 1, "ok", ok, "command", command, "data", data, "error", error)));
    }
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
