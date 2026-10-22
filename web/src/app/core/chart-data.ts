/** Couleurs lues dans les jetons CSS : Chart.js dessine en canvas et ne comprend pas var(--…). */
export function themeColors() {
  const css = getComputedStyle(document.documentElement);
  const get = (name: string, fallback: string) => css.getPropertyValue(name).trim() || fallback;
  return {
    // Jetons sémantiques : ils suivent le thème clair ou sombre du moment.
    accent: get('--color-text', '#1b2a2e'),
    info: get('--color-text', '#1b2a2e'),
    warning: get('--color-highlight', '#f2d31b'),
    success: get('--color-success', '#1f7a4d'),
    danger: get('--color-danger', '#b5382a'),
    lavender: get('--color-text-muted', '#53646a'),
    grid: get('--color-border', '#c5cfcb'),
    text: get('--color-text-muted', '#53646a'),
    surface: get('--color-bg', '#eef1ee'),
  };
}
