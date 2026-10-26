package com.db2chm.cli;

import com.db2chm.db.TableInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Glob matching without executing SQL or a user-supplied regular expression. */
public final class TableFilter {
    private final List<String> includes;
    private final List<String> excludes;
    private final boolean ignoreCase;
    public TableFilter(List<String> includes, List<String> excludes, boolean ignoreCase) {
        this.includes = new ArrayList<>(includes);
        this.excludes = new ArrayList<>(excludes);
        this.ignoreCase = ignoreCase;
    }
    public List<TableInfo> select(List<TableInfo> source, int limit) {
        List<TableInfo> result = new ArrayList<>();
        for (TableInfo table : source) {
            if ((includes.isEmpty() || any(includes, table.getName())) && !any(excludes, table.getName())) result.add(table);
        }
        result.sort(Comparator.comparing(TableInfo::getName));
        return limit >= 0 && result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }
    private boolean any(List<String> patterns, String name) {
        for (String pattern : patterns) if (matches(pattern, name, ignoreCase)) return true;
        return false;
    }
    public static boolean matches(String pattern, String name, boolean ignoreCase) {
        if (ignoreCase) { pattern = pattern.toLowerCase(Locale.ROOT); name = name.toLowerCase(Locale.ROOT); }
        int[] p = pattern.codePoints().toArray(), n = name.codePoints().toArray();
        boolean[] previous = new boolean[n.length + 1]; previous[0] = true;
        for (int token : p) {
            boolean[] current = new boolean[n.length + 1];
            if (token == '*') current[0] = previous[0];
            for (int j = 1; j <= n.length; j++) {
                current[j] = token == '*' ? current[j - 1] || previous[j] : previous[j - 1] && (token == '?' || token == n[j - 1]);
            }
            previous = current;
        }
        return previous[n.length];
    }
}
