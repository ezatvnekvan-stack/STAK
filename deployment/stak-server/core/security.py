"""Small shared guards. No credentials or provider URLs enter diagnostics."""
import re
from urllib.parse import urlsplit

class Redactor:
    def __init__(self, secrets=()):
        self.secrets = sorted({str(s) for s in secrets if s and len(str(s)) > 3}, key=len, reverse=True)

    def __call__(self, text):
        value = str(text)
        for secret in self.secrets:
            value = value.replace(secret, "[hidden]")
        value = re.sub(r'https?://[^\s<>"\']+', '[url]', value, flags=re.I)
        value = re.sub(r'(?i)(bearer\s+|(?:api[_-]?key|token|password|authorization)\s*[=:]\s*)[^\s,;]+', r'\1[hidden]', value)
        return ''.join(c if ord(c) >= 32 or c == '\n' else ' ' for c in value)[:1500]

def service_url(raw):
    value = str(raw).strip().rstrip('/')
    try:
        u = urlsplit(value)
        if u.scheme not in ('http', 'https') or not u.hostname or u.username or u.password or u.fragment:
            raise ValueError
        _ = u.port
    except ValueError:
        raise ValueError('Invalid service URL; use HTTP(S) without embedded credentials') from None
    return value
