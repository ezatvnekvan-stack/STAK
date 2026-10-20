/* Production React components with intercepted provider fixtures. No live
   account, Android-device or performance result is inferred from this suite. */
const fs=require('fs');
module.exports=async({page,width,theme,assert})=>{
 const go=route=>page.goto('http://127.0.0.1:4173'+route);
 const button=name=>page.getByRole('button',{name,exact:true});
 const shot=async name=>{await page.evaluate(()=>document.fonts.ready);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));const filename=`design-${name}-${width}-${theme}.png`,bytes=await page.screenshot({path:`../artifacts/${filename}`});if(width===360)console.log(`STAK_DESIGN_SCREENSHOT ${filename} ${bytes.toString('base64')}`);};
 const fits=async()=>assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,'Page fits the phone viewport');
 const nav=async()=>{const links=page.getByRole('navigation',{name:'Main navigation'}).getByRole('link');assert.equal(await links.locator('.stak-nav-label:visible').count(),await links.count(),'All navigation labels stay visible');};
 const wasm=[];page.on('request',request=>{if(/\.wasm(?:\?|$)/.test(request.url()))wasm.push(request.url());});
 await page.emulateMedia({reducedMotion:'reduce'});await go('/');await page.getByTestId('media-hero').waitFor();await nav();
 const hero=page.getByTestId('media-hero'),choices=hero.locator('.hero-pagination button');
 assert.ok(await choices.count()>1);await choices.nth(1).click();await page.waitForFunction(()=>document.querySelectorAll('.hero-pagination button')[1]?.getAttribute('aria-pressed')==='true');
 assert.equal(await hero.locator('.hero-open').count(),1,'Only the selected slide mounts live metadata/actions');
 await fits();await shot('home');
 await go('/discover');await page.getByTestId('discover-results-catalog').waitFor();await nav();await fits();await shot('discover');
 await go('/account');await page.getByTestId('connection-status').waitFor();
 assert.equal(await page.locator('.settings-page>.space-y-3').evaluate(el=>getComputedStyle(el).gridTemplateColumns.split(' ').length),2,'Settings uses a two-column destination index');
 await nav();await fits();await shot('settings');
 await page.getByTestId('settings-group-appearance').getByRole('button').click();await page.getByTestId('theme-motion-preview').waitFor();
 assert.equal(await page.locator('.rive-theme-art').count(),0,'Reduced motion does not mount Rive');
 const opposite=theme==='dark'?'light':'dark';await page.getByTestId('theme-motion-preview').click();await page.waitForFunction(expected=>document.documentElement.dataset.theme===expected,opposite);
 await page.getByRole('radio',{name:theme==='dark'?'Dark':'Light',exact:true}).click();
 await page.emulateMedia({reducedMotion:'no-preference'});
 try{await page.locator('.rive-theme-art[data-rive-status="ready"] canvas').waitFor({timeout:15000});}catch(error){console.log('DESIGN_RIVE_DIAGNOSTIC',JSON.stringify(await page.evaluate(()=>({canvasCapable:typeof CanvasRenderingContext2D,systemReduced:matchMedia('(prefers-reduced-motion: reduce)').matches,appReduced:document.documentElement.dataset.reducedMotion,hidden:document.hidden,status:document.querySelector('[data-rive-status]')?.getAttribute('data-rive-status'),canvases:document.querySelectorAll('canvas').length,resources:performance.getEntriesByType('resource').filter(entry=>/animations\//.test(entry.name)).map(entry=>({name:new URL(entry.name).pathname,bytes:entry.transferSize}))}))));throw error;}
 await page.waitForTimeout(1500);
 const canvas=page.locator('.rive-theme-art canvas'),before=await canvas.evaluate(el=>el.toDataURL());
 await page.getByTestId('theme-motion-preview').click();await page.waitForFunction(expected=>document.documentElement.dataset.theme===expected,opposite);await page.waitForTimeout(1500);
 assert.ok((await canvas.evaluate(el=>el.toDataURL()))!==before,'The bundled Rive drawing responds to the actual theme change');
 await page.getByRole('radio',{name:theme==='dark'?'Dark':'Light',exact:true}).click();await page.waitForTimeout(1500);await fits();await shot('appearance');
 assert.ok(wasm.length>0,'The Rive runtime was actually requested');
 assert.ok(wasm.every(url=>new URL(url).origin==='http://127.0.0.1:4173'),'Primary/fallback WASM are bundled locally');
 await page.emulateMedia({reducedMotion:'reduce'});await page.waitForFunction(()=>!document.querySelector('.rive-theme-art'));
 await go('/title/series/tt0903747');await page.getByTestId('tracking-details').waitFor();await fits();await shot('details');
 assert.equal(await page.locator('.collection-more').count(),0);
 fs.mkdirSync('../validation',{recursive:true});fs.writeFileSync(`../validation/design-${width}-${theme}.json`,JSON.stringify({width,theme,interceptedTestData:true,emblaSelection:true,oneActiveHero:true,allNavigationLabels:true,settingsGrid:true,realThemeControl:true,riveCanvasLoaded:true,localWasm:true,reducedMotionUnmountsRive:true,noHorizontalOverflow:true,screenshots:['home','discover','settings','appearance','details'].map(name=>`design-${name}-${width}-${theme}.png`)},null,2));
};
