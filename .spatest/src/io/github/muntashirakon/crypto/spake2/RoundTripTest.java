package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.Ed25519Field;
import io.github.muntashirakon.crypto.ed25519.FieldElement;

public class RoundTripTest {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static void main(String[] args) {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        Ed25519Field f = spec.getCurve().getField();

        String[] tests = {
                "d4a3f8b2c91e670455aa32bb19cc08d7e6f5214a9b3c77e0d2f1a6b58c94e301",
                "77e2b1a4c8d3f69012ab45cd67ef89a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9",
                "5866666666666666666666666666666666666666666666666666666666666666",
                "0100000000000000000000000000000000000000000000000000000000000000"
        };
        for (String t : tests) {
            byte[] b = hexToBytes(t);
            byte[] rt = f.fromByteArray(b).toByteArray();
            System.out.println((t.equals(hex(rt)) ? "RT-OK  " : "RT-FAIL") + " " + t + " -> " + hex(rt));
        }

        // square 测试
        byte[] b = hexToBytes("d4a3f8b2c91e670455aa32bb19cc08d7e6f5214a9b3c77e0d2f1a6b58c94e301");
        FieldElement fe = f.fromByteArray(b);
        System.out.println("square=" + hex(fe.square().toByteArray()));
        // multiply by self（应与 square 一致）
        System.out.println("mulself=" + hex(fe.multiply(fe).toByteArray()));
    }
}
