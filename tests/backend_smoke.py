#!/usr/bin/env python3
import json, os, secrets, urllib.request, urllib.error

API=os.environ.get('API','http://127.0.0.1:8799/api.php')
DEPLOYMENT_ID=os.environ.get('DEPLOYMENT_ID','test-deployment-00000001')
slot=secrets.token_hex(32)
blob='opaque-test-'+secrets.token_hex(24)

def call(action, **payload):
    body=json.dumps({'deploymentId':DEPLOYMENT_ID,'action':action,**payload}).encode()
    req=urllib.request.Request(API,data=body,headers={'Content-Type':'application/json'},method='POST')
    with urllib.request.urlopen(req,timeout=5) as r:
        return json.load(r)

assert call('put',slot=slot,blob=blob,ttl=600)['ok'] is True
peek=call('peek_many',slots=[secrets.token_hex(32),slot,secrets.token_hex(32)])
assert peek['ok'] is True and peek['index']==1, peek
take=call('take',slot=slot)
assert take['ok'] is True and take['blob']==blob, take
again=call('take',slot=slot)
assert again['ok'] is True and again['blob'] is None, again
print('BACKEND SMOKE: OK')
