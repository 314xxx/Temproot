package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.Ed25519Field;
import io.github.muntashirakon.crypto.ed25519.FieldElement;

import java.lang.reflect.Field;

public class FieldMulTest {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    // 输出 limbs 用于 Python 对比
    static String limbs(Object fe) throws Exception {
        Field fT = Class.forName("io.github.muntashirakon.crypto.ed25519.Ed25519FieldElement").getDeclaredField("t");
        fT.setAccessible(true);
        int[] t = (int[]) fT.get(fe);
        StringBuilder sb = new StringBuilder();
        for (int v : t) sb.append(v).append(",");
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        Ed25519Field f = spec.getCurve().getField();

        String mode = args[0];

        if (mode.equals("selftest")) {
            // 简单自测：小值乘法
            FieldElement a = f.fromByteArray(hexToBytes("0200000000000000000000000000000000000000000000000000000000000000"));
            FieldElement b = f.fromByteArray(hexToBytes("0300000000000000000000000000000000000000000000000000000000000000"));
            FieldElement c = a.multiply(b);
            System.out.println("2*3=" + hex(c.toByteArray()));
            // 期望 06000000...

            // 大值乘法（随机 256 位）
            FieldElement x = f.fromByteArray(hexToBytes("d4a3f8b2c91e670455aa32bb19cc08d7e6f5214a9b3c77e0d2f1a6b58c94e301"));
            FieldElement y = f.fromByteArray(hexToBytes("77e2b1a4c8d3f69012ab45cd67ef89a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9"));
            FieldElement z = x.multiply(y);
            System.out.println("xy=" + hex(z.toByteArray()));
        } else if (mode.equals("mul2x")) {
            // 模拟 madd 中的调用模式：fe_add 输出（2x 界）再乘
            FieldElement y = f.fromByteArray(hexToBytes("0100000000000000000000000000000000000000000000000000000000000000"));
            FieldElement x0 = f.fromByteArray(hexToBytes("0000000000000000000000000000000000000000000000000000000000000000"));
            // YpX = Y + X
            FieldElement ypx = y.add(x0);
            System.out.println("ypx_limbs=" + limbs(ypx));
            // qX = y2+x2 for B
            FieldElement qx = f.fromByteArray(hexToBytes("853b8cf5c693bc2f190e8cfbc62d93cfc2423d6498480b2765bad4333a9dcf07".length() == 64 ? "853b8cf5c693bc2f190e8cfbc62d93cfc2423d6498480b2765bad4333a9dcf07" : ""));
            FieldElement a = ypx.multiply(qx);
            System.out.println("a_limbs=" + limbs(a));
            System.out.println("a_bytes=" + hex(a.toByteArray()));
        }
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
}
