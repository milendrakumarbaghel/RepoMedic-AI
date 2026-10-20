# RepoMedic AI analysis

- Repository: broken-react
- ID: abe19227-145c-4e2d-80f6-7bbddbb76882
- Source: upload
- Branch: uploaded
- Created: 2026-10-20T09:31:34.743768Z
- Status: validated
- Execution mode: trusted-demo-node
- AI configured: false
- Files scanned: 4
- Duration: 582 ms

Validated 1 patch(es): 6/6 tests passed; baseline had 3 failures. Findings describe the original source.

## Baseline tests

Status: failed; total: 6; passed: 3; failed: 3; skipped: 0; duration: 230 ms

Command: node --test --test-reporter=tap

Output:

```text
TAP version 13
# Subtest: empty cart costs zero
ok 1 - empty cart costs zero
  ---
  duration_ms: 1.292
  type: 'test'
  ...
# Subtest: quantity is included without a discount
ok 2 - quantity is included without a discount
  ---
  duration_ms: 0.283917
  type: 'test'
  ...
# Subtest: 10 percent discount is one tenth, not ten times the subtotal
not ok 3 - 10 percent discount is one tenth, not ten times the subtotal
  ---
  duration_ms: 1.207708
  type: 'test'
  location: 'cart.test.js:13:1'
  failureType: 'testCodeFailure'
  error: |-
    Expected values to be strictly equal:

    -900 !== 90

  code: 'ERR_ASSERTION'
  name: 'AssertionError'
  expected: 90
  actual: -900
  operator: 'strictEqual'
  stack: |-
    TestContext.<anonymous> (cart.test.js:14:10)
    Test.runInAsyncScope (node:async_hooks:214:14)
    Test.run (node:internal/test_runner/test:1047:25)
    Test.processPendingSubtests (node:internal/test_runner/test:744:18)
    Test.postRun (node:internal/test_runner/test:1173:19)
    Test.run (node:internal/test_runner/test:1101:12)
    async Test.processPendingSubtests (node:internal/test_runner/test:744:7)
  ...
# Subtest: 100 percent discount makes the cart free
not ok 4 - 100 percent discount makes the cart free
  ---
  duration_ms: 0.321209
  type: 'test'
  location: 'cart.test.js:17:1'
  failureType: 'testCodeFailure'
  error: |-
    Expected values to be strictly equal:

    -1980 !== 0

  code: 'ERR_ASSERTION'
  name: 'AssertionError'
  expected: 0
  actual: -1980
  operator: 'strictEqual'
  stack: |-
    TestContext.<anonymous> (cart.test.js:18:10)
    Test.runInAsyncScope (node:async_hooks:214:14)
    Test.run (node:internal/test_runner/test:1047:25)
    Test.processPendingSubtests (node:internal/test_runner/test:744:18)
    Test.postRun (node:internal/test_runner/test:1173:19)
    Test.run (node:internal/test_runner/test:1101:12)
    async Test.processPendingSubtests (node:internal/test_runner/test:744:7)
  ...
# Subtest: fractional percentage is rounded to cents after the discount
not ok 5 - fractional percentage is rounded to cents after the discount
  ---
  duration_ms: 1.382917
  type: 'test'
  location: 'cart.test.js:21:1'
  failureType: 'testCodeFailure'
  error: |-
    Expected values to be strictly equal:

    -689.65 !== 52.47

  code: 'ERR_ASSERTION'
  name: 'AssertionError'
  expected: 52.47
  actual: -689.65
  operator: 'strictEqual'
  stack: |-
    TestContext.<anonymous> (cart.test.js:22:10)
    Test.runInAsyncScope (node:async_hooks:214:14)
    Test.run (node:internal/test_runner/test:1047:25)
    Test.processPendingSubtests (node:internal/test_runner/test:744:18)
    Test.postRun (node:internal/test_runner/test:1173:19)
    Test.run (node:internal/test_runner/test:1101:12)
    async Test.processPendingSubtests (node:internal/test_runner/test:744:7)
  ...
# Subtest: rejects invalid quantities and out-of-range discounts
ok 6 - rejects invalid quantities and out-of-range discounts
  ---
  duration_ms: 1.034875
  type: 'test'
  ...
1..6
# tests 6
# suites 0
# pass 3
# fail 3
# cancelled 0
# skipped 0
# todo 0
# duration_ms 137.498541
```

