/** Three measured scroll stations, independent of React and rendering. */
export function storyPose(progress:number,width:number,selected:boolean,side:number,miuix:boolean){
 const smooth=(n:number)=>{const t=Math.max(0,Math.min(1,n));return t*t*(3-2*t);};
 const compare=smooth(progress),appearance=smooth((progress-1.25)/.75);
 const mix=(a:number,b:number,t:number)=>a+(b-a)*t;
 const compareFit=Math.min(1,width/9.7);
 const heroX=width*.25+(selected?0:side*1.4*compareFit);
 const compareX=width*.18+(selected?0:side*1.8*compareFit);
 const x=mix(mix(heroX,compareX,compare),selected?-width*.24:side*(width*.65+2),appearance);
 return{x,y:mix(mix(selected?-.04:-.28,-.75,compare),-.1,appearance),z:mix(selected?.25:-.55,selected?.2:-.4,compare),
  scale:mix(mix(selected?.82:.7*compareFit,(selected?.72:.64)*compareFit,compare),selected?.92:.6,appearance),
  rz:mix(mix(selected?(miuix?0:-.075):side*-.11,selected?0:side*.09,compare),-.045,appearance),
  ry:mix(mix(selected?-.12:side*-.14,side*-.08,compare),.12,appearance),
  appearance:Math.max(0,Math.min(1,(appearance-.15)/.6))};
}

export function storyCopy(progress:number){
 const ramp=(a:number,b:number)=>Math.max(0,Math.min(1,(progress-a)/(b-a)));
 return[1-ramp(.15,.4),ramp(.65,.9)*(1-ramp(1.1,1.35)),ramp(1.7,1.95)];
}
