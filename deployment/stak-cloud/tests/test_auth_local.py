"""Optional local test against official GoTrue in the isolated DB container namespace.
Never use a production service/account. Tokens/passwords are not printed.
"""
import hashlib,json,os,subprocess,unittest,uuid
DOCKER=os.environ.get('DOCKER_COMMAND','docker')
CONTAINER=os.environ.get('STAK_TEST_POSTGRES','stak-account-db-tests')
def request(path,payload=None):
    command=[DOCKER,'exec',CONTAINER,'wget','-qO-','--header','Content-Type: application/json']
    if payload is not None:command+=['--post-data',json.dumps(payload)]
    command+=['http://127.0.0.1:9999'+path]
    reply=subprocess.run(command,capture_output=True,text=True)
    if reply.returncode:raise RuntimeError('Local Auth request rejected')
    return json.loads(reply.stdout) if reply.stdout else {}

class Auth(unittest.TestCase):
    def test_username_only_registration_login_refresh_and_local_logout(self):
        settings=request('/settings');self.assertTrue(settings['mailer_autoconfirm']);self.assertTrue(settings['external']['email']);self.assertFalse(settings['external']['google'])
        name='fixture_'+uuid.uuid4().hex[:12]
        identity=hashlib.sha256(name.encode()).hexdigest()+'@accounts.stak.invalid'
        user=request('/signup',{'email':identity,'password':'synthetic-password','data':{'stak_username':name}})
        self.assertTrue(user['access_token']);self.assertTrue(user['refresh_token']);self.assertEqual(identity,user['user']['email'])
        logged=request('/token?grant_type=password',{'email':identity,'password':'synthetic-password'})
        self.assertEqual(user['user']['id'],logged['user']['id'])
        refreshed=request('/token?grant_type=refresh_token',{'refresh_token':logged['refresh_token']})
        self.assertEqual(user['user']['id'],refreshed['user']['id'])
        with self.assertRaises(RuntimeError):request('/token?grant_type=password',{'email':identity,'password':'incorrect-password'})
        command=[DOCKER,'exec',CONTAINER,'wget','-qO-','--header','Authorization: Bearer '+refreshed['access_token'],'--post-data','{}','http://127.0.0.1:9999/logout?scope=local']
        result=subprocess.run(command,capture_output=True,text=True);self.assertEqual(0,result.returncode)

if __name__=='__main__':unittest.main(verbosity=2)
