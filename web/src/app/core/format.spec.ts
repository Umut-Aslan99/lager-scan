import { qty, signed } from './format';

describe('format', () => {
  it('formats quantities the German way', () => {
    expect(qty(1234.5)).toBe('1.234,5');
    expect(qty(null)).toBe('–');
  });

  it('shows the sign of a count difference', () => {
    expect(signed(3)).toBe('+3');
    expect(signed(-2)).toBe('-2');
    expect(signed(0)).toBe('0');
  });
});
