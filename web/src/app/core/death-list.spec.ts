import { Death } from './death.model';
import { sameDeathList } from './death-list';

const death = (id: number): Death => ({
  id,
  timestamp: 1_000 * id,
  chapter: 'Celeste/1-ForsakenCity',
  room: '1',
  deathX: 0,
  deathY: 0,
  totalDeaths: id,
  sessionDeaths: id,
  timeInRoomMs: 0,
});

describe('sameDeathList', () => {
  it('considère identiques deux listes vides', () => {
    expect(sameDeathList([], [])).toBeTrue();
  });

  it('considère identiques deux copies de la même liste (nouvelle réponse HTTP)', () => {
    expect(sameDeathList([death(1), death(2)], [death(1), death(2)])).toBeTrue();
  });

  it('détecte une mort ajoutée', () => {
    expect(sameDeathList([death(1), death(2)], [death(1), death(2), death(3)])).toBeFalse();
  });

  it('détecte une liste dont la dernière mort a changé', () => {
    expect(sameDeathList([death(1), death(2)], [death(1), death(5)])).toBeFalse();
  });
});
