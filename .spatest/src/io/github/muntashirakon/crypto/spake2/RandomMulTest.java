package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519Field;
import io.github.muntashirakon.crypto.ed25519.Ed25519FieldElement;
import io.github.muntashirakon.crypto.ed25519.FieldElement;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 随机 limbs 的 multiply 正确性测试：stdin 每行一组
 * "t0,t1,...,t9 g0,g1,...,g9"（带符号 limbs，模拟 fe_add/fe_sub 输出），
 * 输出 multiply 编码，与 Python 大整数参考对比。
 */
public class RandomMulTest {
    public static void main(String[] args) throws Exception {
        Ed25519Field f = (Ed25519Field) Ed25519.getSpec().getCurve().getField();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            int[] t = parseLimbs(parts[0]);
            int[] g = parseLimbs(parts[1]);
            FieldElement te = new Ed25519FieldElement(f, t.clone());
            FieldElement ge = new Ed25519FieldElement(f, g.clone());
            FieldElement r = te.multiply(ge);
            byte[] enc = r.toByteArray();
            StringBuilder sb = new StringBuilder();
            for (byte b : enc) sb.append(String.format("%02x", b));
            System.out.println(sb);
        }
    }

    static int[] parseLimbs(String s) {
        String[] p = s.split(",");
        int[] r = new int[10];
        for (int i = 0; i < 10; i++) r[i] = Integer.parseInt(p[i]);
        return r;
    }
}
