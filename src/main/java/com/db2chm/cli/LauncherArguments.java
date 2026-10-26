package com.db2chm.cli;

import com.google.gson.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** ASCII launcher marker plus UTF-8 environment transport avoids Windows ACP argument loss. */
public final class LauncherArguments {
    private LauncherArguments() { }
    public static String[] decode(String[] args, Map<String,String> environment) throws CliFailure {
        if (args.length == 0 || !"--launcher-argv".equals(args[0])) return args;
        if (args.length > 2 || (args.length == 2 && !"--json".equals(args[1]))) throw CliFailure.usage("Invalid launcher transport.");
        try {
            String encoded = environment.get("DB2CHM_ARGV64");
            if (encoded == null || encoded.length() > 24000) throw new IllegalArgumentException();
            byte[] bytes = Base64.getDecoder().decode(encoded);
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonArray array = JsonParser.parseString(text).getAsJsonArray();
            if (array.size() > 512) throw new IllegalArgumentException();
            String[] decoded = new String[array.size()];
            for (int i=0; i<decoded.length; i++) {
                JsonElement value = array.get(i);
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
                decoded[i] = value.getAsString();
            }
            return decoded;
        } catch (Exception e) { throw CliFailure.usage("Invalid UTF-8 launcher arguments."); }
    }
}
