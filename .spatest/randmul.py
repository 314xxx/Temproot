#!/usr/bin/env python3
"""生成随机 limbs 输入，喂给 Java RandomMulTest，再对比大整数参考结果。"""
import random, subprocess, sys

p = 2**255 - 19
W = [0, 26, 51, 77, 102, 128, 153, 179, 204, 230]


def limbs_val(t):
    v = 0
    for i in range(10):
        v += t[i] * (1 << W[i])
    return v


def enc(v):
    return int.to_bytes(v % p, 32, 'little').hex()


random.seed(int(sys.argv[1]) if len(sys.argv) > 1 else 42)
N = 500
cases = []
for _ in range(N):
    # 模拟 fe_add/fe_sub 输出：limbs 在 ±1.65*2^26 内（偶）/±1.65*2^25（奇）
    t = [random.randint(-int(1.65 * 2**26), int(1.65 * 2**26)) if i % 2 == 0
         else random.randint(-int(1.65 * 2**25), int(1.65 * 2**25)) for i in range(10)]
    g = [random.randint(-int(1.65 * 2**26), int(1.65 * 2**26)) if i % 2 == 0
         else random.randint(-int(1.65 * 2**25), int(1.65 * 2**25)) for i in range(10)]
    cases.append((t, g))

stdin = "\n".join(
    ",".join(str(x) for x in t) + " " + ",".join(str(x) for x in g) for t, g in cases
)
out = subprocess.run(
    ["java", "-cp", "/workspace/app/libs/spake2-java-1.0.0.jar:/workspace/.spatest/classes",
     "io.github.muntashirakon.crypto.spake2.RandomMulTest"],
    input=stdin, capture_output=True, text=True).stdout.split()

bad = 0
for idx, ((t, g), got) in enumerate(zip(cases, out)):
    want = enc(limbs_val(t) * limbs_val(g))
    if got != want:
        bad += 1
        if bad <= 3:
            print(f"CASE {idx} MISMATCH")
            print("  t =", t)
            print("  g =", g)
            print("  got  =", got)
            print("  want =", want)
print(f"total={N} bad={bad}")
