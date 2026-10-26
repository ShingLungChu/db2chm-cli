package com.db2chm;

import com.db2chm.cli.*;
import com.db2chm.db.*;
import com.db2chm.generator.*;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CoreChecks {
    private static void check(boolean condition, String message) { if(!condition) throw new AssertionError(message); }
    private static ConnectionInfo connection(DatabaseType type, String url) { ConnectionInfo i=new ConnectionInfo();i.setDbType(type);i.setUrl(url);i.setUser("metadata_reader");return i; }
    private static final class Adapter extends AbstractDbAdapter {
        Adapter(ConnectionInfo i){super(i);}
        public List<TableInfo> listTables(){return Collections.emptyList();}
        public TableInfo getTableColumns(String name){return null;}
        void query(String sql)throws Exception{try(java.sql.PreparedStatement s=prepareMetadataQuery(sql)){}}
    }
    public static void main(String[] args)throws Exception {
        check(TableFilter.matches("中文?表","中文😀表",false),"Unicode glob");
        check(TableFilter.matches("BIZ_*","biz_order",true),"Case folding");
        check(!TableFilter.matches(".*","anything",false),"Glob must not become user regex");
        check(TableFilter.matches("a;DROP TABLE x","a;DROP TABLE x",false),"Special names are literal");
        Map<DatabaseType,String> valid=new EnumMap<>(DatabaseType.class);
        valid.put(DatabaseType.MYSQL,"jdbc:mysql://localhost:3306/demo?useSSL=false&serverTimezone=Asia%2FShanghai");
        valid.put(DatabaseType.ORACLE,"jdbc:oracle:thin:@localhost:1521/ORCLPDB1");
        valid.put(DatabaseType.DM,"jdbc:dm://localhost:5236");
        valid.put(DatabaseType.VASTBASE,"jdbc:postgresql://localhost:5432/demo?currentSchema=public");
        valid.put(DatabaseType.KINGBASE,"jdbc:kingbase8://localhost:54321/demo?currentSchema=public");
        for(Map.Entry<DatabaseType,String> e:valid.entrySet()) JdbcPolicy.validate(connection(e.getKey(),e.getValue()));
        ConnectionInfo info=connection(DatabaseType.MYSQL,"jdbc:mysql://localhost:3306/demo");
        info.setPassword("test-secret-not-persisted");
        check(!new Gson().toJson(info).contains("test-secret-not-persisted"),"Password persisted");
        check(!info.describe().contains("localhost"),"Full connection printed");
        Adapter adapter=new Adapter(info); adapter.connect();
        check(com.mysql.cj.jdbc.Driver.readOnlyCalled,"Read-only not requested");
        check("false".equals(com.mysql.cj.jdbc.Driver.received.getProperty("allowMultiQueries")),"Multiqueries enabled");
        adapter.query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=?");
        check(com.mysql.cj.jdbc.Driver.statementTimeout==30,"Query deadline missing");
        try{adapter.query("DELETE FROM demo");throw new AssertionError("Write query accepted");}catch(java.sql.SQLException expected){}
        adapter.close(); check(com.mysql.cj.jdbc.Driver.closed,"Connection not closed");
        com.mysql.cj.jdbc.Driver.readOnlyAccepted=false;
        try{new Adapter(info).connect();throw new AssertionError("Non-read-only connection accepted");}catch(CliFailure e){check(e.exitCode()==8,"Wrong read-only refusal");}
        check(com.mysql.cj.jdbc.Driver.closed,"Refused connection leaked");
        Path root=Files.createTempDirectory(Paths.get(args[1]),"native checks 中文 ");
        try {
            NativeCompiler compiler=NativeCompiler.resolve("chmcmd",args[0]);
            Path hhp=root.resolve("project with spaces.hhp"), out=root.resolve("database.chm");
            String success="const fs=require('fs'),p=require('path');let b=Buffer.alloc(96);b.write('ITSF');fs.writeFileSync(p.join(p.dirname(process.argv[1]),'database.chm'),b);";
            Files.write(hhp,success.getBytes(StandardCharsets.UTF_8));
            check(((Number)compiler.compile(hhp,out,5).get("bytes")).longValue()==96,"Compiler argv/path handling");
            try{compiler.compile(hhp,out,5);throw new AssertionError("Stale output accepted");}catch(CliFailure e){check(e.code().equals("STALE_CHM"),"Stale output code");}
            Files.delete(out);
            Files.write(hhp,(success+"process.exit(2);").getBytes(StandardCharsets.UTF_8));
            try{compiler.compile(hhp,out,5);throw new AssertionError("Compiler error exit accepted");}catch(CliFailure e){check(e.code().equals("COMPILER_FAILED"),"Compiler exit contract");}
            Files.delete(out);
            Files.write(hhp,"setTimeout(()=>{},10000);".getBytes(StandardCharsets.UTF_8));
            try{compiler.compile(hhp,out,1);throw new AssertionError("Compiler timeout ignored");}catch(CliFailure e){check(e.exitCode()==124,"Compiler timeout code");}
            Path destination=root.resolve("published");
            try(OutputTransaction transaction=new OutputTransaction(destination.toString())){
                Files.write(transaction.workspace.resolve("new"),new byte[]{1});
                Files.createDirectory(destination);
                Files.write(destination.resolve("sentinel"),new byte[]{2});
                try{transaction.publish(new AtomicBoolean());throw new AssertionError("Concurrent output overwritten");}catch(CliFailure e){check(e.code().equals("OUTPUT_EXISTS"),"Concurrent destination code");}
            }
            check(Files.exists(destination.resolve("sentinel")),"Existing output changed");
            try{new ChmBuilder(root.toFile()).setTitle("title\nCompiled file=outside");throw new AssertionError("HHP option injection accepted");}catch(IllegalArgumentException expected){}
        } finally {
            Files.walkFileTree(root,new java.nio.file.SimpleFileVisitor<Path>(){
                public FileVisitResult visitFile(Path f,java.nio.file.attribute.BasicFileAttributes a)throws java.io.IOException{Files.delete(f);return FileVisitResult.CONTINUE;}
                public FileVisitResult postVisitDirectory(Path p,java.io.IOException e)throws java.io.IOException{Files.delete(p);return FileVisitResult.CONTINUE;}
            });
        }
        System.out.println("Core safety and compiler invocation checks passed (test doubles, not real database/CHM compatibility).");
    }
}
