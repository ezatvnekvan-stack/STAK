"""Local diagnostic/check CLI. Reads credentials from the container, never prints them."""
import json
import os
import sys
import urllib.request
import urllib.error

def main():
    args=sys.argv[1:];command=args[0] if args else 'status'
    port=int(os.environ.get('STAK_BIND_PORT','8055'))
    token=os.environ.get('STAK_WATCHER_TOKEN','')
    def call(path,method='GET'):
        req=urllib.request.Request(f'http://127.0.0.1:{port}/{path}',method=method,
            headers={'Authorization':'Bearer '+token,'Connection':'close'})
        with urllib.request.urlopen(req,timeout=10) as r:return json.load(r)
    try:
        if command=='status':
            print(json.dumps({'health':call('health'),'diagnostics':call('v1/diagnostics'),'activity':call('v1/status')},indent=2))
        elif command=='check' and len(args)==2:
            import re
            if not re.fullmatch(r'[A-Za-z0-9_-]{1,160}',args[1]):raise ValueError('Invalid rule id')
            print(json.dumps(call('v1/rules/'+args[1]+'/check','POST')))
        else:raise ValueError('Usage: python -m core.admin [status | check RULE_ID]')
    except urllib.error.HTTPError as e:
        print('Watcher HTTP '+str(e.code)+'. Check configuration; credentials were not printed.',file=sys.stderr);raise SystemExit(1)
    except (OSError,ValueError):
        print('Watcher is unavailable or the command is invalid. No credentials were printed.',file=sys.stderr);raise SystemExit(1)

if __name__=='__main__':main()
