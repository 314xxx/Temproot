#!/usr/bin/env python3
"""Pure-Python reference of BoringSSL SPAKE2 (spake25519.cc), for testing the Java jar."""
import hashlib, sys, json

p = 2**255 - 19
d = (-121665 * pow(121666, p - 2, p)) % p
l = 2**252 + 27742317777372353535851937790883648493
SQRT_M1 = pow(2, (p - 1) // 4, p)
G = (15112221349535400772501151409588531511454012693041857206046113283949847762202,
     46316835694926478169428394003475163141307993866256225615783033603165251855960)


def add(P, Q):
    x1, y1 = P; x2, y2 = Q
    x3 = (x1 * y2 + x2 * y1) * pow(1 + d * x1 * x2 * y1 * y2, p - 2, p) % p
    y3 = (y1 * y2 + x1 * x2) * pow(1 - d * x1 * x2 * y1 * y2, p - 2, p) % p
    return (x3, y3)


def neg(P):
    return ((p - P[0]) % p, P[1])


def mul(s, P):
    Q = (0, 1); R = P
    while s > 0:
        if s & 1: Q = add(Q, R)
        R = add(R, R); s >>= 1
    return Q


def compress(P):
    x, y = P
    return int.to_bytes(y | ((x & 1) << 255), 32, 'little')


def decompress(b):
    y = int.from_bytes(b, 'little'); sign = y >> 255; y &= (1 << 255) - 1
    if y >= p: return None
    u = (y * y - 1) % p; v = (d * y * y + 1) % p
    x = (u * pow(v, 3, p) * pow(u * pow(v, 7, p), (p - 5) // 8, p)) % p
    if v * x * x % p == u: pass
    elif v * x * x % p == (-u) % p: x = x * SQRT_M1 % p
    else: return None
    if x & 1 != sign: x = p - x
    return (x, y)


def genpoint(seed):
    v = hashlib.sha256(seed).digest()
    while True:
        P = decompress(v)
        if P is not None: return P
        v = hashlib.sha256(v).digest()


N = genpoint(b"edwards25519 point generation seed (N)")
M = genpoint(b"edwards25519 point generation seed (M)")

# documented values from BoringSSL spake25519.cc
assert N == (49918732221787544735331783592030787422991506689877079631459872391322455579424,
             54629554431565467720832445949441049581317094546788069926228343916274969994000), "N mismatch"
assert M == (31406539342727633121250288103050113562375374900226415211311216773867585644232,
             21177308356423958466833845032658859666296341766942662650232962324899758529114), "M mismatch"

# table entry i (0..14) -> multiple of P: bits of (i+1) select 2^192/2^128/2^64/2^0
def table_mult(i):
    k = (((i + 1) >> 3 & 1) << 192) | (((i + 1) >> 2 & 1) << 128) | \
        (((i + 1) >> 1 & 1) << 64) | ((i + 1) & 1)
    return k


def gen_msg(password, priv64, alice):
    priv = (int.from_bytes(priv64, 'little') % l) * 8
    P = mul(priv, G)
    ph = hashlib.sha512(password).digest()
    s = int.from_bytes(ph, 'little') % l
    # password scalar hack (unilateral cofactor fix)
    if s & 1: s += l
    if s & 2: s += 2 * l
    if s & 4: s += 4 * l
    mask = mul(s, M if alice else N)
    return compress(add(P, mask)), priv, s, ph


def process_msg(their_msg, my_msg, priv, s, ph, alice, my_name, their_name):
    Qstar = decompress(their_msg)
    peers_mask = mul(s, N if alice else M)
    dh = mul(priv, add(Qstar, neg(peers_mask)))
    dh_enc = compress(dh)
    sha = hashlib.sha512()
    def ulp(data): sha.update(len(data).to_bytes(8, 'little')); sha.update(data)
    if alice:
        ulp(my_name); ulp(their_name); ulp(my_msg); ulp(their_msg)
    else:
        ulp(their_name); ulp(my_name); ulp(their_msg); ulp(my_msg)
    ulp(dh_enc); ulp(ph)
    return sha.digest()


def precomp_entry(point):
    """(y+x, y-x, 2*d*x*y) little-endian — the Java table format."""
    x, y = point
    return (int.to_bytes((y + x) % p, 32, 'little') +
            int.to_bytes((y - x) % p, 32, 'little') +
            int.to_bytes(2 * d * x * y % p, 32, 'little'))


if __name__ == '__main__':
    mode = sys.argv[1]
    if mode == 'selftest':
        # Alice vs Bob, both Python — sanity of the reference itself
        pwd = b'123456'
        pa = bytes(range(64)); pb = bytes(range(64, 128))
        ma, prA, sA, phA = gen_msg(pwd, pa, True)
        mb, prB, sB, phB = gen_msg(pwd, pb, False)
        ka = process_msg(mb, ma, prA, sA, phA, True, b'adb pair client\0', b'adb pair server\0')
        kb = process_msg(ma, mb, prB, sB, phB, False, b'adb pair server\0', b'adb pair client\0')
        print('MSG_A', ma.hex()); print('MSG_B', mb.hex())
        print('KEY_A', ka.hex()); print('KEY_B', kb.hex())
        assert ka == kb, 'selftest failed'
        print('SELFTEST OK')
    elif mode == 'tables':
        # expected Java PRECOMP table bytes for N and M
        for name, P in (('N', N), ('M', M)):
            out = b''.join(precomp_entry(mul(table_mult(i), P)) for i in range(15))
            print(name, out.hex())
    elif mode == 'bob':
        # interact: read json {password, priv, alice_msg} from stdin; emit json
        data = json.loads(sys.stdin.read())
        pwd = bytes.fromhex(data['password']); priv64 = bytes.fromhex(data['priv'])
        alice_msg = bytes.fromhex(data['alice_msg'])
        mb, prB, sB, phB = gen_msg(pwd, priv64, False)
        kb = process_msg(alice_msg, mb, prB, sB, phB, False,
                         b'adb pair server\0', b'adb pair client\0')
        print(json.dumps({'bob_msg': mb.hex(), 'bob_key': kb.hex()}))
    elif mode == 'alice':
        data = json.loads(sys.stdin.read())
        pwd = bytes.fromhex(data['password']); priv64 = bytes.fromhex(data['priv'])
        ma, prA, sA, phA = gen_msg(pwd, priv64, True)
        print(json.dumps({'alice_msg': ma.hex()}))
