package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.GroupElement;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BisectZeroAdd {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static String limbs(Object fe) throws Exception {
        Field fT = Class.forName("io.github.muntashirakon.crypto.ed25519.Ed25519FieldElement").getDeclaredField("t");
        fT.setAccessible(true);
        int[] t = (int[]) fT.get(fe);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(t[i]).append(i < 9 ? " " : "]");
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        GroupElement B = spec.getB();
        io.github.muntashirakon.crypto.ed25519.Curve curve =
                (io.github.muntashirakon.crypto.ed25519.Curve) spec.getCurve();

        Method mSel = GroupElement.class.getDeclaredMethod("select", int.class, int.class);
        mSel.setAccessible(true);
        Field fY = GroupElement.class.getDeclaredField("Y");
        Field fZ = GroupElement.class.getDeclaredField("Z");
        fY.setAccessible(true); fZ.setAccessible(true);

        // 零点 PRECOMP
        GroupElement zeroPrecomp = curve.getZero(GroupElement.Representation.PRECOMP);

        // h = zero + B
        GroupElement h = curve.getZero(GroupElement.Representation.P3);
        h = h.madd((GroupElement) mSel.invoke(B, 0, 1)).toP3();
        System.out.println("step0 enc=" + hex(h.toByteArray()));

        // 逐次 madd 零点
        for (int k = 1; k <= 31; k++) {
            h = h.madd(zeroPrecomp).toP3();
            String enc = hex(h.toByteArray());
            boolean ok = enc.equals("5866666666666666666666666666666666666666666666666666666666666666");
            if (k <= 3 || !ok) {
                System.out.println("step" + k + " enc=" + enc.substring(0, 16) + (ok ? " OK" : " WRONG")
                        + "\n   Y=" + limbs(fY.get(h))
                        + "\n   Z=" + limbs(fZ.get(h)));
            }
            if (!ok) break;
        }
    }
}
