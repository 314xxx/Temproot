package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.GroupElement;

public class PrimTest {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static byte[] le(long v) {
        byte[] r = new byte[32];
        for (int i = 0; i < 8; i++) r[i] = (byte) (v >>> (8 * i));
        return r;
    }

    public static void main(String[] args) {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        GroupElement B = spec.getB();
        System.out.println("B=" + hex(B.toByteArray()));
        System.out.println("1B=" + hex(B.scalarMultiply(le(1)).toByteArray()));
        System.out.println("2B=" + hex(B.scalarMultiply(le(2)).toByteArray()));
        System.out.println("3B=" + hex(B.scalarMultiply(le(3)).toByteArray()));
        System.out.println("8B=" + hex(B.scalarMultiply(le(8)).toByteArray()));
        System.out.println("12345B=" + hex(B.scalarMultiply(le(12345)).toByteArray()));
        // table check: first entry of N table should be N itself
        GroupElement N1 = Spake2Context.SPAKE_N_SMALL_PRECOMP[0];
        System.out.println("N0=" + hex(N1.toP3().toByteArray()));
        GroupElement M1 = Spake2Context.SPAKE_M_SMALL_PRECOMP[0];
        System.out.println("M0=" + hex(M1.toP3().toByteArray()));
    }
}
