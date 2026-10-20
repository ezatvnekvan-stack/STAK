/* Intercepted test fixtures exercise production components; no live service claim. */
const fs=require('fs');
module.exports=async({page,width,theme,assert})=>{
 const go=route=>page.goto('http://127.0.0.1:4173'+route),button=name=>page.getByRole('button',{name,exact:true});
 const shot=async name=>{await page.evaluate(()=>document.fonts.ready);const filename=`collections-${name}-${width}-${theme}.png`,bytes=await page.screenshot({path:`../artifacts/${filename}`});if(width===360 && ['jellyfin','torbox','watchlist'].includes(name))console.log(`STAK_COLLECTIONS_SCREENSHOT ${filename} ${bytes.toString('base64')}`);};
 const fits=async()=>assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true);
 // Add a second title through the actual watchlist mutation, not fake rendered cards.
 await go('/title/movie/tt0133093');await page.getByTestId('watchlist-toggle-button').waitFor();
 if(await page.getByTestId('watchlist-toggle-button').getAttribute('data-active')!=='true')await page.getByTestId('watchlist-toggle-button').click();
 await go('/watchlist');const watch=page.getByTestId('watchlist-grid');await watch.waitFor();
 assert.equal(await watch.evaluate(el=>getComputedStyle(el).gridTemplateColumns.split(' ').length),3);
 assert.equal(await watch.locator('[data-testid^="poster-watchlist-marker"]').count(),0);
 await shot('watchlist');await fits();
 await button('List view').click();assert.equal(await watch.getAttribute('data-layout'),'list');
 await page.reload();await watch.waitFor();await page.waitForFunction(()=>document.querySelector('[data-testid=watchlist-grid]')?.dataset.layout==='list');assert.equal(await watch.getAttribute('data-layout'),'list');
 await page.getByTestId('watchlist-search').fill('no-matching-title-xyz');await page.getByTestId('watchlist-empty').waitFor();
 await button('Clear filters').click();await watch.waitFor();await button('Poster grid').click();
 await button('Filters and sorting').click();await page.getByTestId('watchlist-filters').waitFor();
 await page.getByRole('switch',{name:'Hide watched',exact:true}).click();await shot('watchlist-filters');
 await button('Reset filters').click();await button('Done').click();await page.getByTestId('watchlist-filters').waitFor({state:'hidden'});await watch.waitFor();
 assert.equal(await watch.locator('.collection-more').count(),0,'Poster overflow dots are removed');
 const actionPoster=watch.locator('a[data-quick-actions]').first();
 await actionPoster.focus();await page.keyboard.press('Shift+F10');await button('Remove from watchlist').waitFor();
 await page.waitForFunction(()=>!!document.activeElement?.closest('.poster-action-menu'));
 await page.keyboard.press('Escape');
 await page.waitForFunction(()=>document.activeElement?.matches('a[data-quick-actions][aria-expanded="false"]'));
 await page.keyboard.press('Shift+F10');await button('Remove from watchlist').waitFor();
 await button('Remove from watchlist').click();await page.locator('[data-sonner-toast]').filter({hasText:'Removed from watchlist'}).getByRole('button',{name:'Undo',exact:true}).click();
 await watch.waitFor();
 await page.evaluate(()=>document.documentElement.dataset.compact='true');
 assert.equal(await watch.evaluate(el=>getComputedStyle(el).gridTemplateColumns.split(' ').length),3);
 await page.evaluate(()=>document.documentElement.dataset.compact='false');
 const tasks=[
 {id:1,name:'Crystal.Lake.S01.2160p.WEB-DL.HDR',size:46400000000,download_finished:true,download_present:true,progress:1,download_state:'cached'},
 {id:2,name:'The.Mist.2007.2160p.BluRay.REMUX',size:24110000000,download_finished:true,download_present:true,progress:1,download_state:'completed'},
 {id:3,name:'Fixture.Show.S02E03.1080p.WEB-DL',size:2e9,download_finished:false,download_present:false,progress:.62,download_speed:12000000,download_state:'downloading'},
 {id:4,name:'a'.repeat(40),size:10790000000,download_finished:true,download_present:true,progress:1,download_state:'cached'},
 {id:5,name:'Fixture.Movie.2025.1080p',size:2e9,download_finished:false,download_present:false,progress:0,download_state:'download_error'}
 ].map((item,i)=>({...item,hash:(i?'b':'c').repeat(39)+i,created_at:`2026-10-${String(18-i).padStart(2,'0')}T00:00:00Z`,files:[{id:1,name:item.name+'/Fixture-only.mkv',size:item.size}]}));
 await page.route('https://api.torbox.app/**/torrents/mylist*',route=>route.request().method()==='OPTIONS'?route.fulfill({status:204,headers:{'access-control-allow-origin':'*','access-control-allow-headers':'*','access-control-allow-methods':'*'}}):route.fulfill({contentType:'application/json',headers:{'access-control-allow-origin':'*'},body:JSON.stringify({success:true,data:tasks})}));
 await go('/torbox');await page.getByTestId('library-row-4').waitFor();await fits();
 assert.equal(await page.locator('[data-testid^="library-select-"]').count(),0);
 assert.equal(await page.getByText('Unidentified torrent',{exact:true}).count(),1);
 assert.equal(await page.getByRole('progressbar',{name:'Download progress for Fixture Show',exact:true}).getAttribute('aria-valuenow'),'62');
 await shot('torbox');await button('Select').click();await page.getByTestId('library-select-all').click();
 assert.equal(await page.getByTestId('bulk-selected-count').textContent(),'5');await shot('torbox-selection');
 await page.getByTestId('clear-selection-button').click();assert.equal(await page.getByTestId('bulk-action-bar').count(),0);
 await page.getByTestId('library-tab-active').click();assert.equal(await page.locator('[data-testid^="library-row-"]').count(),1);
 await page.getByTestId('library-tab-all').click();await page.getByTestId('library-search-input').fill('mist');assert.equal(await page.locator('[data-testid^="library-row-"]').count(),1);
 await button('Clear search by name or hash').click();await page.getByTestId('library-row-4').waitFor();
 await page.getByTestId('library-open-0').click();await page.getByTestId('files-sheet').waitFor();
 await page.getByTestId('files-sheet').locator('summary').click();await page.getByText(tasks[0].name,{exact:true}).waitFor();
 await button('Close dialog').click();
 // Connect a TEST-ONLY Jellyfin server through Settings to exercise the real adapter.
 let storageRequests=0;const libraryRequests=[];
 const media=Array.from({length:40},(_,i)=>({Id:`fixture-${i}`,Name:`Fixture ${i%2?'series':'movie'} ${String(i+1).padStart(2,'0')}`,Type:i%2?'Series':'Movie',ProductionYear:2025-i%4,ProviderIds:{Imdb:`tt${4000000+i}`},ImageTags:{Primary:'fixture'},UserData:{Played:i%4===0,PlayedPercentage:i===1?35:0}}));
 await page.route('https://jellyfin.example/**',route=>{
  const request=route.request(),u=new URL(request.url()),json=data=>route.fulfill({contentType:'application/json',headers:{'access-control-allow-origin':'*'},body:JSON.stringify(data)});
  if(request.method()==='OPTIONS')return route.fulfill({status:204,headers:{'access-control-allow-origin':'*','access-control-allow-headers':'*','access-control-allow-methods':'*'}});
  if(u.pathname.includes('/Images/'))return route.fulfill({contentType:'image/svg+xml',headers:{'access-control-allow-origin':'*'},body:'<svg xmlns="http://www.w3.org/2000/svg" width="200" height="300"><rect width="200" height="300" fill="#344849"/><circle cx="100" cy="120" r="60" fill="#7f9688"/><text x="100" y="250" fill="white" text-anchor="middle" font-size="16">TEST ARTWORK</text></svg>'});
  if(u.pathname.endsWith('/AuthenticateByName'))return json({AccessToken:'fixture-only-not-a-secret',User:{Id:'fixture-user',Name:'Fixture user'}});
  if(u.pathname==='/Users/fixture-user')return json({Id:'fixture-user',Name:'Fixture user',Policy:{IsAdministrator:true}});
  if(u.pathname.endsWith('/Views'))return json({Items:[{Id:'movies',Name:'Movies'},{Id:'series',Name:'Series'}]});
  if(u.pathname.endsWith('/Items')){
   const types=u.searchParams.get('IncludeItemTypes');
   if(types==='Movie,Episode' && u.searchParams.get('Fields')==='MediaSources'){storageRequests++;return json({Items:media.slice(0,1).map(i=>({...i,MediaSources:[{Id:i.Id,Size:1e9}]})),TotalRecordCount:1});}
   if(u.searchParams.get('Limit')==='1')return json({Items:[],TotalRecordCount:20});
   if(u.searchParams.get('IsPlayed')==='true' && u.searchParams.get('SortBy')==='DatePlayed')return json({Items:[],TotalRecordCount:0});
   libraryRequests.push(Object.fromEntries(u.searchParams));let items=media;
   if(u.searchParams.get('ParentId'))items=items.filter(i=>i.Type===(u.searchParams.get('ParentId')==='movies'?'Movie':'Series'));
   if(u.searchParams.get('SearchTerm'))items=items.filter(i=>i.Name.toLowerCase().includes(u.searchParams.get('SearchTerm').toLowerCase()));
   if(u.searchParams.has('IsPlayed'))items=items.filter(i=>i.UserData.Played===(u.searchParams.get('IsPlayed')==='true'));
   const start=Number(u.searchParams.get('StartIndex'))||0,limit=Number(u.searchParams.get('Limit'))||36;
   return json({Items:items.slice(start,start+limit),TotalRecordCount:items.length});
  }
  if(u.pathname==='/Sessions'||u.pathname==='/ScheduledTasks')return json([]);
  return json({Items:[],TotalRecordCount:0});
 });
 await go('/account?section=jellyfin');const group=page.getByTestId('settings-group-jellyfin'),disclosure=group.locator('button[aria-controls]').first();
 if(await disclosure.getAttribute('aria-expanded')!=='true')await disclosure.click();
 await page.getByTestId('jellyfin-url-input').fill('https://jellyfin.example');await page.getByLabel('Jellyfin username',{exact:true}).fill('Fixture user');await page.getByLabel('Jellyfin password',{exact:true}).fill('fixture-only');
 await page.getByTestId('jellyfin-test-button').click();await page.getByTestId('jellyfin-user-0').click();await page.getByTestId('jellyfin-reconnect-button').waitFor();
 await go('/library');const jelly=page.getByTestId('jellyfin-collection');await jelly.locator('a').first().waitFor();await fits();assert.equal(await jelly.locator('.collection-more').count(),0,'Jellyfin posters have no overflow dots');
 assert.equal(storageRequests,0,'No expensive storage scan before Server details is opened');
 assert.equal(await jelly.evaluate(el=>getComputedStyle(el).gridTemplateColumns.split(' ').length),3);
 assert.ok((await jelly.boundingBox()).y<520,'Jellyfin artwork is visible without a large dashboard');
 assert.equal(await jelly.getByLabel('Watched',{exact:true}).first().evaluate(el=>getComputedStyle(el).color),'rgb(255, 255, 255)','Watched marker stays readable over artwork in every theme');await shot('jellyfin');await button('Load more').click();await page.getByText('40 titles loaded · 40 server items',{exact:true}).waitFor();
 await button('List view').click();assert.equal(await jelly.getAttribute('data-layout'),'list');await shot('jellyfin-list');
 await button('Poster grid').click();await button('Filters and sorting').click();await page.getByRole('combobox',{name:'Watch status',exact:true}).click();await page.getByRole('option',{name:'Unwatched',exact:true}).click();await button('Done').click();
 await page.waitForFunction(()=>document.querySelector('[data-testid="jellyfin-collection"]')?.querySelectorAll('a').length===30);
 assert.ok(libraryRequests.some(r=>r.IsPlayed==='false'),'Watch filters reach the Jellyfin API');
 await page.locator('.collection-active-chip').click();await jelly.locator('a').first().waitFor();
 await page.getByLabel('Search this library',{exact:true}).fill('movie 03');await page.waitForTimeout(400);await page.getByText('Fixture movie 03',{exact:true}).waitFor();await page.waitForFunction(()=>document.querySelector('[data-testid="jellyfin-collection"]')?.querySelectorAll('a').length===1);
 await button('Server details').click();await page.getByTestId('jellyfin-server-details').waitFor();await page.getByText('1 GB',{exact:true}).waitFor();assert.ok(storageRequests>0);
 assert.equal(await page.getByRole('link',{name:'Remote control',exact:true}).getAttribute('href'),'/remote');await shot('jellyfin-server');await button('Done').click();
 await button('Clear search this library').click();await jelly.locator('a').first().waitFor();
 await page.evaluate(()=>document.documentElement.dataset.compact='true');assert.equal(await jelly.evaluate(el=>getComputedStyle(el).gridTemplateColumns.split(' ').length),3);await page.evaluate(()=>document.documentElement.dataset.compact='false');
 await page.evaluate(()=>scrollTo(0,document.documentElement.scrollHeight));const last=await jelly.locator('a').last().boundingBox(),nav=await page.getByRole('navigation',{name:'Main navigation'}).boundingBox();assert.ok(last.y+last.height<=nav.y,'Final Jellyfin card clears taskbar');await fits();
 fs.mkdirSync('../validation',{recursive:true});fs.writeFileSync(`../validation/collections-${width}-${theme}.json`,JSON.stringify({width,theme,testFixturesOnly:true,watchlistSearch:true,watchlistGridList:true,watchlistUndo:true,compact3Columns:true,torboxSelection:true,torboxProgress:true,originalFilename:true,jellyfinRealAdapter:true,jellyfinPagination:true,jellyfinServerFilters:true,jellyfinDeferredStorage:true,serverControlsPreserved:true,posterTopUnder520:true,navClearance:true,noOverflow:true},null,2));
};
