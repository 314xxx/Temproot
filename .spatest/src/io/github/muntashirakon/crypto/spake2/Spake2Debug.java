package io.github.muntashirakon.crypto.spake2;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

public class Spake2Debug {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static byte[] unhex(String s) {
        byte[] r = new byte[s.length() / 2];
        for (int i = 0; i < r.length; i++) r[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return r;
    }

    public static void main(String[] args) throws Exception {
        byte[] password = unhex(args[0]);
        byte[] priv64 = unhex(args[1]);
        Spake2Context ctx = new Spake2Context(Spake2Role.Alice,
                "adb pair client\0".getBytes(StandardCharsets.UTF_8),
                "adb pair server\0".getBytes(StandardCharsets.UTF_8));
        byte[] msg = ctx.generateMessage(password, priv64);
        dump(ctx);
        System.out.println("my_msg=" + hex(msg));
    }

    static void dump(Spake2Context ctx) throws Exception {
        for (String f : new String[]{"privateKey", "passwordScalar", "passwordHash"}) {
            Field fld = Spake2Context.class.getDeclaredField(f);
            fld.setAccessible(true);
            byte[] v = (byte[]) fld.get(ctx);
            System.out.println(f + "=" + hex(v));
        }
    }
}
