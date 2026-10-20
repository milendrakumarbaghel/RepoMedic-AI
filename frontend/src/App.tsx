import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowDownToLine, ArrowRight, Check, ChevronRight, CircleHelp, Clock3, FileText, FolderGit2, GitFork, LayoutDashboard, LoaderCircle, LockKeyhole, Menu, Plus, RefreshCw, SearchCheck, Settings2, ShieldCheck, WifiOff, X } from 'lucide-react'
import { api, downloadArtifact } from './lib/api'
import { formatDate, healthLabel } from './lib/analysis'
import type { Analysis, Health } from './types'
import { Brand, WorkflowIllustration } from './components/Brand'
import { Modal } from './components/Modal'
import { RepositoryImport } from './components/RepositoryImport'
import { StatCards, Workbench } from './components/Workbench'
import './App.css'

type View = 'Overview' | 'Diagnostics' | 'Reports'
type Dialog = 'import' | 'settings' | 'help' | null
type Toast = { message: string; type: 'error' | 'success' | 'info' }

function App() {
  const [view, setView] = useState<View>('Overview')
  const [analyses, setAnalyses] = useState<Analysis[]>([])
  const [selected, setSelected] = useState<Analysis | null>(null)
  const [health, setHealth] = useState<Health | null>(null)
  const [connection, setConnection] = useState<'connecting' | 'online' | 'offline'>('connecting')
  const [historyLoading, setHistoryLoading] = useState(true)
  const [historyError, setHistoryError] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [downloading, setDownloading] = useState<string | null>(null)
  const [dialog, setDialog] = useState<Dialog>(null)
  const [mobileNav, setMobileNav] = useState(false)
  const [toast, setToast] = useState<Toast | null>(null)
  const operation = useRef(false)
  const downloadInFlight = useRef(false)

  useEffect(() => {
    const controller = new AbortController()
    api.health(controller.signal).then(result => { setHealth(result); setConnection(result.status === 'ok' ? 'online' : 'offline') }).catch(error => {
      if (error.name !== 'AbortError') setConnection('offline')
    })
    api.history(controller.signal).then(result => {
      setAnalyses(current => mergeHistory(result, current))
      setSelected(current => current || [...result].sort(newestFirst)[0] || null)
      setHistoryLoading(false)
    }).catch(error => {
      if (error.name !== 'AbortError') { setHistoryError(true); setHistoryLoading(false); setToast({ message: error.message, type: 'error' }) }
    })
    return () => controller.abort()
  }, [])

  const closeDialog = useCallback(() => setDialog(null), [])

  async function refreshConnection() {
    setConnection('connecting')
    try {
      const result = await api.health()
      setHealth(result); setConnection(result.status === 'ok' ? 'online' : 'offline')
      setToast({ message: result.status === 'ok' ? 'Connected to the local engine.' : `The engine reported status: ${result.status}.`, type: result.status === 'ok' ? 'success' : 'error' })
    } catch (error) { setConnection('offline'); notifyError(error) }
  }

  async function refreshHistory() {
    setHistoryLoading(true); setHistoryError(false)
    try {
      const result = await api.history()
      setAnalyses(result.sort(newestFirst))
      setSelected(current => result.find(item => item.id === current?.id) || result[0] || null)
    } catch (error) { setHistoryError(true); notifyError(error) }
    finally { setHistoryLoading(false) }
  }

  function notifyError(error: unknown) { setToast({ message: error instanceof Error ? error.message : 'Something went wrong. Please try again.', type: 'error' }) }

  async function run(label: string, action: () => Promise<Analysis>): Promise<boolean> {
    if (operation.current) return false
    operation.current = true; setBusy(label); setToast(null)
    try {
      const response = await action()
      const result = label === 'validate' && selected ? { ...response, baselineTests: selected.baselineTests } : response
      setSelected(result)
      setAnalyses(current => mergeHistory(current, [result]))
      setDialog(null); setView('Overview'); setMobileNav(false)
      const blocked = result.tests.status === 'blocked'
      setToast({ message: label === 'validate'
        ? blocked ? 'Patch processing finished, but tests were blocked. Review the output before trusting the change.' : result.tests.status === 'passed' && result.status === 'validated' ? 'Patch validated. Review the latest test results alongside your original baseline.' : 'Validation finished. Review the latest results; the fix is not yet verified.'
        : blocked ? 'Diagnosis ready. Test execution was blocked — review the evidence.' : result.status === 'error' ? 'The engine reported an analysis error. Review the diagnosis and test output.' : 'Your diagnosis is ready. Let’s take a closer look.', type: result.status === 'error' ? 'error' : blocked || label === 'validate' && result.status !== 'validated' ? 'info' : 'success' })
      return true
    } catch (error) { notifyError(error); return false }
    finally { operation.current = false; setBusy(null) }
  }

  async function selectAnalysis(id: string) {
    if (operation.current) return
    operation.current = true; setBusy('history'); setMobileNav(false)
    try {
      const result = await api.analysis(id)
      setSelected(result); setAnalyses(current => mergeHistory(current, [result])); setView('Diagnostics')
    } catch (error) { notifyError(error) }
    finally { operation.current = false; setBusy(null) }
  }

  async function download(kind: 'report' | 'patch') {
    if (!selected || downloadInFlight.current) return
    downloadInFlight.current = true; setDownloading(kind)
    try { await downloadArtifact(selected.id, selected.repository, kind) }
    catch (error) { notifyError(error) }
    finally { downloadInFlight.current = false; setDownloading(null) }
  }

  function navigate(next: View) {
    setView(next); setMobileNav(false)
    if (next === 'Diagnostics') requestAnimationFrame(() => document.getElementById('diagnosis')?.scrollIntoView?.({ behavior: 'smooth', block: 'start' }))
  }

  const importProps = {
    busy: !!busy,
    onImport: (url: string) => run('import', () => api.import(url)),
    onUpload: (file: File) => run('upload', () => api.upload(file)),
  }

  return <div className="app-shell">
    <a href="#main-content" className="skip-link">Skip to main content</a>
    {mobileNav && <button className="nav-scrim" aria-label="Close navigation" onClick={() => setMobileNav(false)} />}
    <aside className={`sidebar ${mobileNav ? 'is-open' : ''}`} aria-label="Workspace navigation" inert={!!dialog}>
      <div className="sidebar-brand"><Brand /><button className="icon-button mobile-nav-close" onClick={() => setMobileNav(false)} aria-label="Close navigation"><X size={19} /></button></div>
      <div className="workspace-switch"><span className="workspace-avatar"><FolderGit2 size={17} /></span><div><strong>Personal workspace</strong><span>Your code, in good hands</span></div><LockKeyhole size={12} /></div>
      <p className="nav-eyebrow">REPOSITORY HEALTH</p>
      <nav className="main-nav" aria-label="Main navigation"><button aria-current={view === 'Overview' ? 'page' : undefined} onClick={() => navigate('Overview')}><LayoutDashboard size={18} />Overview{view === 'Overview' && <span className="nav-active-dot" />}</button><button aria-current={view === 'Diagnostics' ? 'page' : undefined} onClick={() => navigate('Diagnostics')}><SearchCheck size={18} />Diagnostics{selected && <span className="nav-count">{selected.findings.length}</span>}</button><button aria-current={view === 'Reports' ? 'page' : undefined} onClick={() => navigate('Reports')}><FileText size={18} />Reports</button></nav>
      <div className="history-heading"><p className="nav-eyebrow">RECENT DIAGNOSES</p><button className="icon-button" aria-label="Refresh diagnosis history" disabled={historyLoading || !!busy} onClick={refreshHistory}><RefreshCw size={13} className={historyLoading ? 'spin' : ''} /></button></div>
      <div className="sidebar-history">{historyLoading ? <p className="history-message">Loading your workspace…</p> : historyError ? <div className="history-message">History couldn’t load.<button className="text-button" onClick={refreshHistory}>Try again<RefreshCw size={11} /></button></div> : analyses.length ? analyses.slice(0, 5).map(analysis => <button key={analysis.id} className={`history-item ${selected?.id === analysis.id ? 'selected' : ''}`} onClick={() => selectAnalysis(analysis.id)} disabled={!!busy}><span className={`history-status ${healthLabel(analysis).toLowerCase().replace(' ', '-')}`} /><span><strong>{analysis.repository}</strong><small>{formatDate(analysis.createdAt)}</small></span><ChevronRight size={12} /></button>) : <div className="history-empty"><span className="history-empty-icon"><Clock3 size={17} /></span><p>A fresh start.<br /><span>Your diagnoses will live here.</span></p></div>}</div>
      <div className="sidebar-bottom"><div className="care-card"><span className="care-card-symbol"><ShieldCheck size={19} /></span><h3>A little peace of mind.</h3><p>Understand the workflow, your data, and what happens next.</p><button onClick={() => setDialog('help')}>How RepoMedic works<ArrowRight size={13} /></button></div><button className="sidebar-setting" onClick={() => setDialog('settings')}><Settings2 size={16} />Workspace settings<ChevronRight size={13} /></button><div className="local-workspace"><span className={`connection-dot ${connection}`} /><span>Local workspace</span><span className="local-label">{connection === 'online' ? 'CONNECTED' : connection === 'connecting' ? 'CONNECTING' : 'OFFLINE'}</span></div></div>
    </aside>

    <div className="main-shell" inert={!!dialog}>
      <header className="topbar"><div className="flex items-center gap-3"><button className="icon-button mobile-menu" onClick={() => setMobileNav(true)} aria-label="Open navigation" aria-expanded={mobileNav}><Menu size={21} /></button><div className="breadcrumb"><FolderGit2 size={15} /><span>Workspace</span><ChevronRight size={13} /><strong>{view}</strong></div></div><div className="topbar-actions"><button className={`engine-status ${connection}`} onClick={() => setDialog('settings')}><span className={`connection-dot ${connection}`} /><span>{connection === 'online' ? 'Local engine connected' : connection === 'connecting' ? 'Connecting to engine' : 'Local engine offline'}</span></button><span className="topbar-divider" /><button className="avatar" aria-label="Open personal workspace settings" onClick={() => setDialog('settings')}>JD<span /></button></div></header>

      <main id="main-content" className="main-content">
        <div className="page-heading"><div><span className="eyebrow"><span />A LITTLE CARE. A LOT MORE CONFIDENCE.</span><h1>{view === 'Reports' ? 'Your code’s story, in one place.' : 'A healthier codebase starts here.'}</h1><p>{view === 'Reports' ? 'Revisit the findings, fixes, and evidence behind every diagnosis.' : 'Find the issues. Understand the why. Ship with peace of mind.'}</p></div><button className="button button-primary new-diagnosis" onClick={() => setDialog('import')} disabled={!!busy}><Plus size={17} />New diagnosis</button></div>

        {busy && <div className="operation-banner" role="status"><LoaderCircle size={17} className="spin" /><span>{busy === 'validate' ? 'Applying suggested patches and rerunning tests…' : busy === 'history' ? 'Opening your diagnosis…' : busy === 'demo' ? 'Checking the broken React demo against real tests…' : 'Importing your repository and running the diagnosis…'}</span><span className="operation-note">Let’s follow the evidence.</span></div>}

        {view !== 'Reports' ? <>
          <section className="hero-panel"><div className="hero-copy"><div className="hero-eyebrow"><span className="tiny-cross">+</span> MEET YOUR CODE’S NEW CARE TEAM</div><h2>Big picture. Small fixes.<br /><span>Better code, together.</span></h2><p>A fresh set of eyes for your repository. Get a clear diagnosis<br className="desktop-break" /> and thoughtful fixes — backed by actual test results.</p><RepositoryImport {...importProps} /></div><WorkflowIllustration /></section>
          <StatCards analysis={selected} />
          <div id="diagnosis"><Workbench key={selected?.id || 'empty'} analysis={selected} busy={busy} onDemo={() => run('demo', api.demo)} onValidate={() => { if (selected) void run('validate', () => api.validate(selected.id)) }} onDownload={download} downloading={downloading} /></div>
          <div className="bottom-note"><span><ShieldCheck size={15} />Thoughtful fixes. Transparent evidence. Always your call.</span><button className="text-button" onClick={() => setDialog('help')}>A quick guide<ArrowRight size={13} /></button></div>
        </> : <section className="panel reports-panel"><div className="panel-heading"><div className="flex items-center gap-2"><FileText size={18} /><h2>Diagnosis library</h2><span className="count-badge">{analyses.length}</span></div><button className="button button-quiet button-small" onClick={refreshHistory} disabled={historyLoading || !!busy}><RefreshCw size={14} className={historyLoading ? 'spin' : ''} />Refresh</button></div>{historyLoading ? <div className="reports-empty"><LoaderCircle size={24} className="spin" /><p>Loading your diagnosis library…</p></div> : historyError ? <div className="reports-empty"><WifiOff size={30} /><h3>Your library couldn’t be reached</h3><p>Check the local engine connection, then refresh to try again.</p><button className="button button-secondary" onClick={refreshHistory}>Try again</button></div> : analyses.length === 0 ? <div className="reports-empty"><FileText size={34} /><h3>A story waiting to be written.</h3><p>Your reports will appear here after your first diagnosis.</p><button className="button button-primary" onClick={() => run('demo', api.demo)} disabled={!!busy}>Try broken React demo<ArrowRight size={15} /></button></div> : <div className="report-list">{analyses.map(analysis => <button className="report-row" key={analysis.id} onClick={() => selectAnalysis(analysis.id)} disabled={!!busy}><span className="report-icon"><FolderGit2 size={21} /></span><span className="report-name"><strong>{analysis.repository}</strong><span>{formatDate(analysis.createdAt)} · {analysis.branch} · {analysis.filesScanned} files</span></span><span className="subtle-badge">{healthLabel(analysis)}</span><span className="report-open">Open report<ArrowRight size={15} /></span></button>)}</div>}</section>}
        <footer className="page-footer"><span>Made for the code you care about.</span><span>RepoMedic AI <span className="footer-plus">+</span> A healthier way to build.</span></footer>
      </main>
    </div>

    {dialog === 'import' && <Modal title="Give your repository a fresh start." onClose={closeDialog} busy={!!busy}><p className="modal-intro">Import a public GitHub repository or bring a ZIP. We’ll take it from there.</p><RepositoryImport {...importProps} /><div className="modal-note"><ShieldCheck size={16} />Source files are sent to your configured backend for analysis. Results will identify the execution mode and patch provider.</div></Modal>}
    {dialog === 'settings' && <Modal title="Your local workspace" onClose={closeDialog}><p className="modal-intro">A clear view of the engine doing the work.</p><div className="settings-connection"><span className={`connection-dot ${connection}`} /><div><strong>{connection === 'online' ? 'Engine connected' : connection === 'connecting' ? 'Connecting…' : 'Engine unavailable'}</strong><p>Same-origin API · /api</p></div><button className="button button-secondary button-small" disabled={connection === 'connecting'} onClick={refreshConnection}><RefreshCw size={14} />Check connection</button></div><dl className="settings-details"><div><dt>Workspace</dt><dd>Personal · local backend</dd></div><div><dt>AI integration</dt><dd>{connection !== 'online' || !health ? 'Unknown — connect to check' : health.aiEnabled ? 'Enabled on backend' : 'Disabled on backend'}</dd></div><div><dt>Patch attribution</dt><dd>Reported per patch</dd></div><div><dt>Execution evidence</dt><dd>Reported per diagnosis</dd></div></dl><p className="modal-note"><Settings2 size={17} />Backend connection and model credentials are configured by the server. The frontend sends requests to /api.</p></Modal>}
    {dialog === 'help' && <Modal title="Better code starts with a little clarity." onClose={closeDialog}><p className="modal-intro">A guided checkup, with you in control of the next step.</p><div className="help-steps"><div><GitFork size={20} /><span><strong>1. Bring your repository</strong><p>Import a public GitHub URL, upload a ZIP, or try the deliberately broken React demo.</p></span></div><div><SearchCheck size={20} /><span><strong>2. Follow the evidence</strong><p>Review file-level findings, proposed changes, patch providers, and the actual test output. Blocked tests stay marked as blocked.</p></span></div><div><Check size={20} /><span><strong>3. Make a change. Check it.</strong><p>Apply suggested patches on the backend and rerun tests. Your original test baseline remains available for comparison.</p></span></div><div><ArrowDownToLine size={20} /><span><strong>4. Take the details with you</strong><p>Download the Markdown report and unified diff from the selected diagnosis.</p></span></div></div><div className="privacy-note"><LockKeyhole size={18} /><div><strong>About your data</strong><p>Repositories and archives are sent to your configured backend. Network access, model use, and retention depend on that backend’s configuration. Each diagnosis reports its execution mode and each patch names its provider.</p></div></div></Modal>}
    {toast && <div className={`toast ${toast.type}`} role={toast.type === 'error' ? 'alert' : 'status'}><span className="toast-icon">{toast.type === 'success' ? <Check size={18} /> : toast.type === 'error' ? <WifiOff size={18} /> : <CircleHelp size={18} />}</span><p>{toast.message}</p><button className="icon-button" aria-label="Dismiss notification" onClick={() => setToast(null)}><X size={17} /></button></div>}
  </div>
}

function newestFirst(a: Analysis, b: Analysis) { return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime() }
function mergeHistory(older: Analysis[], newer: Analysis[]) {
  return [...new Map([...older, ...newer].map(item => [item.id, item])).values()].sort(newestFirst)
}

export default App
