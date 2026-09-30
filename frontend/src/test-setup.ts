import '@testing-library/jest-dom/vitest';
import { beforeEach } from 'vitest';
import { installFakeEventSource } from './test-support/fakeEventSource';

// The provider opens the one event stream as soon as it mounts, and jsdom has no EventSource, so
// every test starts with a controllable one in its place.
beforeEach(() => {
  installFakeEventSource();
});
