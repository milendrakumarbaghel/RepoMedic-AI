import React, { useState } from 'react';
import { calculateTotal } from './cart.js';

export default function ShoppingCart() {
  const [discount, setDiscount] = useState(10);
  const items = [{ name: 'RepoMedic mug', price: 19.99, quantity: 3 }];
  return (
    <main>
      <h1>Shopping cart</h1>
      <ul>{items.map(item => <li key={item.name}>{item.quantity} × {item.name}</li>)}</ul>
      <label>Discount (%) <input type="number" min="0" max="100" value={discount}
        onChange={event => setDiscount(Number(event.target.value))} /></label>
      <strong>Total: ${calculateTotal(items, discount).toFixed(2)}</strong>
    </main>
  );
}
