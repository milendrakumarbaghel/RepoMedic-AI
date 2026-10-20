export function Brand({ compact = false }: { compact?: boolean }) {
  return <span className={`brand ${compact ? 'compact' : ''}`}>
    <svg viewBox="0 0 42 42" fill="none" aria-hidden="true"><rect width="42" height="42" rx="12" fill="currentColor" /><path d="M17 9h8v8h8v8h-8v8h-8v-8H9v-8h8z" fill="#f2f7ee" /><path d="M8 22h9l3-6 4 12 3-6h7" stroke="#83b695" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
    {!compact && <span>RepoMedic <span className="brand-ai">AI</span><small>CODE. CARED FOR.</small></span>}
  </span>
}

export function WorkflowIllustration() {
  return <div className="workflow-illustration" aria-label="Find issues, review a fix, and verify with tests" role="img">
    <div className="illustration-topline"><span className="illustration-dot" /> A LITTLE CARE GOES A LONG WAY</div>
    <svg className="workflow-lines" viewBox="0 0 450 210" fill="none" aria-hidden="true">
      <path d="M75 50H190Q210 50 210 70V112Q210 132 230 132H365" stroke="#90ac8e" strokeWidth="1.5" strokeDasharray="5 5" />
      <path d="M75 50V160Q75 180 95 180H275Q295 180 295 160V132" stroke="#bac9ad" strokeWidth="1.5" />
      <circle cx="75" cy="50" r="6" fill="#ecf2e5" stroke="#6c9273" strokeWidth="2" /><circle cx="295" cy="132" r="5" fill="#ecf2e5" stroke="#6c9273" strokeWidth="2" />
    </svg>
    <div className="workflow-node repository-node"><span className="node-symbol">⌘</span><div><small>YOUR REPOSITORY</small><strong>Something good starts here.</strong><span><i /> main <span className="node-dash">/</span> ready for a checkup</span></div></div>
    <div className="workflow-node remedy-node"><span className="remedy-check">✓</span><div><strong>A clearer path forward.</strong><span>One thoughtful fix at a time.</span></div><span className="sparkle">✧</span></div>
    <div className="illustration-caption"><span>find it.</span> fix it. <span>verify it.</span><span className="caption-underline" /></div>
    <span className="floating-plus plus-one">+</span><span className="floating-plus plus-two">+</span>
  </div>
}
