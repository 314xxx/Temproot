package io.github.muntashirakon.crypto.ed25519;

public class Ed25519PrimTest {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static byte[] le(long v) {
        byte[] r = new byte[32];
        for (int i = 0; i < 8; i++) r[i] = (byte) (v >>> (8 * i));
        return r;
    }

    public static void main(String[] args) {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        GroupElement B = spec.getB();

        // dbl: 2B
        System.out.println("B.dbl      =" + hex(B.dbl().toP2().toByteArray()));
        // add: B + B
        System.out.println("B.add(B)   =" + hex(B.add(B.toCached()).toP3().toByteArray()));
        // toRadix16 of 1
        byte[] r16 = GroupElement.toRadix16(le(1));
        StringBuilder sb = new StringBuilder();
        for (byte x : r16) sb.append(x).append(',');
        System.out.println("radix16(1) =" + sb);

        // decode+encode roundtrip of B
        byte[] bEnc = B.toByteArray();
        GroupElement B2 = spec.getCurve().createPoint(bEnc, false);
        System.out.println("B.roundtrip=" + hex(B2.toByteArray()));

        // select(0, 1) should equal precomp(B) → madd with zero = B
        GroupElement z = spec.getCurve().getZero(GroupElement.Representation.P3);
        GroupElement sel = B.select(0, 1);
        System.out.println("z.madd(sel)=" + hex(z.madd(sel).toP3().toByteArray()));
    }
}
