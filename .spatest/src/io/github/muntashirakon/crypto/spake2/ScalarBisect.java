package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.GroupElement;

public class ScalarBisect {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static void main(String[] args) {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        // B itself
        System.out.println("B=" + hex(spec.getB().toByteArray()));
        for (int k : new int[]{1, 2, 3, 4, 8, 16, 100, 255}) {
            byte[] a = new byte[32];
            a[0] = (byte) (k & 0xff);
            a[1] = (byte) ((k >> 8) & 0xff);
            GroupElement r = spec.getB().scalarMultiply(a);
            System.out.println(k + "*B=" + hex(r.toByteArray()));
        }
    }
}
