package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.GroupElement;

import java.lang.reflect.Field;

public class TableBisect {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        GroupElement B = spec.getB();

        // dbl test: 2B
        GroupElement twoB = B.dbl().toP3();
        System.out.println("dbl_2B=" + hex(twoB.toByteArray()));

        // add test: B + B via cached
        GroupElement twoB2 = B.add(B.toCached()).toP3();
        System.out.println("add_2B=" + hex(twoB2.toByteArray()));

        // precmp table via reflection
        Field fld = GroupElement.class.getDeclaredField("precmp");
        fld.setAccessible(true);
        GroupElement[][] precmp = (GroupElement[][]) fld.get(B);
        System.out.println("precmp dims: " + precmp.length + " x " + precmp[0].length);
        for (int j = 0; j < 8; j++) {
            GroupElement e = precmp[0][j];
            // PRECOMP representation: (y+x, y-x, 2dxy). Encode to check.
            // Convert: reconstruct point from precomp is hard; instead print the three field elements.
            System.out.println("precmp[0][" + j + "] ypx=" + hex(e.getX().toByteArray())
                    + " ymx=" + hex(e.getY().toByteArray())
                    + " xy2d=" + hex(e.getZ().toByteArray()));
        }

        // select test
        java.lang.reflect.Method sel = GroupElement.class.getDeclaredMethod("select", int.class, int.class);
        sel.setAccessible(true);
        for (int b = 1; b <= 8; b++) {
            GroupElement t = (GroupElement) sel.invoke(B, 0, b);
            System.out.println("select(0," + b + ") ypx=" + hex(t.getX().toByteArray())
                    + " ymx=" + hex(t.getY().toByteArray())
                    + " xy2d=" + hex(t.getZ().toByteArray()));
        }
    }
}
