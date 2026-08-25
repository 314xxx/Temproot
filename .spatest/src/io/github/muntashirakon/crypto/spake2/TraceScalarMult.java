package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.GroupElement;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TraceScalarMult {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static String coords(GroupElement g) throws Exception {
        Field fX = GroupElement.class.getDeclaredField("X");
        Field fY = GroupElement.class.getDeclaredField("Y");
        Field fZ = GroupElement.class.getDeclaredField("Z");
        Field fT = GroupElement.class.getDeclaredField("T");
        fX.setAccessible(true); fY.setAccessible(true); fZ.setAccessible(true); fT.setAccessible(true);
        io.github.muntashirakon.crypto.ed25519.FieldElement X =
                (io.github.muntashirakon.crypto.ed25519.FieldElement) fX.get(g);
        io.github.muntashirakon.crypto.ed25519.FieldElement Y =
                (io.github.muntashirakon.crypto.ed25519.FieldElement) fY.get(g);
        io.github.muntashirakon.crypto.ed25519.FieldElement Z =
                (io.github.muntashirakon.crypto.ed25519.FieldElement) fZ.get(g);
        io.github.muntashirakon.crypto.ed25519.FieldElement T =
                (io.github.muntashirakon.crypto.ed25519.FieldElement) fT.get(g);
        return "X=" + (X == null ? "null" : hex(X.toByteArray()).substring(0, 8))
                + " Y=" + (Y == null ? "null" : hex(Y.toByteArray()).substring(0, 8))
                + " Z=" + (Z == null ? "null" : hex(Z.toByteArray()).substring(0, 8))
                + " T=" + (T == null ? "null" : hex(T.toByteArray()).substring(0, 8))
                + " repr=" + g.getRepresentation();
    }

    public static void main(String[] args) throws Exception {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        GroupElement B = spec.getB();
        io.github.muntashirakon.crypto.ed25519.Curve curve =
                (io.github.muntashirakon.crypto.ed25519.Curve) spec.getCurve();

        Method mSel = GroupElement.class.getDeclaredMethod("select", int.class, int.class);
        mSel.setAccessible(true);
        Method mRadix = GroupElement.class.getDeclaredMethod("toRadix16", byte[].class);
        mRadix.setAccessible(true);

        byte[] a = new byte[32];
        a[0] = 1;
        byte[] e = (byte[]) mRadix.invoke(null, (Object) a);

        GroupElement h = curve.getZero(GroupElement.Representation.P3);
        System.out.println("init h: " + coords(h));

        // 奇数循环
        for (int i = 1; i < 64; i += 2) {
            GroupElement t = (GroupElement) mSel.invoke(B, i / 2, e[i]);
            h = h.madd(t).toP3();
            if (i <= 5 || i >= 61) System.out.println("odd i=" + i + " h: " + coords(h));
        }

        System.out.println("before dbl chain: " + coords(h));
        h = h.dbl().toP2();
        System.out.println("dbl1(P2): " + coords(h));
        h = h.dbl().toP2();
        System.out.println("dbl2(P2): " + coords(h));
        h = h.dbl().toP2();
        System.out.println("dbl3(P2): " + coords(h));
        h = h.dbl().toP3();
        System.out.println("dbl4(P3): " + coords(h));

        // 偶数循环
        for (int i = 0; i < 64; i += 2) {
            GroupElement t = (GroupElement) mSel.invoke(B, i / 2, e[i]);
            h = h.madd(t).toP3();
            if (e[i] != 0 || i == 0) System.out.println("even i=" + i + " e=" + e[i] + " h: " + coords(h)
                    + " enc=" + hex(h.toByteArray()));
        }
        System.out.println("final: " + hex(h.toByteArray()));
    }
}
