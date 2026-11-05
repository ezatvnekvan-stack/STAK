"""Contract tests against isolated postgres:16-alpine, no media or real accounts."""
from pathlib import Path
import json,os,subprocess,unittest,uuid

DOCKER=os.environ.get('DOCKER_COMMAND','docker')
CONTAINER=os.environ.get('STAK_TEST_POSTGRES','stak-account-db-tests')
def sql(statement,role=None,uid=None,check=True):
    prefix=''
    if role:prefix+='set role '+role+';'
    if uid:prefix+="set request.jwt.claim.sub='"+uid+"';"
    p=subprocess.run([DOCKER,'exec','-i',CONTAINER,'psql','-X','-q','-t','-A','-U','postgres','-v','ON_ERROR_STOP=1'],input=prefix+statement,text=True,capture_output=True)
    if check and p.returncode:raise AssertionError(p.stderr)
    return p

class Backups(unittest.TestCase):
    one='00000000-0000-4000-8000-000000000001'
    two='00000000-0000-4000-8000-000000000002'
    blob={'format':1,'nonce':'AAAAAAAAAAAAAAAA','data':'AAAAAA=='}
    def setUp(self):sql('truncate stak_private.backup_versions,stak_private.backup_heads;')
    def write(self,uid,expected=0,op=None,blob=None,fingerprint=None):
        value=json.dumps(blob or self.blob).replace("'","''")
        query="select public.stak_backup_write(%d,'%s','%s'::jsonb,'%s'::uuid);"%(expected,fingerprint or 'a'*64,value,op or uuid.uuid4())
        return json.loads(sql(query,'authenticated',uid).stdout)
    def read(self,uid):return json.loads(sql('select public.stak_backup_read();','authenticated',uid).stdout)
    def test_anonymous_cannot_read_or_write(self):
        self.assertNotEqual(0,sql('select public.stak_backup_read();','anon',check=False).returncode)
        self.assertNotEqual(0,sql("select public.stak_backup_write(0,'"+'a'*64+"','{}',gen_random_uuid());",'anon',check=False).returncode)
    def test_no_user_claim_is_rejected(self):self.assertNotEqual(0,sql('select public.stak_backup_read();','authenticated',check=False).returncode)
    def test_tables_are_not_directly_available(self):self.assertNotEqual(0,sql('select * from stak_private.backup_versions;','authenticated',self.one,False).returncode)
    def test_accounts_cannot_read_each_other(self):
        self.write(self.one);self.assertEqual(0,self.read(self.two)['revision']);self.assertEqual(1,self.read(self.one)['revision'])
    def test_compare_and_swap_rejects_stale_empty_install(self):
        self.write(self.one);self.assertTrue(self.write(self.one)['conflict']);self.assertEqual(1,self.read(self.one)['revision'])
    def test_idempotency_replays_ack_without_new_version(self):
        op=str(uuid.uuid4());one=self.write(self.one,op=op);two=self.write(self.one,op=op)
        self.assertEqual(one,two);self.assertEqual(1,self.read(self.one)['revision'])
    def test_same_operation_cannot_be_reused_for_different_content(self):
        op=str(uuid.uuid4());self.write(self.one,op=op)
        with self.assertRaises(AssertionError):self.write(self.one,op=op,fingerprint='b'*64)
    def test_five_prior_copies_are_retained_without_cross_account_deletion(self):
        self.write(self.two)
        for revision in range(7):self.write(self.one,revision)
        count=int(sql("select count(*) from stak_private.backup_versions where user_id='"+self.one+"';").stdout)
        self.assertEqual(5,count);self.assertEqual(7,self.read(self.one)['revision']);self.assertEqual(1,self.read(self.two)['revision'])
    def test_invalid_envelope_does_not_create_a_backup(self):
        with self.assertRaises(AssertionError):self.write(self.one,blob={'format':2,'nonce':'bad','data':'bad'})
        self.assertEqual(0,self.read(self.one)['revision'])

if __name__=='__main__':unittest.main(verbosity=2)
