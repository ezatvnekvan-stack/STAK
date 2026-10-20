import React from 'react';
import { act, mount, until } from '../testUtils';
import { metadataOptions,useMetadataQuery } from './metadataQuery';
import { api } from './api';
jest.mock('./api',()=>({api:{get:jest.fn()}}));
function Consumer(){const {data}=useMetadataQuery('/meta/title-logo',{imdb_id:'tt123',type:'series'});return <span>{data?.logo || 'Loading'}</span>;}
test('two presentation consumers share one request and a fresh cache survives remount',async()=>{
  api.get.mockResolvedValue({data:{logo:'real-provider-result'}});
  const view=await mount(<><Consumer/><Consumer/></>);
  try{await until(()=>view.container.textContent.includes('real-provider-result'));expect(api.get).toHaveBeenCalledTimes(1);await act(async()=>{await view.client.fetchQuery({...metadataOptions('/meta/title-logo',{imdb_id:'tt123',type:'series'}),queryFn:()=>{throw new Error('Must use fresh cache');},staleTime:600000});});}finally{await view.close();jest.clearAllMocks();}
});
