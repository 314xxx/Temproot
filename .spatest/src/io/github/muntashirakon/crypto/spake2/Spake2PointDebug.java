package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Curve;
import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.GroupElement;
import io.github.muntashirakon.crypto.ed25519.Utils;

import java.nio.charset.StandardCharsets;

public class Spake2PointDebug {
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

    // copy of the private method in Spake2Context
    static GroupElement geScalarMultiplySmallPrecomp(Curve curve, final byte[] a, GroupElement[] precompTable) {
        GroupElement h = curve.getZero(GroupElement.Representation.P3);
        for (long i = 63; i >= 0; i--) {
            int index = 0;
            for (long j = 0; j < 4; j++) {
                byte bit = (byte) (1 & (a[(int) ((8 * j) + (i >>> 3))] >>> (i & 7)));
                index |= (bit << j);
            }
            GroupElement e = curve.getZero(GroupElement.Representation.PRECOMP);
            for (int j = 1; j < 16; j++) {
                e = e.cmov(precompTable[j - 1], Utils.equal(index, j));
            }
            h = h.add(h.toCached()).toP3().madd(e).toP3();
        }
        return h;
    }

    public static void main(String[] args) throws Exception {
        byte[] privKey = unhex(args[0]);      // 32 bytes
        byte[] passwordScalar = unhex(args[1]); // 32 bytes
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();

        GroupElement P = spec.getB().scalarMultiply(privKey);
        System.out.println("P=" + hex(P.toByteArray()));

        GroupElement mask = geScalarMultiplySmallPrecomp(spec.getCurve(), passwordScalar, Spake2Context.SPAKE_M_SMALL_PRECOMP);
        System.out.println("mask=" + hex(mask.toByteArray()));

        GroupElement PStar = P.add(mask.toCached()).toP2();
        System.out.println("PStar=" + hex(PStar.toByteArray()));

        // also: what does scalarMultiply on M directly give (no precomp table)?
        GroupElement maskDirect = Spake2Context.SPAKE_M_SMALL_PRECOMP[0].toP3().scalarMultiply(passwordScalar);
        System.out.println("maskDirect=" + hex(maskDirect.toByteArray()));
    }
}
