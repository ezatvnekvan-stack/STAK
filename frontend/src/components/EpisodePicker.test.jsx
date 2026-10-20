import { act, mount } from "../testUtils";
import React from "react";
import EpisodePicker from "./EpisodePicker";

test("episode and season controls select actual episode numbers with accessible selected states", async () => {
  const change = jest.fn();
  const view = await mount(<EpisodePicker videos={[{season:"1",episode:"1",name:"Pilot"},{season:1,episode:2,name:"Next"},{season:2,episode:3,name:"Return"}]} season={1} episode={2} watched={{"1:2":{played:true}}} onChange={change}/>);
  expect(view.container.querySelector('[data-testid="episode-chip-1-2"]').getAttribute("aria-pressed")).toBe("true");
  await act(async () => view.container.querySelector('[data-testid="episode-chip-1-1"]').click());
  expect(change).toHaveBeenLastCalledWith(1,1);
  await act(async () => view.container.querySelector('[data-testid="season-chip-2"]').click());
  expect(change).toHaveBeenLastCalledWith(2,3);
  expect(view.container.textContent).toContain("Watched");
  await view.close();
});

test("horizontal episode rail supports keyboard selection without losing real episode coordinates", async () => {
  const change = jest.fn();
  const view = await mount(<EpisodePicker videos={[{ season: 1, episode: 3, name: 'Third' }, { season: 1, episode: 7, name: 'Seventh', tmdbRating: 8 }]} season={1} episode={3} onChange={change} />);
  try {
    const rail = view.container.querySelector('[data-testid="episode-rail"]');
    expect(rail.getAttribute('aria-label')).toBe('Episodes in season 1');
    const first = view.container.querySelector('[data-testid="episode-chip-1-3"]');
    await act(async () => { first.focus(); first.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true })); });
    expect(change).toHaveBeenLastCalledWith(1, 7);
    expect(document.activeElement).toBe(view.container.querySelector('[data-testid="episode-chip-1-7"]'));
    expect(view.container.querySelector('[data-testid="rating-tmdb"]')).toBeNull();
  } finally { await view.close(); }
});

test('specials never appear in Details episode browsing',async()=>{
  const view=await mount(<EpisodePicker videos={[{season:0,episode:1,name:'Special'},{season:1,episode:1,name:'Pilot'}]} season={1} episode={1} onChange={jest.fn()}/>);
  try{expect(view.container.querySelector('[data-testid="season-chip-0"]')).toBeNull();expect(view.container.textContent).not.toContain('S00');expect(view.container.textContent).toContain('Pilot');}finally{await view.close();}
});