## Latest tests / validation attempt

Status: passed; total: 6; passed: 6; failed: 0; skipped: 0; duration: 267 ms

Command: node --test --test-reporter=tap

Output:

```text
TAP version 13
# Subtest: empty cart costs zero
ok 1 - empty cart costs zero
  ---
  duration_ms: 1.13925
  type: 'test'
  ...
# Subtest: quantity is included without a discount
ok 2 - quantity is included without a discount
  ---
  duration_ms: 0.250792
  type: 'test'
  ...
# Subtest: 10 percent discount is one tenth, not ten times the subtotal
ok 3 - 10 percent discount is one tenth, not ten times the subtotal
  ---
  duration_ms: 0.158208
  type: 'test'
  ...
# Subtest: 100 percent discount makes the cart free
ok 4 - 100 percent discount makes the cart free
  ---
  duration_ms: 0.148042
  type: 'test'
  ...
# Subtest: fractional percentage is rounded to cents after the discount
ok 5 - fractional percentage is rounded to cents after the discount
  ---
  duration_ms: 1.219667
  type: 'test'
  ...
# Subtest: rejects invalid quantities and out-of-range discounts
ok 6 - rejects invalid quantities and out-of-range discounts
  ---
  duration_ms: 0.683458
  type: 'test'
  ...
1..6
# tests 6
# suites 0
# pass 6
# fail 0
# cancelled 0
# skipped 0
# todo 0
# duration_ms 136.912458
```

## Original-source findings

### Incorrect percentage calculation (critical)

cart.js:13 — correctness

The discount multiplies by a whole percentage rather than its fractional value.

Divide discountPercent by 100 before multiplying. A 10% discount on 100 must subtract 10.

Before:

```text
const discount = subtotal * discountPercent;
```

Suggested replacement:

```text
const discount = subtotal * (discountPercent / 100);
```

## Patches

- cart.js:13 — validated; provider: rule-based — Divide discountPercent by 100 before multiplying. A 10% discount on 100 must subtract 10.

## Events

- Inspect ZIP [complete] Extracted bounded ZIP; ignored credentials and generated/dependency directories. Host execution requires a fresh exact audited fixture content match. (68 ms)
- Static analysis [complete] Read 4 bounded text files; detected 1 source findings. Findings are heuristics for review, not proof of exploitability. (4 ms)
- Trusted audited fixture recognition [complete] Reverified all four shipped fixture files byte-for-byte (original or exact audited arithmetic fix), with no extra files, directories, or symlinks. Content-verified upload may run trusted Node tests on the host; source and repository identity are preserved. (0 ms)
- Baseline tests [failed] node --test --test-reporter=tap: 3 passed, 3 failed, 0 skipped. (230 ms)
- Patch provider [complete] Bundled demo and content-verified fixture uploads use only the deterministic rule-based fix; no external AI request is made. (0 ms)
- Trusted audited fixture recognition [complete] Reverified all four shipped fixture files byte-for-byte (original or exact audited arithmetic fix), with no extra files, directories, or symlinks. Content-verified upload may run trusted Node tests on the host; source and repository identity are preserved. (2 ms)
- Patched tests [complete] node --test --test-reporter=tap: 6 passed, 0 failed, 0 skipped. (267 ms)
- Validate patches [complete] Accepted exact source-only patches after a real nonzero passing test run. Baseline results are preserved. (273 ms)
