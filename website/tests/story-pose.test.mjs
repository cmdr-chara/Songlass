import test from 'node:test';
import assert from 'node:assert/strict';
import {storyPose,storyCopy} from '../src/story-pose.ts';
test('scroll stations retain player, compare identities, then reveal Appearance on the opposite side',()=>{
 const hero=storyPose(0,10,true,-1,false),compare=storyPose(1,10,true,-1,false),appearance=storyPose(2,10,true,-1,false);
 assert(hero.x>2&&hero.appearance===0);assert(compare.x>0&&compare.x<hero.x&&compare.appearance===0);assert(appearance.x< -2&&appearance.appearance===1);
 assert(storyPose(1,10,false,-1,false).x<compare.x);assert(storyPose(1,10,false,1,false).x>compare.x);
});
test('scroll trajectory is continuous and clamped rather than replayed as timed entrances',()=>{
 for(const selected of[true,false])for(const side of[-1,1]){
  let prior=storyPose(0,10,selected,side,false);
  for(let i=1;i<=2000;i++){const next=storyPose(i/1000,10,selected,side,false);for(const key of Object.keys(next)){assert(Number.isFinite(next[key]));assert(Math.abs(next[key]-prior[key])<.05);}prior=next;}
  assert.deepEqual(storyPose(-10,10,selected,side,false),storyPose(0,10,selected,side,false));assert.deepEqual(storyPose(10,10,selected,side,false),storyPose(2,10,selected,side,false));
 }
});

test('only the current copy panel is readable at each scroll station',()=>{
 assert.deepEqual(storyCopy(0),[1,0,0]);assert.deepEqual(storyCopy(1),[0,1,0]);assert.deepEqual(storyCopy(2),[0,0,1]);
 for(let i=0;i<=200;i++)assert(storyCopy(i/100).filter(a=>a>0).length<=1);
});
