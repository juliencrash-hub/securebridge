#!/usr/bin/env python3
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors=[]
def must(cond,msg):
    if not cond: errors.append(msg)
def read(rel): return (ROOT/rel).read_text(encoding='utf-8')

app=read('web/assets/app.js')
api=read('server/api.php')
web_index=read('web/index.html')
web_ht=read('web/.htaccess')

must("window.SECUREBRIDGE_DEPLOYMENT" in app, 'Config de déploiement absente du client')
must('deploymentId:DEPLOYMENT_ID' in app, 'deploymentId absent des appels API web')
must("redirect:'error'" in app, 'Redirections fetch web non bloquées')
must('PAIR_REPLAY_MAX' in app and 'PAIR_MAX_AGE_MS' in app and 'pairProof' in app and 'phase:2' in app, 'Anti-rejeu pairing incomplet')
must('MAX_DR_SKIP' in app and 'replayIds' in app and 'prepareSendRatchet' in app and 'receiveDhRatchet' in app, 'Double Ratchet/anti-rejeu incomplet')
must('FOREGROUND_PEEK_SLOTS = 128' in app, 'Padding foreground 128 absent')
m=re.search(r'function expiryOptions\(\)\{return `([^`]+)`', app)
must(m is not None, 'expiryOptions introuvable')
if m:
    opts=m.group(1)
    must('value="3600"' in opts and 'value="86400"' in opts and 'value="604800"' in opts, 'Durées 1h/24h/7j absentes')
    for bad in ('value="60"','value="300"','value="600"','value="1800"'):
        must(bad not in opts, f'Durée éphémère < 1h présente: {bad}')
must('config.local.js' in web_index, 'config.local.js non chargé')
must("script-src 'self'" in web_ht and "connect-src 'self'" in web_ht, 'CSP web insuffisante')
must("hash_equals($deploymentId" in api, 'Backend non lié au deploymentId')
must("MAX_PEEK_SLOTS = 128" in api, 'Backend peek batch 128 absent')
must("$action === 'put'" in api and "$action === 'take'" in api and "$action === 'peek_many'" in api, 'PUT/TAKE/PEEK backend absent')
must('pair_create' not in api and 'pair_read' not in api and 'pair_respond' not in api, 'Ancien pairing serveur présent')

if errors:
    print('SECURITY STATIC TESTS: FAIL')
    for e in errors: print(' -', e)
    sys.exit(1)
print('SECURITY STATIC TESTS: OK')
