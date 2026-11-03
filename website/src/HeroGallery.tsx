import { useI18n } from './i18n';
import { screenshot, styles } from './style-state.js';
import { styleKeys } from './scene-state';
import type { VisualStyle } from './scene-state';
import './hero-gallery.css';

type HeroGalleryProps = {
  edition: VisualStyle;
  reducedMotion: boolean;
};

/** One stable set of real captures, with presentation poses independent of playback. */
export default function HeroGallery({ edition, reducedMotion }: HeroGalleryProps) {
  const { t } = useI18n();
  const selected = styleKeys.indexOf(edition);

  return <div className="hero-gallery" data-ui data-edition={edition} data-reduced-motion={reducedMotion}>
    {styleKeys.map((key, index) => {
      const position = index === selected ? 'center' : (index - selected + styleKeys.length) % styleKeys.length === 1 ? 'left' : 'right';
      return <div
        key={key}
        className="hero-gallery__device"
        data-position={position}
      >
        <div className="hero-gallery__screen">
          <img
            className="hero-gallery__phone"
            data-position={position}
            data-screen={key === edition ? 'player' : undefined}
            src={screenshot(key, 'player').replace('.png', '.webp')}
            width="810"
            height="1800"
            alt={key === edition ? `${t('Player di Songlass')}, ${styles[key].label}` : ''}
            loading={key === edition ? 'eager' : 'lazy'}
            decoding="async"
            fetchPriority={key === edition ? 'high' : 'auto'}
          />
        </div>
      </div>;
    })}
  </div>;
}
