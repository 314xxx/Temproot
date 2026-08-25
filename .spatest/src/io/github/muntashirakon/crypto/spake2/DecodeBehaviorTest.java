package io.github.muntashirakon.crypto.spake2;

import io.github.muntashirakon.crypto.ed25519.Curve;
import io.github.muntashirakon.crypto.ed25519.Ed25519;
import io.github.muntashirakon.crypto.ed25519.Ed25519CurveParameterSpec;
import io.github.muntashirakon.crypto.ed25519.GroupElement;
import io.github.muntashirakon.crypto.ed25519.Utils;

import java.security.SecureRandom;

/**
 * Verifies what Curve.fromBytesNegateVarTime actually does:
 *  - standard decode: decode(encode(P)) == P
 *  - negate decode:   decode(encode(P)) == -P
 *  - broken:          neither
 */
public class DecodeBehaviorTest {
    public static void main(String[] args) {
        Ed25519CurveParameterSpec spec = Ed25519.getSpec();
        Curve curve = spec.getCurve();
        SecureRandom rnd = new SecureRandom();

        int standard = 0, negated = 0, other = 0;
        int signBitOne = 0, signBitOneWrong = 0;

        for (int i = 0; i < 200; i++) {
            byte[] scalar = new byte[32];
            rnd.nextBytes(scalar);
            GroupElement P = spec.getB().scalarMultiply(scalar);
            byte[] enc = P.toByteArray();
            boolean signBit = (enc[31] & 0x80) != 0;
            if (signBit) signBitOne++;

            GroupElement dec = curve.fromBytesNegateVarTime(enc);
            byte[] reenc = dec.toByteArray();

            // -P encoded: same y, flipped x sign
            GroupElement negP = new GroupElement(curve, GroupElement.Representation.P3,
                    P.getX().negate(), P.getY(), P.getZ(), P.getT().negate());
            byte[] negEnc = negP.toByteArray();

            if (Utils.equal(reenc, enc) == 1) {
                standard++;
                if (signBit) signBitOneWrong++; // for sign-bit-1 points this would be "lucky standard"
            } else if (Utils.equal(reenc, negEnc) == 1) {
                negated++;
            } else {
                other++;
                if (i < 4) {
                    System.out.printf("enc : %s%n", Utils.bytesToHex(enc));
                    System.out.printf("reen: %s%n", Utils.bytesToHex(reenc));
                }
            }
        }
        System.out.printf("standard=%d negated=%d other=%d (signBit1 points=%d, signBit1-standard=%d)%n",
                standard, negated, other, signBitOne, signBitOneWrong);

        // Also test the GroupElement constructor decode (standard decode path)
        int ok = 0, bad = 0;
        for (int i = 0; i < 200; i++) {
            byte[] scalar = new byte[32];
            rnd.nextBytes(scalar);
            GroupElement P = spec.getB().scalarMultiply(scalar);
            byte[] enc = P.toByteArray();
            GroupElement dec = curve.createPoint(enc, false);
            if (Utils.equal(dec.toByteArray(), enc) == 1) ok++; else bad++;
        }
        System.out.printf("GroupElement-ctor decode: ok=%d bad=%d%n", ok, bad);
    }
}
