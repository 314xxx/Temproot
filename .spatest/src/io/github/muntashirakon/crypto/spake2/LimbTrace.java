package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.GroupElement;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class LimbTrace {
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
        for (int i = 0; i < 10; i++) sb.append(String.format("%d(%x)", t[i], t[i])).append(i < 9 ? " " : "]");
        return sb.toString();
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
        Field fX = GroupElement.class.getDeclaredField("X");
        Field fY = GroupElement.class.getDeclaredField("Y");
        Field fZ = GroupElement.class.getDeclaredField("Z");
        Field fT = GroupElement.class.getDeclaredField("T");
        fX.setAccessible(true); fY.setAccessible(true); fZ.setAccessible(true); fT.setAccessible(true);

        byte[] a = new byte[32];
        a[0] = 1;
        byte[] e = (byte[]) mRadix.invoke(null, (Object) a);

        GroupElement h = curve.getZero(GroupElement.Representation.P3);

        // 奇数循环：全部加零点
        for (int i = 1; i <= 9; i += 2) {
            GroupElement t = (GroupElement) mSel.invoke(B, i / 2, e[i]);
            h = h.madd(t).toP3();
            System.out.println("odd i=" + i
                    + "\n  Y limbs=" + limbs(fY.get(h))
                    + "\n  Z limbs=" + limbs(fZ.get(h)));
        }

        // 对照：正常点 B 的 limbs
        System.out.println("B.X limbs=" + limbs(fX.get(B)));
        System.out.println("B.Y limbs=" + limbs(fY.get(B)));
    }
}
