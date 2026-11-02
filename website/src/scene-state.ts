export type VisualStyle = 'liquid_glass' | 'material_design' | 'miuix';
export const styleKeys: VisualStyle[] = ['liquid_glass','material_design','miuix'];
type SceneState = {
  visible: boolean;
  progress: number;
  pointerX: number;
  pointerY: number;
  reduced: boolean;
  storyEnabled: boolean;
  invalidate: () => void;
  setPointer: (x: number, y: number) => void;
};
export const sceneState: SceneState = {
  visible: true, progress: 0, pointerX: 0, pointerY: 0, reduced: false, storyEnabled: false,
  invalidate: () => {}, setPointer: () => {},
};
