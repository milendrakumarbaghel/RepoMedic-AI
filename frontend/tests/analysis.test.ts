import { test } from 'node:test'
import assert from 'node:assert/strict'
import { healthLabel, normalizeRepositoryUrl, providerLabel, testRate, validateZip } from '../src/lib/analysis'
import { analysis, failedTests } from './fixtures'

test('empty and blocked analyses never report a successful health or test rate', () => {
  assert.equal(healthLabel(null), '—')
  assert.equal(testRate(), '—')
  const blocked = { ...failedTests, status: 'blocked' as const, passed: 0, failed: 0, total: 0 }
  assert.equal(testRate(blocked), '—')
  assert.equal(healthLabel(analysis({ status: 'validated', tests: blocked })), 'Blocked')
  assert.equal(healthLabel(analysis({ status: 'validated' })), 'Needs care')
})

test('pass rates exclude skipped tests and avoid division by zero', () => {
  assert.equal(testRate(failedTests), '50%')
  assert.equal(testRate({ ...failedTests, total: 5, skipped: 1 }), '50%')
  assert.equal(testRate({ ...failedTests, total: 4, skipped: 4 }), '—')
})

test('deterministic providers are attributed accurately and model names preserved', () => {
  for (const provider of ['deterministic', 'rule-based', 'rule_based', 'Rule based engine']) assert.equal(providerLabel(provider), 'Rule-based')
  assert.equal(providerLabel('gemini-2.5-pro'), 'gemini-2.5-pro')
  assert.equal(providerLabel(''), 'Unspecified provider')
})

test('repository input is normalized and invalid or credential-bearing URLs are rejected', () => {
  assert.equal(normalizeRepositoryUrl(' https://github.com/team/shop.git/ '), 'https://github.com/team/shop.git')
  for (const input of ['bad-url', 'http://github.com/team/shop', 'https://example.com/team/shop', 'https://github.com/team', 'https://github.com/team/shop/tree/main', 'https://token@github.com/team/shop']) {
    assert.throws(() => normalizeRepositoryUrl(input), /GitHub repository URL/)
  }
})

test('ZIP selection rejects wrong formats and empty files while accepting uppercase extensions', () => {
  assert.match(validateZip(new File(['text'], 'source.txt'))!, /\.zip/)
  assert.match(validateZip(new File([], 'source.zip'))!, /empty/)
  assert.equal(validateZip(new File(['archive'], 'SOURCE.ZIP')), null)
})
