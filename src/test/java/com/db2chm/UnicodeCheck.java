package com.db2chm;
import com.google.gson.Gson;
import java.util.*;
public final class UnicodeCheck {
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out,true,"UTF-8"));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("matches",args.length==1&&"过滤 output".equals(args[0]));
        result.put("args",args);
        result.put("fileEncoding",System.getProperty("file.encoding"));
        result.put("nativeEncoding",System.getProperty("sun.jnu.encoding"));
        System.out.println(new Gson().toJson(result));
    }
}
