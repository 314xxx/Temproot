package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Curve;
import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.GroupElement;

import java.lang.reflect.Field;

/**
 * 追踪 h = B; h = h.madd(zero).toP3() 迭代：
 * 每步打印 X/Y/Z/T limbs 与编码，并输出 limbs 供 Python 数学验证
 * （正确性判据：X ≡ Z·Bx 且 Y ≡ Z·By (mod p)，因为加零不改变点值）。
 */
public class TraceZeroAdd {
    static final int[] W = {0, 26, 51, 77, 102, 128, 153, 179, 204, 230};
    static Field fT;

    static {
        try {
            fT = Class.forName("io.github.muntashirakon.crypto.ed25519.Ed25519FieldElement")
                    .getDeclaredField("t");
            fT.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static int[] limbs(Object fe) throws Exception {
        return (int[]) fT.get(fe);
    }

    static String limbsStr(int[] t) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(t[i]).append(i < 9 ? " " : "]");
        return sb.toString();
    }

    // limbs -> hex（带符号 2^25.5 表示转成模 p 大整数的 32 字节 LE）
    static String limbsToHex(int[] t) {
        java.math.BigInteger v = java.math.BigInteger.ZERO;
        java.math.BigInteger p = java.math.BigInteger.valueOf(2).pow(255).subtract(java.math.BigInteger.valueOf(19));
        for (int i = 9; i >= 0; i--) {
            v = v.add(java.math.BigInteger.valueOf(t[i]).shiftLeft(W[i]));
        }
        v = v.mod(p);
        byte[] b = v.toByteArray();
        byte[] le = new byte[32];
        for (int i = 0; i < b.length && i < 32; i++) le[i] = b[b.length - 1 - i];
        StringBuilder sb = new StringBuilder();
        for (byte x : le) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        Curve curve = (Curve) spec.getCurve();
        GroupElement B = spec.getB();

        GroupElement zeroPrecomp = curve.getZero(GroupElement.Representation.PRECOMP);
        GroupElement h = curve.getZero(GroupElement.Representation.P3);
        java.lang.reflect.Method mSel = GroupElement.class.getDeclaredMethod("select", int.class, int.class);
        mSel.setAccessible(true);
        h = h.madd((GroupElement) mSel.invoke(B, 0, 1)).toP3();

        for (int k = 1; k <= 14; k++) {
            h = h.madd(zeroPrecomp).toP3();
            int[] X = limbs(h.getX()), Y = limbs(h.getY()), Z = limbs(h.getZ()), T = limbs(h.getT());
            System.out.println("step" + k);
            System.out.println("  X=" + limbsToHex(X) + " " + limbsStr(X));
            System.out.println("  Y=" + limbsToHex(Y) + " " + limbsStr(Y));
            System.out.println("  Z=" + limbsToHex(Z) + " " + limbsStr(Z));
            System.out.println("  T=" + limbsToHex(T) + " " + limbsStr(T));
        }
    }
}
