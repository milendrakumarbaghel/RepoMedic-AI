import { useId, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { ArrowRight, Check, FileArchive, GitFork, Link, LoaderCircle, UploadCloud, X } from 'lucide-react'
import { normalizeRepositoryUrl, validateZip } from '../lib/analysis'

export function RepositoryImport({ busy, onImport, onUpload }: {
  busy: boolean; onImport: (url: string) => Promise<boolean>; onUpload: (file: File) => Promise<boolean>
}) {
  const [mode, setMode] = useState<'url' | 'zip'>('url')
  const [url, setUrl] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState('')
  const [dragging, setDragging] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const id = useId()

  function chooseFile(next?: File) {
    if (!next || busy) return
    const problem = validateZip(next)
    setError(problem || '')
    setFile(problem ? null : next)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    setError('')
    if (mode === 'zip') {
      if (!file) { setError('Choose a ZIP archive to start your diagnosis.'); return }
      if (await onUpload(file)) setFile(null)
    } else {
      let normalized: string
      try { normalized = normalizeRepositoryUrl(url) }
      catch (problem) { setError((problem as Error).message); return }
      if (await onImport(normalized)) setUrl('')
    }
  }

  function switchMode(next: 'url' | 'zip') { setMode(next); setError('') }
  function tabKey(event: KeyboardEvent<HTMLButtonElement>) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    const next = event.key === 'Home' ? 'url' : event.key === 'End' ? 'zip' : mode === 'url' ? 'zip' : 'url'
    switchMode(next)
    document.getElementById(`${id}-${next}-tab`)?.focus()
  }

  return <form className="import-form" onSubmit={submit} aria-label="Analyze a repository" aria-busy={busy}>
    <div className="import-tabs" role="tablist" aria-label="Repository source">
      <button type="button" role="tab" id={`${id}-url-tab`} aria-controls={`${id}-url-panel`} aria-selected={mode === 'url'} tabIndex={mode === 'url' ? 0 : -1} disabled={busy} onKeyDown={tabKey} onClick={() => switchMode('url')}><GitFork size={16} /> GitHub repository</button>
      <button type="button" role="tab" id={`${id}-zip-tab`} aria-controls={`${id}-zip-panel`} aria-selected={mode === 'zip'} tabIndex={mode === 'zip' ? 0 : -1} disabled={busy} onKeyDown={tabKey} onClick={() => switchMode('zip')}><UploadCloud size={17} /> Upload ZIP</button>
    </div>
    <div role="tabpanel" id={`${id}-${mode}-panel`} aria-labelledby={`${id}-${mode}-tab`}>
      {mode === 'url' ? <>
        <label htmlFor={`${id}-url`} className="sr-only">GitHub repository URL</label>
        <div className={`url-input ${error ? 'invalid' : ''}`}><Link size={17} /><input id={`${id}-url`} value={url} disabled={busy} onChange={event => { setUrl(event.target.value); setError('') }} placeholder="https://github.com/your-name/your-repo" autoComplete="url" spellCheck={false} aria-invalid={!!error} aria-describedby={`${id}-hint ${error ? `${id}-error` : ''}`} /></div>
        <p id={`${id}-hint`} className="input-hint">A public repository. A fresh perspective.</p>
      </> : <>
        <input ref={input} type="file" accept=".zip,application/zip,application/x-zip-compressed" className="sr-only" tabIndex={-1} disabled={busy} aria-label="Repository ZIP archive" aria-invalid={!!error} aria-describedby={`${id}-hint ${error ? `${id}-error` : ''}`} onChange={event => { chooseFile(event.target.files?.[0]); event.target.value = '' }} />
        <div className={`dropzone ${dragging ? 'dragging' : ''} ${file ? 'has-file' : ''}`} onDragOver={event => { event.preventDefault(); if (!busy) setDragging(true) }} onDragLeave={() => setDragging(false)} onDrop={event => { event.preventDefault(); setDragging(false); chooseFile(event.dataTransfer.files[0]) }}>
          {file ? <><FileArchive size={24} /><span className="file-name"><strong>{file.name}</strong><span>{(file.size / 1024).toFixed(1)} KB · Ready to diagnose</span></span><button type="button" className="icon-button" aria-label="Remove selected archive" disabled={busy} onClick={() => { setFile(null); setError('') }}><X size={16} /></button></> : <><UploadCloud size={24} /><span>Drop your repository ZIP here, or <button type="button" className="text-button" disabled={busy} onClick={() => input.current?.click()}>browse files</button></span></>}
        </div>
        <p id={`${id}-hint`} className="input-hint">Include source files and tests. Leave out node_modules. <a href="/demo/broken-react.zip" download="broken-react.zip" className="demo-zip-link">Download demo ZIP ↗</a></p>
      </>}
    </div>
    {error && <p className="form-error" role="alert" id={`${id}-error`}>{error}</p>}
    <div className="import-footer"><button className="button button-primary" type="submit" disabled={busy}>{busy ? <LoaderCircle size={16} className="spin" /> : <span className="mini-cross" aria-hidden="true">+</span>}{busy ? 'Diagnosing repository…' : 'Analyze repository'}{!busy && <ArrowRight size={16} />}</button><span className="import-assurance"><Check size={13} /> Evidence. Then action.</span></div>
  </form>
}
