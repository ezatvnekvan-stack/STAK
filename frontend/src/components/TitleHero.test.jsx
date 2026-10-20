import React from 'react';
import { act, mount } from '../testUtils';
import TitleHero from './TitleHero';
import { useMetadataQuery } from '../lib/metadataQuery';
import { setTheme } from '../lib/theme';
jest.mock('../lib/metadataQuery', () => ({ useMetadataQuery: jest.fn() }));
jest.mock('./RatingsBlock', () => () => null);
let view;
afterEach(async () => { if (view) await view.close(); view = null; setTheme('dark'); });
test.each(['movie', 'series'])('cinematic %s artwork omits posters and retains logo fallback/back action', async type => {
  useMetadataQuery.mockReturnValue({ data: { logo: 'https://example.invalid/logo.png' } });
  const back = jest.fn(), name = 'A very long title with no short words or logo available';
  view = await mount(<TitleHero meta={{ id:'tt123', type, name, background:'https://example.invalid/backdrop.jpg', poster:'https://example.invalid/poster.jpg' }} onBack={back}/>);
  expect(view.container.querySelector('.title-poster')).toBeNull();
  expect(view.container.querySelectorAll('img')).toHaveLength(2);
  const logo=view.container.querySelector('.title-branding img');
  await act(async () => logo.dispatchEvent(new Event('error')));
  expect(view.container.querySelector('h1').textContent).toBe(name); expect(view.container.querySelector('h1').className).toBe('font-dot');
  await act(async () => view.container.querySelector('.title-hero-art').dispatchEvent(new Event('error')));
  expect(view.container.querySelector('.title-hero').dataset.artwork).toBe('false');
  await act(async () => view.container.querySelector('button').click()); expect(back).toHaveBeenCalledTimes(1);
});

test('available original logos remain visible in light mode', async () => {
  setTheme('light'); useMetadataQuery.mockReturnValue({ data: { logo: 'https://example.invalid/original-logo.png' } });
  view=await mount(<TitleHero meta={{id:'tt123',type:'movie',name:'Title'}} onBack={()=>{}}/>);
  expect(view.container.querySelector('.title-branding img').getAttribute('src')).toBe('https://example.invalid/original-logo.png');
  expect(view.container.querySelector('h1').textContent).toBe('Title');
});
