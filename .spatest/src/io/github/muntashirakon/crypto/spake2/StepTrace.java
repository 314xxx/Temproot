package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Curve;
import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.Ed25519ScalarOps;
import io.github.muntashirakon.crypto.ed25519.GroupElement;
import io.github.muntashirakon.crypto.ed25519.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 把 Spake2Context.generateMessage 拆成独立步骤，逐层输出中间值，
 * 与 Python BoringSSL 参考实现 (ref_spake2.py) 对比以定位错误层。
 */
public class StepTrace {
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

    public static void main(String[] args) throws Exception {
        byte[] password = unhex(args[0]);
        byte[] priv64 = unhex(args[1]);

        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        Ed25519ScalarOps scalarOps = spec.getScalarOps();

        // Step 1: reduce + *8
        byte[] privateKey = scalarOps.reduce(priv64);
        System.out.println("priv_reduced=" + hex(privateKey));
        int carry = 0;
        for (int i = 0; i < 32; i++) {
            int nextCarry = (byte) ((privateKey[i] & 0xFF) >>> 5);
            privateKey[i] = (byte) ((privateKey[i] << 3) | carry);
            carry = nextCarry;
        }
        System.out.println("priv8=" + hex(privateKey));

        // Step 2: P = B * priv8
        GroupElement P = spec.getB().scalarMultiply(privateKey);
        System.out.println("P=" + hex(P.toByteArray()));

        // Step 3: password hash + reduce + hack
        byte[] ph = Spake2Context.getHash("SHA-512", password);
        System.out.println("ph=" + hex(ph));
        byte[] ps = scalarOps.reduce(ph);
        System.out.println("ps_reduced=" + hex(ps));
        byte[] hacked = hackAdd(ps);
        System.out.println("ps_hacked=" + hex(hacked));

        // Step 4: mask = M * ps_hacked via small precomp table
        Spake2Context ctx = new Spake2Context(Spake2Role.Alice,
                "adb pair client\0".getBytes(), "adb pair server\0".getBytes());
        Method m = Spake2Context.class.getDeclaredMethod("geScalarMultiplySmallPrecomp",
                Curve.class, byte[].class, GroupElement[].class);
        m.setAccessible(true);
        Field fM = Spake2Context.class.getDeclaredField("SPAKE_M_SMALL_PRECOMP");
        fM.setAccessible(true);
        GroupElement[] tableM = (GroupElement[]) fM.get(null);
        GroupElement mask = (GroupElement) m.invoke(ctx, spec.getCurve(), hacked, tableM);
        System.out.println("mask=" + hex(mask.toByteArray()));

        // Step 5: PStar = P + mask
        GroupElement pStar = P.add(mask.toCached()).toP2();
        System.out.println("pstar=" + hex(pStar.toByteArray()));
    }

    static byte[] hackAdd(byte[] ps) {
        byte[] l = Utils.hexToBytes(
                "edd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010");
        byte[] s = ps.clone();
        if ((s[0] & 1) != 0) s = addBytes(s, l);
        byte[] l2 = shiftLeft1(l);
        if ((s[0] & 2) != 0) s = addBytes(s, l2);
        byte[] l4 = shiftLeft1(l2);
        if ((s[0] & 4) != 0) s = addBytes(s, l4);
        return s;
    }

    static byte[] addBytes(byte[] a, byte[] b) {
        byte[] r = new byte[32];
        int carry = 0;
        for (int i = 0; i < 32; i++) {
            int tmp = (a[i] & 0xFF) + (b[i] & 0xFF) + carry;
            r[i] = (byte) tmp;
            carry = tmp >>> 8;
        }
        return r;
    }

    static byte[] shiftLeft1(byte[] a) {
        byte[] r = new byte[32];
        int carry = 0;
        for (int i = 0; i < 32; i++) {
            int carryOut = (a[i] & 0xFF) >>> 7;
            r[i] = (byte) ((a[i] << 1) | carry);
            carry = carryOut;
        }
        return r;
    }
}
