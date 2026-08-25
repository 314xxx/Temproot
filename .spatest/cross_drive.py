#!/usr/bin/env python3
"""Randomized cross-test: Java SPAKE2 (jar/classes) vs Python BoringSSL reference.

Direction 1 (real-world): Java=Alice(client, app) <-> Python=Bob(server, adbd)
Direction 2:               Python=Alice <-> Java=Bob
"""
import json, os, random, string, subprocess, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ref_spake2 as ref

CP = sys.argv[1] if len(sys.argv) > 1 else 'classes-src'
MAIN = 'io.github.muntashirakon.crypto.spake2.Spake2CrossTest'


def run_java(role, password_hex, priv_hex, peer_msg=None):
    inp = f'password={password_hex}\npriv={priv_hex}\n'
    if peer_msg:
        inp += f'peer_msg={peer_msg}\n'
    r = subprocess.run(['java', '-cp', CP, MAIN, role], input=inp,
                       capture_output=True, text=True, cwd=os.path.dirname(
                           os.path.abspath(__file__)) or '.')
    out = {}
    for line in r.stdout.splitlines():
        if line.startswith('my_msg='):
            out['msg'] = line[7:]
        elif line.startswith('my_key='):
            out['key'] = line[7:]
    if r.returncode != 0 or ('key' not in out and peer_msg):
        out['stderr'] = r.stderr
    return out


def rnd_hex(n):
    return ''.join(f'{random.randrange(256):02x}' for _ in range(n))


fail = 0
total = 0
N = int(sys.argv[2]) if len(sys.argv) > 2 else 30
for i in range(N):
    # random pairing-code-like password
    pwd = ''.join(random.choice(string.digits) for _ in range(6)).encode()
    pwd_hex = pwd.hex()
    priv_a = rnd_hex(64)
    priv_b = rnd_hex(64)

    # Direction 1: Java Alice <-> Python Bob
    a = run_java('alice', pwd_hex, priv_a)
    if 'msg' not in a:
        print(f'[{i}] D1 java alice gen failed: {a.get("stderr", "?")[:300]}'); fail += 1; total += 1; continue
    mb, prB, sB, phB = ref.gen_msg(pwd, bytes.fromhex(priv_b), False)
    kb = ref.process_msg(bytes.fromhex(a['msg']), mb, prB, sB, phB, False,
                         b'adb pair server\0', b'adb pair client\0')
    a2 = run_java('alice', pwd_hex, priv_a, mb.hex())
    total += 1
    if a2.get('key') != kb.hex():
        fail += 1
        print(f'[{i}] D1 MISMATCH pwd={pwd_hex} privA={priv_a[:16]}.. signbitA={int(a["msg"][-2:],16)>>7}')
        if 'stderr' in a2:
            print('   java stderr:', a2['stderr'][:300])

    # Direction 2: Python Alice <-> Java Bob
    ma, prA, sA, phA = ref.gen_msg(pwd, bytes.fromhex(priv_a), True)
    b = run_java('bob', pwd_hex, priv_b)
    if 'msg' not in b:
        print(f'[{i}] D2 java bob gen failed: {b.get("stderr", "?")[:300]}'); fail += 1; total += 1; continue
    b2 = run_java('bob', pwd_hex, priv_b, ma.hex())
    ka = ref.process_msg(bytes.fromhex(b['msg']), ma, prA, sA, phA, True,
                         b'adb pair client\0', b'adb pair server\0')
    total += 1
    if b2.get('key') != ka.hex():
        fail += 1
        print(f'[{i}] D2 MISMATCH pwd={pwd_hex} privB={priv_b[:16]}.. signbitB={int(b["msg"][-2:],16)>>7}')
        if 'stderr' in b2:
            print('   java stderr:', b2['stderr'][:300])

print(f'TOTAL={total} FAIL={fail}')
sys.exit(1 if fail else 0)
