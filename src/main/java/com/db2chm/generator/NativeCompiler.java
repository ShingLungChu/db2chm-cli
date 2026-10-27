package com.db2chm.generator;

import com.db2chm.cli.CliFailure;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Platform compiler resolution and bounded invocation with independent argv entries. */
public final class NativeCompiler {
    public final String kind;
    public final Path executable;
    public final Charset charset;
    public final Charset htmlCharset;
    public final Charset projectCharset;
    public final Charset navigationCharset = Charset.forName("GBK");
    public final int localeId = 0x804;
    private NativeCompiler(String kind, Path executable) {
        this.kind = kind; this.executable = executable;
        this.htmlCharset = Charset.forName("hhc".equals(kind) ? "GBK" : "UTF-8");
        this.projectCharset = htmlCharset;
        this.charset = htmlCharset; // Retained for callers of the previous API.
    }
    public static NativeCompiler resolve(String requested, String explicit) throws CliFailure {
        String kind = "auto".equals(requested) ? (isWindows() ? "hhc" : "chmcmd") : requested;
        String override = explicit;
        if (override == null || override.isEmpty()) override = System.getenv("DB2CHM_COMPILER");
        if ((override == null || override.isEmpty()) && "hhc".equals(kind)) override = System.getProperty("db2chm.hhc", System.getenv("DB2CHM_HHC"));
        if (override != null && !override.isEmpty()) return checked(kind, Paths.get(override));
        String filename = kind + (isWindows() ? ".exe" : "");
        List<Path> candidates = new ArrayList<>();
        String appHome = System.getProperty("db2chm.home");
        String home64 = System.getProperty("db2chm.home64");
        if (home64 != null) try { appHome = new String(Base64.getDecoder().decode(home64), java.nio.charset.StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { throw CliFailure.usage("Invalid application directory transport."); }
        if (appHome != null) { candidates.add(Paths.get(appHome, "native", filename)); candidates.add(Paths.get(appHome, "tools", "hhc", filename)); }
        try {
            Path app = Paths.get(NativeCompiler.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().getParent();
            candidates.add(app.resolve("native").resolve(filename)); candidates.add(app.resolve("tools/hhc").resolve(filename));
        } catch (Exception ignored) { }
        if ("hhc".equals(kind)) for (String key : Arrays.asList("ProgramFiles(x86)", "ProgramFiles")) {
            String dir = System.getenv(key); if (dir != null) candidates.add(Paths.get(dir, "HTML Help Workshop", "hhc.exe"));
        }
        String search = System.getenv("PATH");
        if (search != null) for (String item : search.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!item.isEmpty() && Paths.get(item).isAbsolute()) {
                candidates.add(Paths.get(item, filename));
                if ("chmcmd".equals(kind)) candidates.add(Paths.get(item, "chmcmd-3.2.2" + (isWindows() ? ".exe" : "")));
            }
        }
        for (Path candidate : candidates) if (Files.isRegularFile(candidate) && (isWindows() || Files.isExecutable(candidate))) return checked(kind, candidate);
        if (isWindows() && "auto".equals(requested)) return resolve("chmcmd", null);
        throw new CliFailure(3, "COMPILER_NOT_FOUND", "CHM compiler is missing. Use doctor or --compiler-path with an installed compiler.");
    }
    private static NativeCompiler checked(String kind, Path path) throws CliFailure {
        Path absolute = path.toAbsolutePath().normalize();
        // Windows execution is checked by CreateProcess; AccessCheck in older JVMs can reject valid reparse-point paths.
        if (!Files.isRegularFile(absolute) || (!isWindows() && !Files.isExecutable(absolute))) throw new CliFailure(3, "COMPILER_NOT_FOUND", "The configured compiler is not executable.");
        return new NativeCompiler(kind, absolute);
    }
    public static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"); }
    public Map<String, Object> describe() {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("kind", kind); result.put("path", executable.toString()); result.put("charset", charset.name());
        result.put("htmlCharset",htmlCharset.name()); result.put("projectCharset",projectCharset.name());
        result.put("navigationCodepage",936); result.put("localeId",localeId);
        result.put("encodingProfile","hhc".equals(kind)?"hhc-cp936":"db2chm-utf8-cp936-v1"); return result;
    }
    public void validateNavigation(String title, Collection<String> names) throws CliFailure {
        java.nio.charset.CharsetEncoder encoder=navigationCharset.newEncoder();
        if(!encoder.canEncode(title))throw encodingFailure();
        for(String name:names)if(!encoder.canEncode(name))throw encodingFailure();
    }
    private static CliFailure encodingFailure() {
        return new CliFailure(6,"ENCODING_UNREPRESENTABLE","The Chinese CHM profile requires text representable in CP936. Change unsupported characters or use --html-only for Unicode HTML.");
    }
    public Map<String, Object> compile(Path hhp, Path output, int timeoutSeconds) throws Exception {
        if (Files.exists(output)) throw new CliFailure(6, "STALE_CHM", "Compiler workspace already contains a CHM.");
        Process process;
        try {
            List<String> argv=new ArrayList<>(); argv.add(executable.toString());
            if("chmcmd".equals(kind))argv.add("--db2chm-encoding");
            argv.add(hhp.toAbsolutePath().toString());
            process = new ProcessBuilder(argv).directory(hhp.getParent().toFile()).redirectErrorStream(true).start();
        } catch (IOException e) { throw new CliFailure(3, "COMPILER_START_FAILED", "CHM compiler could not start; check its platform and runtime dependencies."); }
        Capture capture = new Capture(process.getInputStream());
        Thread reader = new Thread(capture, "db2chm-compiler-log"); reader.setDaemon(true); reader.start();
        Thread hook = new Thread(() -> process.destroyForcibly(), "db2chm-compiler-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            boolean exited;
            try { exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS); }
            catch (InterruptedException e) { process.destroyForcibly(); Thread.currentThread().interrupt(); throw e; }
            if (!exited) { process.destroyForcibly(); throw new CliFailure(124, "COMPILER_TIMEOUT", "CHM compiler timed out."); }
            reader.join(1000);
            Set<String> diagnostics = capture.diagnostics();
            boolean fatal = capture.fatal || capture.readFailed || !capture.finished;
            int code = process.exitValue();
            if(capture.encodingError)throw encodingFailure();
            if("chmcmd".equals(kind)&&capture.unsupportedProfile)throw new CliFailure(3,"COMPILER_PROFILE_UNSUPPORTED","This chmcmd does not support the required db2chm encoding profile. Use the compiler bundled with the platform package.");
            boolean expectedCode = code == 0 || ("hhc".equals(kind) && code == 1);
            byte[] signature = new byte[4];
            boolean valid = Files.isRegularFile(output) && Files.size(output) >= 96;
            if (valid) try (InputStream in = Files.newInputStream(output)) { valid = in.read(signature) == 4 && Arrays.equals(signature, new byte[]{'I','T','S','F'}); }
            if (!expectedCode || !valid || fatal) throw new CliFailure(6, "COMPILER_FAILED", "CHM compiler did not produce an accepted new CHM (exit " + code + "; diagnostics " + diagnostics + ").");
            Map<String, Object> result = describe(); result.put("exitCode", code); result.put("bytes", Files.size(output)); result.put("diagnostics", diagnostics); return result;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            boolean interrupted = Thread.interrupted();
            try { process.waitFor(5, TimeUnit.SECONDS); reader.join(1000); } catch (InterruptedException e) { interrupted = true; }
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
            if (interrupted) Thread.currentThread().interrupt();
            try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException ignored) { }
        }
    }
    private static final class Capture implements Runnable {
        private final InputStream input;
        private final Set<String> codes = new LinkedHashSet<>();
        volatile boolean fatal, readFailed, finished, encodingError, unsupportedProfile;
        Capture(InputStream input) { this.input = input; }
        public void run() {
            byte[] buffer = new byte[4096]; int n; String tail = "";
            try { while ((n = input.read(buffer)) != -1) {
                String chunk = tail + new String(buffer,0,n,java.nio.charset.StandardCharsets.ISO_8859_1);
                Matcher matcher = Pattern.compile("HHC[0-9]{4}").matcher(chunk);
                synchronized(codes) { while (matcher.find()) if(codes.size()<128) codes.add(matcher.group()); }
                fatal |= Pattern.compile("HHC[0-9]{4}:\\s*Error:",Pattern.CASE_INSENSITIVE).matcher(chunk).find();
                encodingError |= chunk.contains("DB2CHM_ENCODING:");
                unsupportedProfile |= chunk.toLowerCase(Locale.ROOT).contains("unknown option") || chunk.toLowerCase(Locale.ROOT).contains("unrecognized option");
                tail = chunk.substring(Math.max(0,chunk.length()-512));
            } } catch (IOException ignored) { readFailed=true; }
            finally { finished=true; }
        }
        Set<String> diagnostics() { synchronized(codes) { return new LinkedHashSet<>(codes); } }
    }
}
