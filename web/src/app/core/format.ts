const de = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 3 });
export const qty = (n: number | null | undefined) => (n === null || n === undefined ? '–' : de.format(n));
export const signed = (n: number | null | undefined) => (n === null || n === undefined ? '–' : `${n > 0 ? '+' : ''}${de.format(n)}`);
export const time = (iso: string) => {
  const d = new Date(iso);
  const today = new Date().toDateString() === d.toDateString();
  return today ? d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) : d.toLocaleString('de-DE', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
};
export const uuid = () => crypto.randomUUID();
