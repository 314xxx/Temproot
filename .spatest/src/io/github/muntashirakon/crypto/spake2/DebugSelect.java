package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.GroupElement;
import io.github.muntashirakon.crypto.ed25519.Curve;

import java.lang.reflect.Field;

public class DebugSelect {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        GroupElement B = spec.getB();
        Curve curve = (Curve) spec.getCurve();

        // 1. precmp[0][0] 的原始三个分量
        Field fld = GroupElement.class.getDeclaredField("precmp");
        fld.setAccessible(true);
        GroupElement[][] precmp = (GroupElement[][]) fld.get(B);
        GroupElement p00 = precmp[0][0];
        Field fX = GroupElement.class.getDeclaredField("X");
        Field fY = GroupElement.class.getDeclaredField("Y");
        Field fZ = GroupElement.class.getDeclaredField("Z");
        fX.setAccessible(true); fY.setAccessible(true); fZ.setAccessible(true);
        System.out.println("precmp[0][0].X=" + hex(((io.github.muntashirakon.crypto.ed25519.FieldElement) fX.get(p00)).toByteArray()));
        System.out.println("precmp[0][0].Y=" + hex(((io.github.muntashirakon.crypto.ed25519.FieldElement) fY.get(p00)).toByteArray()));
        System.out.println("precmp[0][0].Z=" + hex(((io.github.muntashirakon.crypto.ed25519.FieldElement) fZ.get(p00)).toByteArray()));

        // 2. select(0, 1) 的结果
        java.lang.reflect.Method mSel = GroupElement.class.getDeclaredMethod("select", int.class, int.class);
        mSel.setAccessible(true);
        GroupElement sel = (GroupElement) mSel.invoke(B, 0, 1);
        System.out.println("select(0,1).X=" + hex(((io.github.muntashirakon.crypto.ed25519.FieldElement) fX.get(sel)).toByteArray()));
        System.out.println("select(0,1).Y=" + hex(((io.github.muntashirakon.crypto.ed25519.FieldElement) fY.get(sel)).toByteArray()));
        System.out.println("select(0,1).Z=" + hex(((io.github.muntashirakon.crypto.ed25519.FieldElement) fZ.get(sel)).toByteArray()));

        // 3. zero.madd(select) 的结果
        GroupElement h = curve.getZero(GroupElement.Representation.P3);
        GroupElement r = h.madd(sel).toP3();
        System.out.println("zero+sel=" + hex(r.toByteArray()));

        // 4. 直接用 B 的 precomp 表示（手工构造）madd
        // B: x=4/5, y=4/5*? -- 用 fromByteArray 重建
        GroupElement p1 = new GroupElement(curve, B.toByteArray(), false);
        System.out.println("reB=" + hex(p1.toByteArray()));

        // 5. toRadix16(1)
        java.lang.reflect.Method mRadix = GroupElement.class.getDeclaredMethod("toRadix16", byte[].class);
        mRadix.setAccessible(true);
        byte[] a = new byte[32];
        a[0] = 1;
        byte[] e = (byte[]) mRadix.invoke(null, (Object) a);
        StringBuilder sb = new StringBuilder();
        for (byte x : e) sb.append(x).append(",");
        System.out.println("toRadix16(1)=" + sb);

        // 6. toRadix16(2)
        a[0] = 2;
        e = (byte[]) mRadix.invoke(null, (Object) a);
        sb = new StringBuilder();
        for (byte x : e) sb.append(x).append(",");
        System.out.println("toRadix16(2)=" + sb);
    }
}
