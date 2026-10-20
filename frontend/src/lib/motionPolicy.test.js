import { motionPolicy } from './motionPolicy';
test('system and application reduced motion both disable animated transitions', () => {
  for (const [appearance, system] of [[{reducedMotion:true},false],[{reducedMotion:false},true],[{},true]]) {
    expect(motionPolicy(appearance,system)).toEqual(expect.objectContaining({reduced:true,duration:0,transition:{duration:0}}));
  }
  expect(motionPolicy({reducedMotion:false},false).reduced).toBe(false);
});
