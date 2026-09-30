type Listener = (event: Event) => void;

/**
 * jsdom ships no EventSource, and a real one would need a server. This stands in for it and lets a
 * test drive a connection: open it, push an event, make it fail, and assert it was closed.
 */
export class FakeEventSource {
  static readonly instances: FakeEventSource[] = [];

  readonly url: string;
  readyState = 0;
  closed = false;

  private readonly listeners = new Map<string, Set<Listener>>();

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: Listener): void {
    const existing = this.listeners.get(type) ?? new Set<Listener>();
    existing.add(listener);
    this.listeners.set(type, existing);
  }

  removeEventListener(type: string, listener: Listener): void {
    this.listeners.get(type)?.delete(listener);
  }

  close(): void {
    this.readyState = 2;
    this.closed = true;
  }

  open(): void {
    this.readyState = 1;
    this.dispatch(new Event('open'));
  }

  emit(type: string, data: string): void {
    this.dispatch(new MessageEvent(type, { data }));
  }

  fail(): void {
    this.readyState = 0;
    this.dispatch(new Event('error'));
  }

  private dispatch(event: Event): void {
    for (const listener of [...(this.listeners.get(event.type) ?? [])]) {
      listener(event);
    }
  }
}

export function installFakeEventSource(): void {
  FakeEventSource.instances.length = 0;
  globalThis.EventSource = FakeEventSource as unknown as typeof EventSource;
}

export function openEventSources(): readonly FakeEventSource[] {
  return FakeEventSource.instances.filter((instance) => !instance.closed);
}

export function activeEventSource(): FakeEventSource {
  const open = openEventSources();
  const last = open[open.length - 1];
  if (!last) {
    throw new Error('No event source was opened');
  }
  return last;
}
