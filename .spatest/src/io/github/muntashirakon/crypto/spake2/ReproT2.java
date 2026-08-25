package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519Field;
import io.github.muntashirakon.crypto.ed25519.FieldElement;

import java.lang.reflect.Field;

/** 精确复现 TraceZeroAdd step2 的 T2 计算：T2 = X'.multiply(Y')。 */
public class ReproT2 {
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

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static String limbs(Object fe) throws Exception {
        int[] t = (int[]) fT.get(fe);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(t[i]).append(i < 9 ? " " : "]");
        return sb.toString();
    }

    static byte[] hexToBytes(String s) {
        byte[] r = new byte[s.length() / 2];
        for (int i = 0; i < r.length; i++)
            r[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return r;
    }

    public static void main(String[] args) throws Exception {
        Ed25519Field f = (Ed25519Field) Ed25519.getSpec().getCurve().getField();

        // step1 的 X1/Y1 limbs（来自 TraceZeroAdd step1 输出）
        int[] x1 = {24463280, 14026994, 26626737, 19549007, 19711840, 28776218, 4603834, 32900391, 53852495, 23672531};
        int[] y1 = {26843593, 6710886, 53687091, 13421772, 40265318, 26843545, 13421772, 20132659, 26843545, 6710886};

        FieldElement X1 = f.fromByteArray(limbsToBytes(x1));
        FieldElement Y1 = f.fromByteArray(limbsToBytes(y1));
        FieldElement ONE = f.fromByteArray(hexToBytes(
                "0100000000000000000000000000000000000000000000000000000000000000"));

        // madd(zero) 的中间量（q = (1,1,0)）
        FieldElement YpX = Y1.add(X1);
        FieldElement YmX = Y1.subtract(X1);
        System.out.println("YpX_limbs=" + limbs(YpX));
        System.out.println("YmX_limbs=" + limbs(YmX));

        FieldElement A = YpX.multiply(ONE);
        FieldElement B = YmX.multiply(ONE);
        System.out.println("A_limbs=" + limbs(A));
        System.out.println("B_limbs=" + limbs(B));

        FieldElement Xp = A.subtract(B);
        FieldElement Yp = A.add(B);
        System.out.println("Xp_limbs=" + limbs(Xp));
        System.out.println("Yp_limbs=" + limbs(Yp));

        // T2 = X' * Y'（toP3 的第 4 个乘法）
        FieldElement T2 = Xp.multiply(Yp);
        System.out.println("T2=" + hex(T2.toByteArray()));
        System.out.println("T2_limbs=" + limbs(T2));

        // 对照：用 Python 可验证的期望值打印 X'/Y' 的规范编码
        System.out.println("Xp_enc=" + hex(Xp.toByteArray()));
        System.out.println("Yp_enc=" + hex(Yp.toByteArray()));
        System.out.println("Xp_2=" + hex(Xp.add(Xp).toByteArray()));
        System.out.println("Yp_2=" + hex(Yp.add(Yp).toByteArray()));
    }

    static byte[] limbsToBytes(int[] t) {
        // 带符号 limbs -> 规范值 -> LE bytes（借 fromByteArray 完成解码）
        java.math.BigInteger v = java.math.BigInteger.ZERO;
        int[] W = {0, 26, 51, 77, 102, 128, 153, 179, 204, 230};
        java.math.BigInteger p = java.math.BigInteger.valueOf(2).pow(255)
                .subtract(java.math.BigInteger.valueOf(19));
        for (int i = 9; i >= 0; i--)
            v = v.add(java.math.BigInteger.valueOf(t[i]).shiftLeft(W[i]));
        v = v.mod(p);
        byte[] b = v.toByteArray();
        byte[] le = new byte[32];
        for (int i = 0; i < b.length && i < 32; i++) le[i] = b[b.length - 1 - i];
        return le;
    }
}
