import { Death } from './death.model';

/**
 * Deux listes de morts sont-elles « les mêmes » ? Les morts ne se modifient pas, elles s'ajoutent :
 * même longueur et même dernier identifiant suffisent, sans parcourir toute la liste.
 * Sert à ne pas redessiner les graphiques quand le rafraîchissement automatique ne ramène rien de neuf.
 */
export function sameDeathList(a: readonly Death[], b: readonly Death[]): boolean {
  if (a === b) return true;
  if (a.length !== b.length) return false;
  return a.length === 0 || a[a.length - 1].id === b[b.length - 1].id;
}
