import { JSDOM } from 'jsdom'
import { register } from 'node:module'
import { pathToFileURL } from 'node:url'

// Node tests exercise the real TSX components; only stylesheet loading is inert.
register('./tests/style-loader.mjs', pathToFileURL(`${process.cwd()}/`))
const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:5173' })
for (const key of ['window', 'document', 'navigator', 'HTMLElement', 'Element', 'Node', 'File', 'FormData', 'MutationObserver', 'Event', 'KeyboardEvent', 'MouseEvent']) {
  Object.defineProperty(globalThis, key, { configurable: true, value: dom.window[key as keyof Window] })
}
Object.defineProperty(globalThis, 'getComputedStyle', { configurable: true, value: dom.window.getComputedStyle.bind(dom.window) })
Object.defineProperty(globalThis, 'requestAnimationFrame', { configurable: true, value: (callback: FrameRequestCallback) => setTimeout(callback, 0) })
Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { configurable: true, writable: true, value: true })
