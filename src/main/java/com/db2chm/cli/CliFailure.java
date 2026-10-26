package com.db2chm.cli;

/** Stable CLI failure contract. Messages must not contain credentials or raw vendor diagnostics. */
public final class CliFailure extends Exception {
    private final int exitCode;
    private final String code;
    public CliFailure(int exitCode, String code, String message) {
        super(message); this.exitCode = exitCode; this.code = code;
    }
    public int exitCode() { return exitCode; }
    public String code() { return code; }
    public static CliFailure usage(String message) { return new CliFailure(2, "INVALID_ARGUMENT", message); }
}
