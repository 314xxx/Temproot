#!/bin/bash
set -e
cd /workspace/.spatest
JAR=/workspace/app/libs/spake2-java-1.0.0.jar
CP="$JAR:classes"
PWD_HEX=$(python3 -c "print('313233343536')")
PRIVA=$(python3 -c "print(''.join(f'{i:02x}' for i in range(64)))")
PRIVB=$(python3 -c "print(''.join(f'{(i+64):02x}' for i in range(64)))")
# Step 1: Java alice generates msg
A_OUT=$(printf 'password=%s\npriv=%s\n' "$PWD_HEX" "$PRIVA" | java -cp "$CP" io.github.muntashirakon.crypto.spake2.Spake2CrossTest alice 2>/dev/null)
A_MSG=$(echo "$A_OUT" | grep '^my_msg=' | cut -d= -f2)
echo "ALICE_MSG=$A_MSG"
# Step 2: Python bob processes alice msg, produces bob msg + key
B_JSON=$(printf '{"password":"%s","priv":"%s","alice_msg":"%s"}' "$PWD_HEX" "$PRIVB" "$A_MSG" | python3 ref_spake2.py bob)
B_MSG=$(echo "$B_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['bob_msg'])")
B_KEY=$(echo "$B_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['bob_key'])")
echo "BOB_MSG=$B_MSG"
echo "BOB_KEY=$B_KEY"
# Step 3: Java alice processes bob msg -> key
A_OUT2=$(printf 'password=%s\npriv=%s\npeer_msg=%s\n' "$PWD_HEX" "$PRIVA" "$B_MSG" | java -cp "$CP" io.github.muntashirakon.crypto.spake2.Spake2CrossTest alice 2>/dev/null)
A_KEY=$(echo "$A_OUT2" | grep '^my_key=' | cut -d= -f2)
echo "ALICE_KEY=$A_KEY"
if [ "$A_KEY" = "$B_KEY" ]; then echo "RESULT: MATCH"; else echo "RESULT: MISMATCH"; fi
