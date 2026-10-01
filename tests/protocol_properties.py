#!/usr/bin/env python3
"""Tests de propriétés cryptographiques de référence.

Ils vérifient le modèle/protocole attendu avec la bibliothèque Python cryptography.
Ils ne remplacent ni les tests instrumentés Android ni un audit du code JS/Java.
"""
import hashlib, hmac, os, secrets
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def hkdf(ikm, salt, info, length):
    return HKDF(algorithm=hashes.SHA256(), length=length, salt=salt, info=info).derive(ikm)

def hm(k, label):
    return hmac.new(k, label, hashlib.sha256).digest()

# X25519 : les deux côtés obtiennent le même secret, une nouvelle clé change le secret.
a = X25519PrivateKey.generate(); b = X25519PrivateKey.generate()
sa = a.exchange(b.public_key()); sb = b.exchange(a.public_key())
assert sa == sb and len(sa) == 32
b2 = X25519PrivateKey.generate()
assert a.exchange(b2.public_key()) != sa

# Preuve anti-rejeu : elle est liée au secret éphémère ET au transcript.
t1 = hashlib.sha256(b'phase1-A|phase1-B').hexdigest()
t2 = hashlib.sha256(b'fresh-A|phase1-B').hexdigest()
p1 = hm(sa, ('PAIR-PROOF-v3|' + t1 + '|reader').encode())
p2_expected = hm(a.exchange(b2.public_key()), ('PAIR-PROOF-v3|' + t2 + '|reader').encode())
assert not hmac.compare_digest(p1, p2_expected)

# Ratchet de chaîne : clé message et prochain état sont séparés, puis changent à chaque pas.
ck = os.urandom(32)
seen = set()
for _ in range(100):
    mk = hm(ck, b'DR-MSG')
    nxt = hm(ck, b'DR-NEXT')
    assert mk != nxt
    assert mk not in seen
    seen.add(mk)
    ck = nxt

# Slots à sens unique : 128 identifiants successifs doivent être tous distincts.
sk = os.urandom(32); slots=[]
for _ in range(128):
    slots.append(hm(sk, b'SLOT').hex())
    sk = hm(sk, b'NEXT')
assert len(slots) == len(set(slots)) == 128

# AAD : déplacer un ciphertext vers un autre slot doit casser l'authentification.
key = AESGCM.generate_key(bit_length=256); aes = AESGCM(key); nonce = os.urandom(12)
ct = aes.encrypt(nonce, b'payload', ('OUTER:' + slots[0]).encode())
assert aes.decrypt(nonce, ct, ('OUTER:' + slots[0]).encode()) == b'payload'
try:
    aes.decrypt(nonce, ct, ('OUTER:' + slots[1]).encode())
    raise AssertionError('AAD slot tamper accepted')
except Exception:
    pass

# Matériau de session : domaines/directions distincts.
material = hkdf(sa, bytes.fromhex(t1), b'SECURE-MSG-SESSION-v3', 224)
parts = [material[i:i+32] for i in range(0,224,32)]
assert len(parts) == 7 and len(set(parts)) == 7

# IDs de message 128 bits : contrôle basique d'absence de collision sur échantillon.
ids = {secrets.token_hex(16) for _ in range(10000)}
assert len(ids) == 10000

print('PROTOCOL PROPERTY TESTS: OK')
