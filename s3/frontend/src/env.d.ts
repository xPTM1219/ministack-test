/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}

interface ImportMetaEnv {
  /** Base URL of the API Gateway stage, e.g. `http://<id>.execute-api.localhost:4566/dev`. */
  readonly VITE_API_BASE_URL?: string
  /** Set to `1` to enable the MSW browser worker in the dev server. */
  readonly VITE_ENABLE_MSW?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}