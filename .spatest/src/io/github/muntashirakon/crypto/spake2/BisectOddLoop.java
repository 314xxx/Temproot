package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.GroupElement;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BisectOddLoop {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
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

        byte[] a = new byte[32];
        a[0] = 1;
        byte[] e = (byte[]) mRadix.invoke(null, (Object) a);

        for (int k = 0; k <= 32; k++) {
            GroupElement h = curve.getZero(GroupElement.Representation.P3);
            // 奇数循环执行 k 次（i = 1, 3, ..., 2k-1）
            for (int i = 1; i <= 2 * k - 1; i += 2) {
                GroupElement t = (GroupElement) mSel.invoke(B, i / 2, e[i]);
                h = h.madd(t).toP3();
            }
            // dbl 链 ×4
            h = h.dbl().toP2().dbl().toP2().dbl().toP2().dbl().toP3();
            // 偶数循环（只有 i=0 非零）
            for (int i = 0; i < 64; i += 2) {
                GroupElement t = (GroupElement) mSel.invoke(B, i / 2, e[i]);
                h = h.madd(t).toP3();
            }
            String enc = hex(h.toByteArray());
            System.out.println("k=" + k + " -> " + enc.substring(0, 16)
                    + (enc.equals("5866666666666666666666666666666666666666666666666666666666666666") ? "  OK" : "  WRONG"));
        }
    }
}
