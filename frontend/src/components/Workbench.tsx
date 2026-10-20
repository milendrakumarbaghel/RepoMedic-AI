import { useId, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { Activity, ArrowDownToLine, ArrowRight, Check, CheckCheck, ChevronDown, ChevronRight, CircleAlert, CircleCheck, Clock3, Code2, FileCode2, FileSearch, FlaskConical, GitBranch, GitPullRequest, LoaderCircle, Play, ShieldCheck, Sparkles, Terminal, TriangleAlert } from 'lucide-react'
import type { Analysis, Finding, Patch, TestResult } from '../types'
import { duration, formatDate, healthLabel, providerLabel, testRate } from '../lib/analysis'

export function StatCards({ analysis }: { analysis: Analysis | null }) {
  const stats = [
    { label: 'Repository health', value: healthLabel(analysis), detail: analysis ? `${analysis.filesScanned} files scanned` : 'Let’s get a baseline', icon: Activity, className: 'health' },
    { label: 'Issues identified', value: analysis ? String(analysis.findings.length).padStart(2, '0') : '—', detail: analysis ? `${analysis.findings.filter(f => f.severity === 'critical').length} critical · ${analysis.findings.filter(f => f.severity === 'warning').length} warnings` : 'A closer look at your code', icon: CircleAlert, className: 'issues' },
    { label: 'Test pass rate', value: testRate(analysis?.tests), detail: analysis ? analysis.tests.status === 'blocked' ? 'Execution blocked · review output' : `${analysis.tests.passed} passed · ${analysis.tests.failed} failed · ${analysis.tests.skipped} skipped` : 'Real tests. Real confidence.', icon: FlaskConical, className: 'tests' },
    { label: 'Suggested fixes', value: analysis ? String(analysis.patches.filter(p => p.status === 'suggested').length).padStart(2, '0') : '—', detail: analysis ? `${analysis.patches.filter(p => p.status === 'validated').length} validated · review every change` : 'Small changes, better code', icon: GitPullRequest, className: 'fixes' },
  ]
  return <section className="stats-grid" aria-label="Selected repository statistics">{stats.map(stat => <article className={`stat-card ${stat.className}`} key={stat.label}><div className="stat-top"><span>{stat.label}</span><stat.icon size={18} strokeWidth={1.7} /></div><strong className={stat.value.length > 7 ? 'stat-word' : ''}>{stat.value}</strong><p>{stat.detail}</p></article>)}</section>
}

function CodePane({ code, line, kind }: { code: string; line: number; kind: 'before' | 'after' }) {
  return <div className={`code-pane ${kind}`}><div className="code-pane-title"><span>{kind === 'before' ? '−' : '+'}</span>{kind === 'before' ? 'Current code' : 'Suggested change'}</div><pre aria-label={kind === 'before' ? 'Current source code' : 'Proposed source code'}>{code ? code.split('\n').map((text, index) => <span className="code-line" key={index}><span className="line-number" aria-hidden="true">{Math.max(1, line) + index}</span><span className="line-sign" aria-hidden="true">{kind === 'before' ? '−' : '+'}</span><code>{text || ' '}</code></span>) : <span className="empty-code">No source snippet supplied.</span>}</pre></div>
}

function PatchPreview({ patch, busy, canValidate, onValidate, onDownload, downloading }: {
  patch?: Patch; busy: boolean; canValidate: boolean; onValidate: () => void; onDownload: () => void; downloading: boolean
}) {
  if (!patch) return <div className="panel no-patch"><ShieldCheck size={25} /><h3>No patch to review yet</h3><p>The engine hasn’t suggested a source change for this finding. Review the evidence and explanation before making a manual change.</p></div>
  return <section className="panel patch-panel" aria-label="Source patch preview">
    <div className="panel-heading"><div className="flex items-center gap-2"><GitPullRequest size={17} /><h3>A little change. A healthier codebase.</h3></div><span className="provider-badge"><Sparkles size={12} />{providerLabel(patch.provider)}</span></div>
    <div className="patch-file"><FileCode2 size={14} /><span>{patch.file}</span><span className={`patch-status ${patch.status}`}>{patch.status}</span></div>
    <div className="code-comparison"><CodePane code={patch.before} line={patch.line} kind="before" /><CodePane code={patch.after} line={patch.line} kind="after" /></div>
    <div className="patch-explanation"><ShieldCheck size={16} /><p>{patch.explanation}</p></div>
    <div className="patch-actions"><span>Review the diff. Let the tests do the talking.</span><div className="flex flex-wrap gap-2"><button className="button button-quiet button-small" onClick={onDownload} disabled={downloading || busy}><ArrowDownToLine size={14} />{downloading ? 'Downloading…' : 'Download diff'}</button><button className="button button-primary button-small" disabled={busy || !canValidate} onClick={onValidate}>{busy ? <LoaderCircle size={14} className="spin" /> : <CheckCheck size={15} />}{busy ? 'Applying & testing…' : canValidate ? 'Apply patch & rerun tests' : patch.status === 'validated' ? 'Patch validated' : 'No applicable patches'}</button></div></div>
  </section>
}

function TestEvidence({ tests, baseline, executionMode, title = 'Test output', baselineOnly = false }: { tests: TestResult; baseline?: TestResult; executionMode: string; title?: string; baselineOnly?: boolean }) {
  const [showBaseline, setShowBaseline] = useState(false)
  const result = showBaseline && baseline ? baseline : tests
  return <section className="terminal-panel" aria-label={title}>
    <div className="terminal-heading"><span><Terminal size={15} />{title}</span><span className="terminal-dots"><i /><i /><i /></span></div>
    <div className="terminal-context"><span><i className={`status-dot ${result.status}`} />{showBaseline || baselineOnly ? 'Baseline run' : 'Latest run'} <span className="terminal-status">/ {result.status}</span></span>{baseline && <button onClick={() => setShowBaseline(value => !value)}>{showBaseline ? 'Show latest' : 'View baseline'}<ArrowRight size={12} /></button>}</div>
    <div className="terminal-command"><span>$</span> {result.command || 'No command reported'}</div>
    <pre className="terminal-output">{result.output || 'The engine did not provide test output.'}</pre>
    <div className={`test-result-strip ${result.status}`}><span>{result.status === 'passed' ? <CircleCheck size={15} /> : result.status === 'blocked' ? <TriangleAlert size={15} /> : <CircleAlert size={15} />}{result.status === 'blocked' ? 'Tests blocked — not verified' : `${result.passed} passed · ${result.failed} failed · ${result.skipped} skipped`}</span><span>{duration(result.durationMs)}</span></div>
    <div className="terminal-footnote"><ShieldCheck size={12} />Execution: {executionMode || 'Not reported'}</div>
  </section>
}

function Findings({ findings, active, onSelect }: { findings: Finding[]; active?: Finding; onSelect: (finding: Finding) => void }) {
  return <section className="panel findings-panel" aria-label="Repository findings"><div className="panel-heading"><div className="flex items-center gap-2"><FileSearch size={17} /><h3>What needs a little care</h3></div><span className="count-badge">{findings.length}</span></div>
    {findings.length === 0 ? <div className="findings-empty"><ShieldCheck size={28} /><h3>No findings reported</h3><p>Review test output and execution status for the full picture.</p></div> : <div className="finding-list">{findings.map(finding => <button key={finding.id} className={`finding-row ${active?.id === finding.id ? 'selected' : ''}`} onClick={() => onSelect(finding)} aria-pressed={active?.id === finding.id}><span className={`severity-icon ${finding.severity}`}>{finding.severity === 'critical' ? <CircleAlert size={16} /> : finding.severity === 'warning' ? <TriangleAlert size={16} /> : <Code2 size={16} />}</span><span className="finding-content"><span className="finding-title">{finding.title}</span><span className="finding-file">{finding.file}:{finding.line}</span><span className="finding-labels"><span className={`severity-label ${finding.severity}`}>{finding.severity}</span><span>{finding.category}</span>{finding.patchable && <span className="fix-available"><Sparkles size={10} />Fix available</span>}</span></span><ChevronRight size={15} className="finding-chevron" /></button>)}</div>}
    {active && <div className="finding-explanation"><span className="eyebrow">THE DIAGNOSIS</span><p>{active.message}</p>{active.explanation !== active.message && <p>{active.explanation}</p>}</div>}
  </section>
}

export function Workbench({ analysis, busy, onDemo, onValidate, onDownload, downloading }: {
  analysis: Analysis | null; busy: string | null; onDemo: () => void; onValidate: () => void; onDownload: (kind: 'report' | 'patch') => void; downloading: string | null
}) {
  const [tab, setTab] = useState('Overview')
  const [findingId, setFindingId] = useState<string | null>(null)
  const [patchId, setPatchId] = useState<string | null>(null)
  const [showFiles, setShowFiles] = useState(false)
  const id = useId()
  const tabs = ['Overview', 'Findings', 'Patch preview', 'Test output']
  const activeFinding = analysis?.findings.find(finding => finding.id === findingId) || analysis?.findings[0]
  const activePatch = analysis?.patches.find(patch => patch.id === patchId) || analysis?.patches.find(patch => patch.file === activeFinding?.file) || (!activeFinding ? analysis?.patches[0] : undefined)
  const canValidate = !!analysis?.patches.some(patch => patch.status === 'suggested')

  function selectFinding(finding: Finding) { setFindingId(finding.id); setPatchId(null) }
  function tabKey(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length
    setTab(tabs[next]); document.getElementById(`${id}-tab-${next}`)?.focus()
  }

  return <section className="workbench" aria-label="Diagnosis workspace" aria-busy={!!busy}>
    <div className="section-heading"><div><div className="section-title"><h2>{analysis ? 'Your diagnosis' : 'Your diagnosis workspace'}</h2><span className={`subtle-badge ${analysis ? 'live' : ''}`}>{analysis ? 'EVIDENCE-BASED' : 'READY WHEN YOU ARE'}</span></div><p>{analysis ? 'The full picture. From first finding to verified fix.' : 'From “something’s broken” to “back on track.” All in one place.'}</p></div><div className="section-actions"><button className="button button-secondary button-small" disabled={!!busy} onClick={onDemo}>{busy === 'demo' ? <LoaderCircle size={14} className="spin" /> : <Play size={13} />}{analysis ? 'Run demo' : 'Try broken React demo'}{!analysis && <ArrowRight size={14} />}</button>{analysis && <button className="button button-quiet button-small" disabled={!!busy || downloading === 'report'} onClick={() => onDownload('report')}><ArrowDownToLine size={14} />{downloading === 'report' ? 'Downloading…' : 'Download report'}</button>}</div></div>
    {analysis && <div className="repository-bar"><div className="repository-icon"><Code2 size={21} /></div><div className="repository-name"><strong>{analysis.repository}</strong><span>{analysis.stack.join(' · ') || 'Stack not reported'}<span className="metadata-divider">/</span>{analysis.source === 'github' ? 'GitHub import' : analysis.source === 'demo' ? 'Demo repository' : 'ZIP upload'}</span></div><span className="branch-badge"><GitBranch size={13} />{analysis.branch || 'No branch reported'}</span><span className={`analysis-status ${healthLabel(analysis).toLowerCase().replace(' ', '-')}`}><i />{healthLabel(analysis)}</span><span className="analysis-date"><Clock3 size={13} />{formatDate(analysis.createdAt)}</span></div>}
    <div className="workbench-tabs" role="tablist" aria-label="Diagnosis details">{tabs.map((item, index) => <button key={item} role="tab" id={`${id}-tab-${index}`} aria-controls={`${id}-panel-${index}`} aria-selected={tab === item} tabIndex={tab === item ? 0 : -1} onKeyDown={event => tabKey(event, index)} onClick={() => setTab(item)}>{item}{item === 'Findings' && analysis && <span>{analysis.findings.length}</span>}</button>)}{!analysis && <span className="sample-label"><span />SAMPLE PREVIEW</span>}</div>
    <div role="tabpanel" id={`${id}-panel-${tabs.indexOf(tab)}`} aria-labelledby={`${id}-tab-${tabs.indexOf(tab)}`} tabIndex={0} className="workbench-content">
      {!analysis ? <EmptyPreview tab={tab} /> : <>
        {tab === 'Overview' && <div className="analysis-summary"><ShieldCheck size={18} /><p>{analysis.summary}</p><span>{duration(analysis.durationMs)}</span></div>}
        {(tab === 'Overview' || tab === 'Findings') && <div className="evidence-grid"><Findings findings={analysis.findings} active={activeFinding} onSelect={selectFinding} /><TestEvidence tests={analysis.tests} baseline={analysis.baselineTests} executionMode={analysis.executionMode} title="The evidence" /></div>}
        {(tab === 'Overview' || tab === 'Patch preview') && <>
          {tab === 'Patch preview' && analysis.patches.length > 1 && <div className="patch-selector"><label htmlFor={`${id}-patch`}>Review a patch</label><select id={`${id}-patch`} value={activePatch?.id || ''} onChange={event => setPatchId(event.target.value)}><option value="" disabled>Select a patch</option>{analysis.patches.map(patch => <option key={patch.id} value={patch.id}>{patch.file}:{patch.line} · {patch.status}</option>)}</select></div>}
          <PatchPreview patch={activePatch} busy={!!busy} canValidate={canValidate} onValidate={onValidate} onDownload={() => onDownload('patch')} downloading={downloading === 'patch'} />
        </>}
        {tab === 'Test output' && <div className="test-comparison"><TestEvidence tests={analysis.baselineTests} executionMode={analysis.executionMode} title="Original baseline · preserved" baselineOnly /><TestEvidence tests={analysis.tests} executionMode={analysis.executionMode} title="Latest test run" /></div>}
        {(tab === 'Overview' || tab === 'Findings') && <div className="file-tree-section"><button className="text-button" aria-expanded={showFiles} aria-controls={`${id}-files`} onClick={() => setShowFiles(value => !value)}><FileCode2 size={14} />{analysis.filesScanned} files scanned{showFiles ? <ChevronDown size={14} /> : <ChevronRight size={14} />}</button>{showFiles && <pre id={`${id}-files`}>{analysis.fileTree.join('\n') || 'No file tree was supplied by the engine.'}</pre>}</div>}
      </>}
    </div>
    {analysis && <section className="activity-panel" aria-label="Analysis activity"><div className="activity-title"><Activity size={16} /><h3>The recovery log</h3><span>Every step, accounted for.</span></div><ol className="activity-timeline">{analysis.events.map((event, index) => <li key={`${event.label}-${index}`} className={event.status}><span className="event-dot">{event.status === 'complete' ? <Check size={12} /> : <CircleAlert size={12} />}</span><div><strong>{event.label}</strong><p>{event.detail}</p><span className="event-status">{event.status}</span></div><time>{duration(event.durationMs)}</time></li>)}</ol>{analysis.events.length === 0 && <p className="muted">No activity events were supplied.</p>}</section>}
  </section>
}

function EmptyPreview({ tab }: { tab: string }) {
  return <div className="empty-preview">
    <div className="empty-preview-copy"><span className="preview-icon">{tab === 'Patch preview' ? <GitPullRequest size={24} /> : tab === 'Test output' ? <FlaskConical size={24} /> : <FileSearch size={24} />}</span><span className="eyebrow">A CHECKUP, NOT A GUESSING GAME</span><h3>{tab === 'Patch preview' ? 'Thoughtful fixes. Nothing hidden.' : tab === 'Test output' ? 'Confidence comes with evidence.' : tab === 'Findings' ? 'Find the root. Not just the symptom.' : 'A clear diagnosis.\nA confident next step.'}</h3><p>{tab === 'Patch preview' ? 'See exactly what changes and why, then apply suggested patches and run the actual tests.' : tab === 'Test output' ? 'Your original baseline stays intact. Compare it with the latest run, including failed or blocked tests.' : 'Bring a repository. We’ll surface the issues, explain the why, and help you find your way to a verified fix.'}</p><div className="preview-step"><span>01</span> Find the friction <ArrowRight size={12} /><span>02</span> Review the remedy <ArrowRight size={12} /><span>03</span> Verify</div></div>
    {tab === 'Patch preview' ? <div className="sample-patch"><div className="sample-patch-heading"><FileCode2 size={14} />Illustrative patch · not an analysis</div><div className="code-comparison"><CodePane code={'const total = price * count;'} line={12} kind="before" /><CodePane code={'const total = price * quantity;'} line={12} kind="after" /></div><p>A line-by-line diff will appear here after diagnosis.</p></div> : <div className="terminal-panel sample-terminal"><div className="terminal-heading"><span><Terminal size={15} />A preview of what’s ahead</span><span className="terminal-dots"><i /><i /><i /></span></div><div className="sample-terminal-body"><p><span className="terminal-prompt">~</span> your next clean commit starts here</p><p><span className="sample-step-number">01</span> Inspect source & discover tests</p><p><span className="sample-step-number">02</span> Connect findings to the evidence</p><p><span className="sample-step-number">03</span> Review, apply & verify a fix</p><div className="sample-terminal-wait"><span className="terminal-cursor" />Awaiting your first repository</div></div><div className="sample-terminal-footer"><ShieldCheck size={12} />Illustrative workflow. No analysis has run.</div></div>}
  </div>
}
