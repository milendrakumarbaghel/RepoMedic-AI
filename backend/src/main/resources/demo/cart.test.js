import test from 'node:test';
import assert from 'node:assert/strict';
import { calculateTotal } from './cart.js';

test('empty cart costs zero', () => {
  assert.equal(calculateTotal([], 10), 0);
});

test('quantity is included without a discount', () => {
  assert.equal(calculateTotal([{ price: 12.5, quantity: 3 }]), 37.5);
});

test('10 percent discount is one tenth, not ten times the subtotal', () => {
  assert.equal(calculateTotal([{ price: 50, quantity: 2 }], 10), 90);
});

test('100 percent discount makes the cart free', () => {
  assert.equal(calculateTotal([{ price: 20, quantity: 1 }], 100), 0);
});

test('fractional percentage is rounded to cents after the discount', () => {
  assert.equal(calculateTotal([{ price: 19.99, quantity: 3 }], 12.5), 52.47);
});

test('rejects invalid quantities and out-of-range discounts', () => {
  assert.throws(() => calculateTotal([{ price: 2, quantity: -1 }]), RangeError);
  assert.throws(() => calculateTotal([{ price: 2, quantity: 1.5 }]), RangeError);
  assert.throws(() => calculateTotal([], 101), RangeError);
  assert.throws(() => calculateTotal([], -1), RangeError);
});
