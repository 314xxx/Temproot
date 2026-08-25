package io.github.muntashirakon.crypto.spake2;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Cross-test harness: runs the jar's Spake2Context against the Python BoringSSL reference. */
public class Spake2CrossTest {
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
        String mode = args[0];
        if (mode.equals("tables")) {
            Field fN = Spake2Context.class.getDeclaredField("PRECOMP_TABLE_N");
            Field fM = Spake2Context.class.getDeclaredField("PRECOMP_TABLE_M");
            fN.setAccessible(true); fM.setAccessible(true);
            int[] tN = (int[]) fN.get(null), tM = (int[]) fM.get(null);
            StringBuilder bN = new StringBuilder(), bM = new StringBuilder();
            for (int v : tN) bN.append(String.format("%02x", v & 0xFF));
            for (int v : tM) bM.append(String.format("%02x", v & 0xFF));
            System.out.println("N " + bN);
            System.out.println("M " + bM);
        } else if (mode.equals("alice") || mode.equals("bob")) {
            // stdin JSON-ish: password=<hex> priv=<hex> [peer_msg=<hex>]
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String password = null, priv = null, peerMsg = null;
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("password=")) password = line.substring(9);
                else if (line.startsWith("priv=")) priv = line.substring(5);
                else if (line.startsWith("peer_msg=")) peerMsg = line.substring(9);
            }
            Spake2Role role = mode.equals("alice") ? Spake2Role.Alice : Spake2Role.Bob;
            // Alice is the pairing client, Bob is the pairing server (adbd).
            Spake2Context ctx = new Spake2Context(role,
                    (mode.equals("alice") ? "adb pair client\0" : "adb pair server\0").getBytes(StandardCharsets.UTF_8),
                    (mode.equals("alice") ? "adb pair server\0" : "adb pair client\0").getBytes(StandardCharsets.UTF_8));
            byte[] msg = ctx.generateMessage(unhex(password), unhex(priv));
            System.out.println("my_msg=" + hex(msg));
            if (peerMsg != null) {
                byte[] key = ctx.processMessage(unhex(peerMsg));
                System.out.println("my_key=" + hex(key));
            }
        }
    }
}
