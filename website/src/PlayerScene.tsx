import { memo, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { Environment, Lightformer, RoundedBox, useTexture } from '@react-three/drei';
import { Group, SRGBColorSpace, Vector3, MathUtils, LinearFilter, LinearMipmapLinearFilter, Shape, ExtrudeGeometry } from 'three';
import { sceneState, styleKeys } from './scene-state';
import type { VisualStyle } from './scene-state';
import { storyPose } from './story-pose';
import { screenshot } from './style-state.js';

function deviceShell(width:number,height:number,depth:number,radius:number) {
  const bevel=.018;
  const x=width/2-bevel,y=height/2-bevel,r=radius-bevel;
  const shape=new Shape();
  shape.moveTo(-x+r,-y);shape.lineTo(x-r,-y);
  shape.quadraticCurveTo(x,-y,x,-y+r);shape.lineTo(x,y-r);
  shape.quadraticCurveTo(x,y,x-r,y);shape.lineTo(-x+r,y);
  shape.quadraticCurveTo(-x,y,-x,y-r);shape.lineTo(-x,-y+r);
  shape.quadraticCurveTo(-x,-y,-x+r,-y);
  const geometry=new ExtrudeGeometry(shape,{depth:depth-bevel*2,bevelEnabled:true,bevelThickness:bevel,bevelSize:bevel,bevelSegments:3,steps:1,curveSegments:8});
  geometry.center();
  return geometry;
}
function Device({ edition, active, onPresented }: { edition: VisualStyle; active: VisualStyle; onPresented:()=>void }) {
  const ref = useRef<Group>(null);
  const shell=useMemo(()=>deviceShell(2.34,5,.15,.2),[]);
  const bezel=useMemo(()=>deviceShell(2.28,4.94,.16,.18),[]);
  useEffect(()=>()=>{shell.dispose();bezel.dispose();},[shell,bezel]);
  const [texture,appearanceTexture] = useTexture([screenshot(edition, 'player'),screenshot(edition,'appearance')]);
  const playerMaterial=useRef<import('three').MeshBasicMaterial>(null);
  const appearanceMaterial=useRef<import('three').MeshBasicMaterial>(null);
  const { invalidate,viewport,gl } = useThree();
  useLayoutEffect(()=>{
    for(const image of [texture,appearanceTexture]){
      image.colorSpace=SRGBColorSpace;
      image.anisotropy=Math.min(8,gl.capabilities.getMaxAnisotropy());
      image.minFilter=LinearMipmapLinearFilter;
      image.magFilter=LinearFilter;
      image.needsUpdate=true;
    }
    invalidate();
  },[texture,appearanceTexture,gl,invalidate]);
  const animateSelection=useRef(false);
  useLayoutEffect(()=>{animateSelection.current=sceneState.visible&&sceneState.storyEnabled;},[active]);
  const others=styleKeys.filter(key=>key!==active);
  const selected=active===edition;
  const side=others.indexOf(edition)===0?-1:1;
  const target=new Vector3();
  const lastDraw=useRef(0);
  const lastProgress=useRef(sceneState.progress);
  const [initialPose]=useState(()=>{
    const pose=storyPose(sceneState.progress,viewport.width,selected,side,active==='miuix');
    return{position:[pose.x,pose.y,pose.z] as [number,number,number],rotation:[.025,pose.ry,pose.rz] as [number,number,number],scale:pose.scale};
  });
  useFrame((_,delta)=>{
    const group=ref.current;
    if(!group || !sceneState.visible || !sceneState.storyEnabled || document.hidden) return;
    const isMiuix=active==='miuix';
    const pose=storyPose(sceneState.progress,viewport.width,selected,side,isMiuix);
    target.set(pose.x,pose.y,pose.z);
    if(playerMaterial.current)playerMaterial.current.opacity=1-pose.appearance;
    if(appearanceMaterial.current)appearanceMaterial.current.opacity=pose.appearance;
    const now=performance.now();
    const selectionFrame=animateSelection.current;
    const snap=(now-lastDraw.current>250&&!selectionFrame)||Math.abs(sceneState.progress-lastProgress.current)>.4;
    animateSelection.current=false;
    lastDraw.current=now;lastProgress.current=sceneState.progress;
    const elapsed=selectionFrame?1/60:Math.min(delta,.25);
    const speed=sceneState.reduced||snap?1:1-Math.exp(-elapsed*12);
    const angle=pose.rz;
    const targetY=pose.ry;
    const targetX=.025;
    const scale=pose.scale;
    const diff=group.position.distanceTo(target)+Math.abs(group.rotation.z-angle)+Math.abs(group.rotation.y-targetY)+Math.abs(group.rotation.x-targetX)+Math.abs(group.scale.x-scale);
    group.position.lerp(target,speed);
    group.rotation.x=MathUtils.lerp(group.rotation.x,targetX,speed);
    group.rotation.y=MathUtils.lerp(group.rotation.y,targetY,speed);
    group.rotation.z=MathUtils.lerp(group.rotation.z,angle,speed);
    group.scale.setScalar(MathUtils.lerp(group.scale.x,scale,speed));
    if(diff>.0008) invalidate();
  });
  const rimColor=edition==='material_design'?'#90959e':edition==='miuix'?'#8b8e96':'#96949f';
  return <group ref={ref} position={initialPose.position} rotation={initialPose.rotation} scale={initialPose.scale}>
    <mesh geometry={shell}>
      <meshStandardMaterial color={rimColor} roughness={.34} metalness={.72} envMapIntensity={.65}/>
    </mesh>
    <mesh geometry={bezel}>
      <meshStandardMaterial color="#080a0f" roughness={.45} metalness={.08}/>
    </mesh>
    <mesh position={[0,0,.086]} onAfterRender={onPresented}>
      <planeGeometry args={[2.16,4.8]}/>
      <meshBasicMaterial ref={playerMaterial} map={texture} toneMapped={false} transparent depthWrite={false}/>
    </mesh>
    <mesh position={[0,0,.087]}>
      <planeGeometry args={[2.16,4.8]}/>
      <meshBasicMaterial ref={appearanceMaterial} map={appearanceTexture} toneMapped={false} transparent opacity={0} depthWrite={false}/>
    </mesh>
    <RoundedBox args={[.03,.38,.06]} radius={.014} smoothness={4} position={[1.175,.7,0]}>
      <meshStandardMaterial color={rimColor} roughness={.3} metalness={.75}/>
    </RoundedBox>
    <RoundedBox args={[.03,.25,.06]} radius={.014} smoothness={4} position={[-1.175,1,0]}>
      <meshStandardMaterial color={rimColor} roughness={.3} metalness={.75}/>
    </RoundedBox>
  </group>;
}
// Bake static reflections once; changing the edition must not rerender the
// cubemap's six faces, especially while the demand canvas is paused.
const SceneLighting=memo(function SceneLighting(){return <>
    <ambientLight intensity={1.1}/>
    <directionalLight position={[3,5,6]} intensity={2.4} color="#ffffff"/>
    <directionalLight position={[-4,-2,4]} intensity={.9} color="#d6deef"/>
    <Environment resolution={64} frames={1}>
      <Lightformer intensity={3} color="#ffffff" position={[-4,3,4]} rotation={[0,Math.PI/6,0]} scale={[4,8,1]}/>
      <Lightformer intensity={1.5} color="#d6deef" position={[4,0,2]} rotation={[0,-Math.PI/3,0]} scale={[1,6,1]}/>
      <Lightformer intensity={1} color="#ffffff" position={[4,-1,2]} rotation={[0,-Math.PI/3,0]} scale={[2,6,1]}/>
    </Environment>
  </>;});
function World({ edition, onReady }: { edition:VisualStyle; onReady:()=>void }) {
  const {invalidate,gl}=useThree();
  useEffect(()=>{
    sceneState.invalidate=()=>{if(sceneState.visible&&sceneState.storyEnabled&&!document.hidden)invalidate();};
    return()=>{sceneState.invalidate=()=>{};};
  },[invalidate]);
  const presentedFrame=useRef<number|undefined>(undefined);
  const onPresented=useCallback(()=>{
    if(presentedFrame.current!==undefined)return;
    // Mark ready only after a textured device has actually drawn. The fallback
    // remains under the transparent canvas until that first presented frame.
    presentedFrame.current=requestAnimationFrame(()=>{onReady();gl.domElement.dataset.scene='rendered';});
  },[gl,onReady]);
  useEffect(()=>()=>{if(presentedFrame.current!==undefined)cancelAnimationFrame(presentedFrame.current);},[]);
  useEffect(()=>{sceneState.invalidate();},[edition]);
  return <>
    <SceneLighting/>
    {styleKeys.map(key=><Device key={key} edition={key} active={edition} onPresented={onPresented}/>)}
  </>;
}
export default function PlayerScene({edition,onReady}:{edition:VisualStyle;onReady:()=>void}) {
  const canvas=useRef<HTMLCanvasElement>(null);
  const [visible,setVisible]=useState(true);
  useEffect(()=>{
    const mount=canvas.current?.closest('.scene-mount');
    const updateVisibility=()=>{
      const rect=mount?.getBoundingClientRect();
      const next=Boolean(rect&&rect.width>0&&rect.height>0&&rect.bottom>0&&rect.top<innerHeight&&!document.hidden&&!document.documentElement.hasAttribute('data-large-text')&&matchMedia('(min-width:1000px) and (min-height:700px) and (prefers-reduced-motion:no-preference)').matches);
      sceneState.visible=next;setVisible(next);
      if(next){sceneState.invalidate();sceneState.setPointer(sceneState.pointerX,sceneState.pointerY);}
    };
    const observer=new IntersectionObserver(updateVisibility,{threshold:0});
    if(mount)observer.observe(mount);
    const flags=new MutationObserver(updateVisibility);flags.observe(document.documentElement,{attributes:true,attributeFilter:['data-large-text']});
    document.addEventListener('visibilitychange',updateVisibility);addEventListener('resize',updateVisibility);const preference=matchMedia('(prefers-reduced-motion:reduce)');preference.addEventListener('change',updateVisibility);updateVisibility();
    return()=>{observer.disconnect();flags.disconnect();document.removeEventListener('visibilitychange',updateVisibility);removeEventListener('resize',updateVisibility);preference.removeEventListener('change',updateVisibility);};
  },[]);
  return <Canvas ref={canvas} data-rendering={visible?'demand':'paused'} aria-hidden="true" camera={{position:[0,0,10.6],fov:32}} dpr={[1,1.5]} frameloop={visible?'demand':'never'} gl={{alpha:true,antialias:true,powerPreference:'high-performance'}} fallback={null}>
    <World edition={edition} onReady={onReady}/>
  </Canvas>;
}
