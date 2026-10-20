import {useMetadataQuery} from './metadataQuery';
import {metadataDefaults} from './metadata/settings';
export function useMetadataSources(){const query=useMetadataQuery('/metadata/settings');return {...query,settings:query.data?.settings || metadataDefaults,status:query.data?.status || {}};}
