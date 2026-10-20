import { isSeasonPack } from "./seasonPack";

test.each([['Show.S01.Complete',1,true],['Show.S01-S03',2,true],['Show.S01-S03',4,false],['Show.S02E01-E10',1,false],['Show.S02E01-E10',2,true],['Show.2x01-10',2,true],['Show.S01E01',1,false],['Show.Season.2',2,true],['Show.2.évad',2,false]])('pack matching %s for season %i', (name, season, expected)=>{
  expect(isSeasonPack({name},season)).toBe(expected);
});
