package com.db2chm.cli;

import com.db2chm.db.ConnectionInfo;
import com.db2chm.db.DatabaseType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public final class CliOptions {
    public String command = "help", out = "output", title = "数据库字典", compiler = "auto", compilerPath;
    public boolean json, mock, htmlOnly, ignoreCase, passwordStdin;
    public int mockTables = 10, mockColumns = 0, maxTables = -1, timeout = 120;
    public final ConnectionInfo connection = new ConnectionInfo();
    public final List<String> includes = new ArrayList<>(), excludes = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();

    public static CliOptions parse(String[] args) throws CliFailure {
        CliOptions o = new CliOptions();
        int start = 0;
        if (args.length > 0 && !args[0].startsWith("-")) {
            o.command = args[0]; start = 1;
            if ("tables".equals(o.command)) {
                if (args.length < 2 || !"list".equals(args[1])) throw CliFailure.usage("Use tables list.");
                o.command = "tables list"; start = 2;
            }
            if (!Arrays.asList("help", "version", "doctor", "export", "tables list").contains(o.command)) throw CliFailure.usage("Unknown command.");
        } else if (args.length > 0) o.command = "export"; // legacy --cli options
        // Apply explicit file configuration first; command-line fields override it.
        for (int i = start; i < args.length; i++) if ("--config".equals(args[i])) {
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) throw CliFailure.usage("--config requires a file path.");
            o.loadConfig(args[++i]);
        }
        for (int i = start; i < args.length; i++) {
            String key = args[i];
            boolean repeat = "--include".equals(key) || "--exclude".equals(key);
            if (!repeat && !o.seen.add(key)) throw CliFailure.usage("Duplicate option: " + key);
            switch (key) {
                case "--help": case "-h": o.command = "help"; break;
                case "--version": case "-V": o.command = "version"; break;
                case "--json": o.json = true; break;
                case "--mock": o.mock = true; break;
                case "--html-only": o.htmlOnly = true; break;
                case "--ignore-case": o.ignoreCase = true; break;
                case "--password-stdin": o.passwordStdin = true; break;
                default:
                    if (!Arrays.asList("--config", "--type", "--url", "--user", "--password", "--schema", "--charset", "--driver", "--driver-jar", "--out", "--title", "--compiler", "--compiler-path", "--include", "--exclude", "--tables", "--mock-tables", "--mock-columns", "--max-tables", "--timeout", "--connect-timeout", "--query-timeout").contains(key)) throw CliFailure.usage("Unknown option: " + safeOption(key));
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) throw CliFailure.usage("Missing value for " + key);
                    String value = args[++i];
                    if (value.indexOf('\0') >= 0) throw CliFailure.usage("NUL is not allowed in arguments.");
                    switch (key) {
                        case "--config": break;
                        case "--type": o.setType(value); break;
                        case "--url": o.connection.setUrl(value); break;
                        case "--user": o.connection.setUser(value); break;
                        case "--password": o.connection.setPassword(value); break;
                        case "--schema": o.connection.setSchema(value); break;
                        case "--charset": o.connection.setCharset(value); break;
                        case "--driver": o.connection.setDriverClass(value); break;
                        case "--driver-jar": o.connection.setDriverJar(value); break;
                        case "--out": o.out = value; break;
                        case "--title": o.title = value; break;
                        case "--compiler": o.compiler = value; break;
                        case "--compiler-path": o.compilerPath = value; break;
                        case "--include": o.addPattern(o.includes, value); break;
                        case "--exclude": o.addPattern(o.excludes, value); break;
                        case "--tables": case "--mock-tables": o.mockTables = number(key, value, 1, 10000); break;
                        case "--mock-columns": o.mockColumns = number(key, value, 8, 1000); break;
                        case "--max-tables": o.maxTables = number(key, value, 1, 10000); break;
                        case "--timeout": o.timeout = number(key, value, 1, 3600); break;
                        case "--connect-timeout": o.connection.setConnectTimeout(number(key, value, 1, 300)); break;
                        case "--query-timeout": o.connection.setQueryTimeout(number(key, value, 1, 300)); break;
                        default: throw CliFailure.usage("Unsupported option.");
                    }
            }
        }
        Set<String> selection = new HashSet<>(Arrays.asList("--mock", "--tables", "--mock-tables", "--mock-columns", "--include", "--exclude", "--ignore-case", "--max-tables"));
        Set<String> exporting = new HashSet<>(Arrays.asList("--out", "--title", "--html-only", "--compiler", "--compiler-path"));
        for (String key : o.seen) {
            if (("help".equals(o.command) || "version".equals(o.command)) && !Arrays.asList("--help", "-h", "--version", "-V", "--json").contains(key)) throw CliFailure.usage("Help/version accept only --json.");
            if ("tables list".equals(o.command) && exporting.contains(key)) throw CliFailure.usage("Export option is not valid for tables list: " + key);
            if ("doctor".equals(o.command) && (selection.contains(key) || Arrays.asList("--out", "--title", "--html-only", "--password", "--password-stdin").contains(key))) throw CliFailure.usage("Option is not valid for doctor: " + key);
        }
        boolean helpFlag = o.seen.contains("--help") || o.seen.contains("-h");
        boolean versionFlag = o.seen.contains("--version") || o.seen.contains("-V");
        if (helpFlag && versionFlag) throw CliFailure.usage("Choose help or version.");
        if (o.passwordStdin && o.seen.contains("--password")) throw CliFailure.usage("Password sources conflict.");
        if (o.seen.contains("--tables") && o.seen.contains("--mock-tables")) throw CliFailure.usage("Use only --mock-tables or legacy --tables.");
        if (!o.mock && (o.seen.contains("--tables") || o.seen.contains("--mock-tables") || o.seen.contains("--mock-columns"))) throw CliFailure.usage("Mock counts require --mock.");
        if (!Arrays.asList("auto", "hhc", "chmcmd").contains(o.compiler)) throw CliFailure.usage("--compiler must be auto, hhc or chmcmd.");
        if (o.title.length() > 512 || o.title.chars().anyMatch(c -> c < 32 || c == 127)) throw CliFailure.usage("Title contains invalid control characters or is too long.");
        try { Charset.forName(o.connection.getCharset()); } catch (Exception e) { throw CliFailure.usage("Unsupported charset."); }
        if (o.mock && o.connection.getDbType() == null) o.connection.setDbType(DatabaseType.MYSQL);
        if ("export".equals(o.command) || "tables list".equals(o.command)) {
            if (o.connection.getDbType() == null) throw CliFailure.usage("--type is required.");
            if (!o.mock && o.connection.getUrl().isEmpty()) throw CliFailure.usage("--url or config url is required.");
        }
        return o;
    }
    private static String safeOption(String option) { return option.matches("--?[a-zA-Z0-9-]{1,40}") ? option : "[invalid]"; }
    private void setType(String value) throws CliFailure {
        DatabaseType type = DatabaseType.byName(value.toUpperCase(Locale.ROOT));
        if (type == null) throw CliFailure.usage("Unsupported database type."); connection.setDbType(type);
    }
    private void addPattern(List<String> target, String value) throws CliFailure {
        if (value.isEmpty() || value.length() > 1024 || target.size() >= 128) throw CliFailure.usage("Invalid or excessive table patterns.");
        target.add(value);
    }
    private static int number(String key, String value, int min, int max) throws CliFailure {
        try { int n = Integer.parseInt(value); if (n >= min && n <= max) return n; } catch (NumberFormatException ignored) { }
        throw CliFailure.usage(key + " must be an integer in " + min + ".." + max + ".");
    }
    private void loadConfig(String path) throws CliFailure {
        try {
            if (Files.size(Paths.get(path)) > 65536) throw CliFailure.usage("Connection config is too large.");
            JsonElement root = JsonParser.parseString(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
            if (!root.isJsonObject()) throw CliFailure.usage("Connection config must be a JSON object.");
            JsonObject object = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (!Arrays.asList("type", "url", "user", "schema", "charset", "driverJar").contains(entry.getKey())) throw CliFailure.usage("Unsupported config key; passwords must use environment or stdin.");
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) throw CliFailure.usage("Connection config values must be strings.");
                String v = entry.getValue().getAsString();
                switch (entry.getKey()) {
                    case "type": setType(v); break;
                    case "url": connection.setUrl(v); break;
                    case "user": connection.setUser(v); break;
                    case "schema": connection.setSchema(v); break;
                    case "charset": connection.setCharset(v); break;
                    case "driverJar": connection.setDriverJar(Paths.get(path).toAbsolutePath().getParent().resolve(v).normalize().toString()); break;
                    default: throw CliFailure.usage("Unsupported config key.");
                }
            }
        } catch (CliFailure e) { throw e; } catch (Exception e) { throw CliFailure.usage("Cannot read a valid connection config."); }
    }
}
