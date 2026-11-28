/**
 * Bridge between the Android app and the editor page running in its WebView.
 * Android → JS: window.calnotes.* ; JS → Android: window.CalNotesNative.* (addJavascriptInterface).
 */
export interface NativeApi {
  onReady(): void
  onChange(title: string, markdown: string): void
  onHeight(pageUnits: number): void
}

declare global {
  interface Window {
    CalNotesNative?: NativeApi
    calnotes?: HostApi
  }
}

export interface LoadArgs {
  noteKey: string
  title: string
  markdown: string
  ink: string | null
  editable: boolean
  titlePlaceholder: string
  bodyPlaceholder: string
}

export interface PrintNote { title: string; markdown: string; ink: string | null; meta: string }

export interface HostApi {
  load(args: LoadArgs): void
  setInk(ink: string | null): void
  setEditable(editable: boolean): void
  setExtraSpace(pageUnits: number): void
  /** Renders notes for printing (PDF export); Android then prints the WebView. */
  renderPrint(notes: PrintNote[]): void
}

export const native = (): NativeApi | undefined => window.CalNotesNative
