const PLACEHOLDER = /\{(\w+)\}/g;

/** Fills `{name}` placeholders in a copy string, leaving an unknown placeholder untouched. */
export function interpolate(
  template: string,
  values: Readonly<Record<string, string | number>>,
): string {
  return template.replace(PLACEHOLDER, (placeholder, name: string) => {
    const value = values[name];
    return value === undefined ? placeholder : String(value);
  });
}
