import { useEffect, useState } from 'react';

/**
 * The value once it has stopped changing for {@link delayMs}. This is what keeps a search from
 * firing a request per keystroke; it is a delay on the reader's own input, never a timer that asks
 * the server for something nobody requested.
 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [settled, setSettled] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return settled;
}
