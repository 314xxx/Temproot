package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.Ed25519Field;
import io.github.muntashirakon.crypto.ed25519.FieldElement;

public class NegLimbTest {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
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

    public static void main(String[] args) {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        Ed25519Field f = spec.getCurve().getField();

        // 规范值（< p，bit255=0）
        FieldElement x = f.fromByteArray(hexToBytes("d4a3f8b2c91e670455aa32bb19cc08d7e6f5214a9b3c77e0d2f1a6b58c94e301"));
        FieldElement y = f.fromByteArray(hexToBytes("13be07eb31babcbab991b9bed616ead72b3466c4eaa637252ac2a3b9efdd9175"));

        // 正常乘法（应正确）
        System.out.println("pos_mul=" + hex(x.multiply(y).toByteArray()));

        // 负 limbs 输入：negate 产生负 limbs
        FieldElement nx = x.negate();
        System.out.println("neg(x)*y=" + hex(nx.multiply(y).toByteArray()));
        // 期望 = p - pos_mul

        // 减法产生负 limbs：x - y（x < y 时为负）
        FieldElement d = x.subtract(y);
        System.out.println("(x-y)*y=" + hex(d.multiply(y).toByteArray()));

        // 加法 2x 界：x + x
        FieldElement s = x.add(x);
        System.out.println("(x+x)*y=" + hex(s.multiply(y).toByteArray()));
    }
}
