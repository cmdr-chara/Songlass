export type VisualStyle = 'liquid_glass' | 'material_design' | 'miuix';
export const styleKeys: VisualStyle[] = ['liquid_glass', 'material_design', 'miuix'];

/** Ephemeral pointer coordinates shared with the gallery's motion lifecycle. */
export const sceneState = {
  pointerX: 0,
  pointerY: 0,
  setPointer: (() => {}) as (x: number, y: number) => void,
};
