/* Production components, intercepted fixtures only; not a live Jellyfin/TorBox test. */
const fs=require('fs');
module.exports=async({page,width,theme,assert})=>{
 const go=route=>page.goto('http://127.0.0.1:4173'+route),button=name=>page.getByRole('button',{name,exact:true});
 const shot=async name=>{await page.evaluate(()=>document.fonts.ready);const filename=`details-refresh-${name}-${width}-${theme}.png`,bytes=await page.screenshot({path:`../artifacts/${filename}`});if(width===360)console.log(`STAK_DETAILS_SCREENSHOT ${filename} ${bytes.toString('base64')}`);};
 const fits=async()=>assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true);
 let addRequests=0,missingTitle=false;
 await page.route('https://jellyfin.example/**',async route=>{
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers:{'access-control-allow-origin':'*','access-control-allow-headers':'*','access-control-allow-methods':'*'}});
  const u=new URL(route.request().url()),json=data=>route.fulfill({contentType:'application/json',headers:{'access-control-allow-origin':'*'},body:JSON.stringify(data)});
  if(u.pathname.includes('/Shows/') && u.pathname.endsWith('/Episodes'))return json({Items:[],TotalRecordCount:0});
  if(u.pathname.includes('/Items') && missingTitle)return json({Items:[],TotalRecordCount:0});
  if(u.pathname.includes('/Items'))return json({Items:[{Id:u.searchParams.get('IncludeItemTypes')==='Movie'?'watched-movie':'watched-show',Type:u.searchParams.get('IncludeItemTypes')==='Movie'?'Movie':'Series',Name:'Test title',ProviderIds:{Imdb:u.searchParams.get('IncludeItemTypes')==='Movie'?'tt0133093':'tt0903747'},UserData:{Played:true,LastPlayedDate:new Date(Date.now()+60000).toISOString()}}],TotalRecordCount:1});
  if(u.pathname==='/Sessions')return json([]);return json({});
 });
 await page.route('**/*torrentio*/stream/**',route=>route.fulfill({contentType:'application/json',headers:{'access-control-allow-origin':'*'},body:JSON.stringify({streams:[
  {infoHash:'d'.repeat(40),name:'Torrentio 4K',title:'Test.title.Prologue.2025.2160p.ProRes\n👤 57 💾 39.32 GB',behaviorHints:{}},
  {infoHash:'e'.repeat(40),name:'Torrentio 1080p',title:'Test.title.2026.1080p.WEB-DL\n👤 3446 💾 1.59 GB',behaviorHints:{}},
  {infoHash:'f'.repeat(40),name:'Torrentio 720p',title:'Test.title.2026.720p.WEB-DL\n👤 100 💾 0.90 GB',behaviorHints:{}}
 ]})}));
 await page.route('https://api.torbox.app/**',async route=>{
  if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers:{'access-control-allow-origin':'*','access-control-allow-headers':'*','access-control-allow-methods':'*'}});
  const u=new URL(route.request().url());if(u.pathname.endsWith('/createtorrent'))addRequests++;
  return route.fulfill({contentType:'application/json',headers:{'access-control-allow-origin':'*'},body:JSON.stringify({success:true,data:u.pathname.endsWith('/checkcached')?['d','e','f'].map(x=>({hash:x.repeat(40)})):u.pathname.endsWith('/mylist')?[]:{email:'fixture@example.invalid'}})});
 });
 await go('/title/series/tt0903747');await page.getByTestId('mark-unwatched-button').waitFor();
 await page.waitForFunction(()=>document.querySelector('[aria-label="Series completion"]')?.getAttribute('aria-valuenow')==='100');
 assert.equal(await page.getByTestId('mark-unwatched-button').count(),1);assert.equal(await page.getByTestId('mark-watched-button').count(),0);assert.equal(await page.getByTestId('mark-season-watched-button').count(),0);assert.equal(await page.getByTestId('jellyfin-title-watched').count(),0);assert.equal(await page.getByTestId('tracking-details').isVisible(),true);assert.equal(await page.getByTestId('tracking-details').locator('summary').count(),0);assert.equal(await page.locator('.episode-watched-overlay').count(),12);await fits();const railBox=await page.getByTestId('episode-rail').boundingBox(),third=await page.getByTestId('episode-chip-1-3').boundingBox();assert.ok(third.x+third.width<=railBox.x+railBox.width+1,'Three episode cards fit across');await shot('series');
 const progressStyles=await page.locator('.series-completion').evaluate(el=>[el.querySelector('.detail-progress-heading'),el.querySelector('.detail-progress-info')].map(node=>{const css=getComputedStyle(node);return [css.fontFamily,css.fontSize,css.fontWeight,css.lineHeight,css.color];}));
 assert.deepEqual(progressStyles[0],progressStyles[1],'Watched count, percentage and remaining share one typographic style');
 const episodeSize=await page.locator('.episode-thumb').first().evaluate(el=>{const box=el.getBoundingClientRect();return {width:box.width,height:box.height};});
 assert.ok(Math.abs(episodeSize.width/episodeSize.height-4/3)<.02,'Larger episode artwork uses a 4:3 frame while three cards fit');
 await page.getByTestId('episode-chip-1-2').click();await page.getByTestId('episode-details-dialog').waitFor();await page.getByTestId('episode-mark-unwatched-button').waitFor();await shot('episode');await button('Close dialog').click();
 await go('/title/movie/tt0133093');await page.getByTestId('mark-unwatched-button').waitFor();await page.getByTestId('mark-unwatched-button').waitFor();await fits();await shot('movie');
 await page.getByTestId('open-library-picker').click();await page.getByTestId('stream-row-2').waitFor();
 assert.equal(await page.locator('.source-suggestion').count(),1);assert.ok((await page.getByTestId('stream-row-0').textContent()).includes('1080p'),'A full matching title ranks above a 4K prologue');
 assert.ok((await page.getByTestId('stream-row-2').textContent()).includes('Check title match'));
 const headerBefore=await page.getByTestId('source-title-header').boundingBox(),refresh=await page.getByTestId('streams-refresh-button').boundingBox(),close=await button('Close dialog').boundingBox();assert.ok(refresh.width>=44 && refresh.height>=44,'Refresh retains a 44px touch target');assert.ok(refresh.y>=headerBefore.y && refresh.y+refresh.height<=headerBefore.y+headerBefore.height,'Refresh stays inside identity header');assert.ok(refresh.y>=close.y+close.height || refresh.x+refresh.width<=close.x || refresh.x>=close.x+close.width,'Refresh does not overlap Close');await page.locator('.source-body').evaluate(el=>el.scrollTop=el.scrollHeight);const headerAfter=await page.getByTestId('source-title-header').boundingBox();assert.ok(Math.abs(headerBefore.y-headerAfter.y)<1,'Identity header remains visible while sources scroll');await page.locator('.source-body').evaluate(el=>el.scrollTop=0);await fits();await shot('sources');
 await button('Filters and sorting').click();await page.getByTestId('source-filter-sheet').waitFor();await page.getByTestId('stream-filter-max-size').waitFor();await shot('filters');await button('Done').click();await page.getByTestId('source-filter-sheet').waitFor({state:'hidden'});
 await page.getByTestId('add-to-torbox-button-0').click();await page.getByTestId('add-to-torbox-button-0').getByText('Open TorBox',{exact:true}).waitFor();assert.equal(addRequests,1);await shot('added');
 await button('Close dialog').click();await page.evaluate(()=>scrollTo(0,document.documentElement.scrollHeight));const last=page.locator('.title-page .stak-credits').last();if(await last.count()){const box=await last.boundingBox(),dock=await page.getByTestId('title-action-bar').boundingBox();assert.ok(box.y+box.height<dock.y,'Final content clears the source dock');}
 missingTitle=true;await go('/title/series/tt1234567');await page.getByLabel('Jellyfin · Not in library',{exact:true}).waitFor();assert.equal(await page.getByText('Checking watched state…',{exact:true}).count(),0);assert.equal(await page.getByTestId('tracking-details').isVisible(),true);await shot('not-in-library');
 assert.equal(await page.locator('.stak-ambient').count(),1);
 fs.mkdirSync('../validation',{recursive:true});fs.writeFileSync(`../validation/details-refresh-${width}-${theme}.json`,JSON.stringify({width,theme,testFixturesOnly:true,jellyfinPlayedWithoutRuntime:true,seriesCompletion:100,individualEpisodeState:true,parentFlagWithoutEpisodeRows:true,trackingDetailsAlwaysVisible:true,noSpuriousChecking:true,matchBeforeResolution:true,prologueNotRecommended:true,stickySourceHeader:true,advancedFiltersReachable:true,realTorBoxMutation:true,noHorizontalOverflow:true,oneAmbient:true},null,2));
};
