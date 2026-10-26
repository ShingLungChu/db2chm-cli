package com.db2chm.util;

import com.db2chm.db.ConnectionInfo;
import com.db2chm.db.DatabaseType;
import com.db2chm.db.JdbcPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 最近连接配置持久化. 存储到 ~/.db2chm/config.json.
 */
public class ConfigStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Config {
        public List<ConnectionInfo> recent = new ArrayList<>();
        public String lastOutputDir;
        public int limit = 5;
    }

    private final File cfgFile;
    private Config config;

    public ConfigStore() {
        File home = new File(System.getProperty("user.home"));
        File dir = new File(home, ".db2chm");
        if (!dir.exists()) dir.mkdirs();
        this.cfgFile = new File(dir, "config.json");
        this.config = load();
    }

    private Config load() {
        if (!cfgFile.exists()) return new Config();
        try {
            String s = new String(Files.readAllBytes(cfgFile.toPath()), StandardCharsets.UTF_8);
            Config c = GSON.fromJson(s, Config.class);
            if (c == null) return new Config();
            if (c.recent == null) c.recent = new ArrayList<>();
            c.recent.removeIf(entry -> !safeToPersist(entry));
            // Rewrite legacy records to remove serialized passwords and unsafe URL properties.
            Files.write(cfgFile.toPath(), GSON.toJson(c).getBytes(StandardCharsets.UTF_8));
            return c;
        } catch (Exception e) {
            return new Config();
        }
    }

    public synchronized void save() {
        try {
            Files.write(cfgFile.toPath(), GSON.toJson(config).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) { /* best-effort */ }
    }

    public Config get() { return config; }

    /** 推一条连接到最近列表头部(去重 by describe). 最多保留 limit 条. */
    public synchronized void pushRecent(ConnectionInfo info) {
        if (!safeToPersist(info)) return;
        config.recent.removeIf(c -> sameKey(c, info));
        config.recent.add(0, info);
        while (config.recent.size() > config.limit) {
            config.recent.remove(config.recent.size() - 1);
        }
        save();
    }

    public List<ConnectionInfo> getRecent() {
        return new ArrayList<>(config.recent);
    }

    public String getLastOutputDir() { return config.lastOutputDir; }

    public void setLastOutputDir(String d) {
        config.lastOutputDir = d;
        save();
    }

    private static boolean sameKey(ConnectionInfo a, ConnectionInfo b) {
        if (a == null || b == null) return false;
        return eq(a.getDbType(), b.getDbType())
                && eq(a.getUrl(), b.getUrl())
                && eq(a.getUser(), b.getUser());
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean safeToPersist(ConnectionInfo info) {
        if (info == null) return false;
        try { JdbcPolicy.validate(info); return true; } catch (Exception e) { return false; }
    }

    /** 把 dbType 字符串还原成枚举(Gson 反序列化时枚举处理). */
    public static DatabaseType parseDbType(String name) {
        if (name == null) return DatabaseType.MYSQL;
        try {
            return DatabaseType.valueOf(name);
        } catch (Exception e) {
            return DatabaseType.MYSQL;
        }
    }
}
