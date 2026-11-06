import React, { useCallback, useEffect, useState } from 'react';

const catalogUrl = process.env.REACT_APP_CATALOG_API || 'https://industrial-catalog-1057772201167.asia-southeast1.run.app';
const sourcingUrl = process.env.REACT_APP_SOURCING_API || 'https://industrial-sourcing-1057772201167.asia-southeast1.run.app';
const orderUrl = process.env.REACT_APP_ORDER_API || 'https://industrial-orders-1057772201167.asia-southeast1.run.app';
const authUrl = process.env.REACT_APP_AUTH_API || 'https://industrial-auth-1057772201167.asia-southeast1.run.app';
const nextOrderStatus = { PLACED: ['CONFIRMED', 'CANCELLED'], CONFIRMED: ['PACKING', 'CANCELLED'], PACKING: ['OUT_FOR_DELIVERY', 'CANCELLED'], OUT_FOR_DELIVERY: ['DELIVERED'] };
const newProduct = { sku: '', name: '', category: '', description: '', unit: 'piece', priceMinor: 0, imageUrl: '', active: true };
const newVendor = { name: '', email: '', phone: '', active: true };
const newOffer = { productId: '', vendorId: '', vendorSku: '', costMinor: 0, availableQuantity: 0, leadDays: 0, active: true };

function rupees(minor) { return `₹${(Number(minor || 0) / 100).toFixed(2)}`; }
function moneyInput(minor) { return (Number(minor || 0) / 100).toFixed(2); }
function toMinor(value) { return Math.round(Number(value || 0) * 100); }

export default function App() {
  const [login, setLogin] = useState({ username: 'admin', password: '' });
  const [credentials, setCredentials] = useState(null);
  const [tab, setTab] = useState('products');
  const [products, setProducts] = useState([]);
  const [vendors, setVendors] = useState([]);
  const [offers, setOffers] = useState([]);
  const [orders, setOrders] = useState([]);
  const [productForm, setProductForm] = useState(newProduct);
  const [vendorForm, setVendorForm] = useState(newVendor);
  const [offerForm, setOfferForm] = useState(newOffer);
  const [retailPrice, setRetailPrice] = useState('0.00');
  const [vendorCost, setVendorCost] = useState('0.00');
  const [editing, setEditing] = useState({ product: null, vendor: null, offer: null });
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  const request = useCallback(async (service, path, options = {}, auth = credentials) => {
    const base = service === 'catalog' ? catalogUrl : service === 'sourcing' ? sourcingUrl : orderUrl;
    const response = await fetch(base + path, {
      ...options,
      headers: {
        Authorization: `Bearer ${auth.token}`,
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      },
    });
    if (!response.ok) {
      if (response.status === 401 && auth === credentials) setCredentials(null);
      let detail = '';
      try { detail = (await response.json()).detail || ''; } catch (_) { /* ignore */ }
      throw new Error(response.status === 401 ? 'Session expired. Please sign in again.' : `${response.status} ${response.statusText}${detail ? `: ${detail}` : ''}`);
    }
    return response.status === 204 ? null : response.json();
  }, [credentials]);

  const refresh = useCallback(async () => {
    const [nextProducts, nextVendors, nextOffers, nextOrders] = await Promise.all([
      request('catalog', '/api/admin/products'),
      request('sourcing', '/api/admin/vendors'),
      request('sourcing', '/api/admin/offers'),
      request('orders', '/api/admin/orders'),
    ]);
    setProducts(nextProducts); setVendors(nextVendors); setOffers(nextOffers); setOrders(nextOrders);
  }, [request]);

  useEffect(() => { if (credentials) refresh().catch(e => setMessage(e.message)); }, [credentials, refresh]);

  async function signIn(event) {
    event.preventDefault(); setBusy(true); setMessage('');
    try {
      const response = await fetch(`${authUrl}/api/auth/login`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(login),
      });
      if (!response.ok) throw new Error(response.status === 401 ? 'Invalid username or password' : `Authentication service returned ${response.status}`);
      const issued = await response.json();
      const session = { token: issued.accessToken };
      await request('catalog', '/api/admin/products', {}, session);
      await request('sourcing', '/api/admin/vendors', {}, session);
      await request('orders', '/api/admin/orders', {}, session);
      setCredentials(session); setLogin({ username: 'admin', password: '' });
    } catch (e) { setMessage(`Sign in failed: ${e.message}`); }
    finally { setBusy(false); }
  }

  async function save(service, kind, id, value) {
    setBusy(true); setMessage('');
    try {
      await request(service, `/api/admin/${kind}${id ? `/${id}` : ''}`, {
        method: id ? 'PUT' : 'POST', body: JSON.stringify(value),
      });
      await refresh();
      setMessage(`${kind.slice(0, -1)} saved.`);
      if (kind === 'products') { setProductForm(newProduct); setRetailPrice('0.00'); setEditing(v => ({ ...v, product: null })); }
      if (kind === 'vendors') { setVendorForm(newVendor); setEditing(v => ({ ...v, vendor: null })); }
      if (kind === 'offers') { setOfferForm(newOffer); setVendorCost('0.00'); setEditing(v => ({ ...v, offer: null })); }
    } catch (e) { setMessage(`Save failed: ${e.message}`); }
    finally { setBusy(false); }
  }

  async function remove(service, kind, id) {
    const action = kind === 'offers' ? 'Delete' : 'Deactivate';
    if (!window.confirm(`${action} this ${kind.slice(0, -1)}?`)) return;
    setBusy(true); setMessage('');
    try {
      await request(service, `/api/admin/${kind}/${id}`, { method: 'DELETE' });
      await refresh(); setMessage(`${kind.slice(0, -1)} ${action.toLowerCase()}d.`);
    } catch (e) { setMessage(`${action} failed: ${e.message}`); }
    finally { setBusy(false); }
  }

  async function changeOrderStatus(id, status) {
    setBusy(true); setMessage('');
    try {
      await request('orders', `/api/admin/orders/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) });
      await refresh(); setMessage(`Order marked ${status.toLowerCase().replaceAll('_', ' ')}.`);
    } catch (e) { setMessage(`Order update failed: ${e.message}`); }
    finally { setBusy(false); }
  }

  if (!credentials) return <div className="login-page"><form className="login-card" onSubmit={signIn}>
    <div className="brand-mark">H</div><p className="eyebrow">HOMERUN SUPPLY</p><h1>Admin sign in</h1>
    <p className="muted">Manage the catalog and internal vendor sourcing.</p>
    <label>Username<input autoComplete="username" value={login.username} onChange={e => setLogin({ ...login, username: e.target.value })} required /></label>
    <label>Password<input type="password" autoComplete="current-password" value={login.password} onChange={e => setLogin({ ...login, password: e.target.value })} required /></label>
    <button className="primary" disabled={busy}>Sign in</button>{message && <p role="alert" className="error">{message}</p>}
  </form></div>;

  return <div className="shell">
    <aside className="sidebar"><div className="brand"><div className="brand-mark">H</div><div><strong>Homerun</strong><span>Supply admin</span></div></div>
      <nav>{[['orders', 'Orders'], ['products', 'Products'], ['vendors', 'Vendors'], ['offers', 'Vendor mapping']].map(([key, label]) =>
        <button key={key} className={tab === key ? 'selected' : ''} onClick={() => setTab(key)}>{label}</button>)}</nav>
      <button className="signout" onClick={() => { setCredentials(null); setProducts([]); setVendors([]); setOffers([]); setOrders([]); }}>Sign out</button>
    </aside>
    <main><header><div><p className="eyebrow">OPERATIONS</p><h1>{tab === 'offers' ? 'Vendor mapping' : tab[0].toUpperCase() + tab.slice(1)}</h1></div><span className="pill">Internal workspace</span></header>
      {message && <div className={message.includes('failed') ? 'notice error' : 'notice'} role="status">{message}<button onClick={() => setMessage('')}>×</button></div>}

      {tab === 'orders' && <section className="panel"><div className="panel-heading"><h2>Customer orders</h2><span>{orders.length} recent orders</span></div>
        {orders.length === 0 ? <p className="empty">No orders have been placed yet.</p> : orders.map(o => <div className="order-card" key={o.id}>
          <div className="order-title"><div><strong>#{o.id.slice(0, 8).toUpperCase()}</strong><span>{o.customerName} · {o.phone} · {new Date(o.createdAt).toLocaleString()}</span></div><b>{rupees(o.totalMinor)}</b></div>
          <p className="order-address">{o.addressLine}, {o.city} {o.postalCode}</p>
          <p className="order-items">{o.items.map(i => `${i.name} × ${i.quantity}`).join(' · ')}</p>
          <div className="order-footer"><span className="status-chip">{o.status.replaceAll('_', ' ')}</span><span>Cash on delivery</span>
            <div className="row-actions">{(nextOrderStatus[o.status] || []).map(status => <button key={status} disabled={busy} onClick={() => changeOrderStatus(o.id, status)}>{status.replaceAll('_', ' ')}</button>)}</div>
          </div>
        </div>)}
      </section>}

      {tab === 'products' && <section className="content-grid"><div className="panel"><h2>{editing.product ? 'Edit product' : 'Add product'}</h2><p className="muted">Only these product details appear in the customer app.</p>
        <form onSubmit={e => { e.preventDefault(); save('catalog', 'products', editing.product, { ...productForm, priceMinor: toMinor(retailPrice) }); }}>
          <div className="two"><label>SKU<input value={productForm.sku} onChange={e => setProductForm({ ...productForm, sku: e.target.value })} required /></label><label>Unit<input value={productForm.unit} onChange={e => setProductForm({ ...productForm, unit: e.target.value })} required /></label></div>
          <label>Product name<input value={productForm.name} onChange={e => setProductForm({ ...productForm, name: e.target.value })} required /></label>
          <label>Category<input value={productForm.category} onChange={e => setProductForm({ ...productForm, category: e.target.value })} placeholder="Fasteners, welding, paint..." required /></label>
          <label>Description<textarea value={productForm.description} onChange={e => setProductForm({ ...productForm, description: e.target.value })} required /></label>
          <div className="two"><label>Retail price (₹)<input type="number" min="0" step="0.01" value={retailPrice} onChange={e => setRetailPrice(e.target.value)} required /></label><label>Image URL<input type="url" value={productForm.imageUrl || ''} onChange={e => setProductForm({ ...productForm, imageUrl: e.target.value })} /></label></div>
          <label className="check"><input type="checkbox" checked={productForm.active} onChange={e => setProductForm({ ...productForm, active: e.target.checked })} /> Visible to customers</label>
          <div className="actions"><button className="primary" disabled={busy}>Save product</button>{editing.product && <button type="button" onClick={() => { setEditing({ ...editing, product: null }); setProductForm(newProduct); setRetailPrice('0.00'); }}>Cancel</button>}</div>
        </form></div><div className="panel list-panel"><div className="panel-heading"><h2>Catalog</h2><span>{products.length} products</span></div>
          {products.length === 0 ? <p className="empty">No products yet. Add your first item.</p> : products.map(p => <div className="row" key={p.id}><div><strong>{p.name}</strong><span>{p.sku} · {p.category} · {rupees(p.priceMinor)} / {p.unit}</span>{!p.active && <small>Hidden</small>}</div><div className="row-actions"><button onClick={() => { setProductForm(p); setRetailPrice(moneyInput(p.priceMinor)); setEditing({ ...editing, product: p.id }); window.scrollTo(0, 0); }}>Edit</button>{p.active && <button className="danger" onClick={() => remove('catalog', 'products', p.id)}>Hide</button>}</div></div>)}</div></section>}

      {tab === 'vendors' && <section className="content-grid"><div className="panel"><h2>{editing.vendor ? 'Edit vendor' : 'Add vendor'}</h2><p className="muted">Vendor details stay inside admin operations.</p>
        <form onSubmit={e => { e.preventDefault(); save('sourcing', 'vendors', editing.vendor, vendorForm); }}>
          <label>Vendor name<input value={vendorForm.name} onChange={e => setVendorForm({ ...vendorForm, name: e.target.value })} required /></label>
          <label>Email<input type="email" value={vendorForm.email || ''} onChange={e => setVendorForm({ ...vendorForm, email: e.target.value })} /></label>
          <label>Phone<input value={vendorForm.phone || ''} onChange={e => setVendorForm({ ...vendorForm, phone: e.target.value })} /></label>
          <label className="check"><input type="checkbox" checked={vendorForm.active} onChange={e => setVendorForm({ ...vendorForm, active: e.target.checked })} /> Active vendor</label>
          <div className="actions"><button className="primary" disabled={busy}>Save vendor</button>{editing.vendor && <button type="button" onClick={() => { setEditing({ ...editing, vendor: null }); setVendorForm(newVendor); }}>Cancel</button>}</div>
        </form></div><div className="panel list-panel"><div className="panel-heading"><h2>Vendor directory</h2><span>{vendors.length} vendors</span></div>
          {vendors.length === 0 ? <p className="empty">No vendors yet.</p> : vendors.map(v => <div className="row" key={v.id}><div><strong>{v.name}</strong><span>{v.email || 'No email'} · {v.phone || 'No phone'}</span>{!v.active && <small>Inactive</small>}</div><div className="row-actions"><button onClick={() => { setVendorForm(v); setEditing({ ...editing, vendor: v.id }); window.scrollTo(0, 0); }}>Edit</button>{v.active && <button className="danger" onClick={() => remove('sourcing', 'vendors', v.id)}>Deactivate</button>}</div></div>)}</div></section>}

      {tab === 'offers' && <section className="content-grid"><div className="panel"><h2>{editing.offer ? 'Edit mapping' : 'Map a vendor'}</h2><p className="muted">Assign multiple vendors to one product. Costs and availability are internal.</p>
        <form onSubmit={e => { e.preventDefault(); save('sourcing', 'offers', editing.offer, { ...offerForm, costMinor: toMinor(vendorCost) }); }}>
          <label>Product<select value={offerForm.productId} onChange={e => setOfferForm({ ...offerForm, productId: e.target.value })} required><option value="">Select product</option>{products.map(p => <option key={p.id} value={p.id}>{p.name} ({p.sku})</option>)}</select></label>
          <label>Vendor<select value={offerForm.vendorId} onChange={e => setOfferForm({ ...offerForm, vendorId: e.target.value })} required><option value="">Select vendor</option>{vendors.map(v => <option key={v.id} value={v.id}>{v.name}</option>)}</select></label>
          <label>Vendor SKU<input value={offerForm.vendorSku || ''} onChange={e => setOfferForm({ ...offerForm, vendorSku: e.target.value })} /></label>
          <div className="two"><label>Cost (₹)<input type="number" min="0" step="0.01" value={vendorCost} onChange={e => setVendorCost(e.target.value)} required /></label><label>Available quantity<input type="number" min="0" value={offerForm.availableQuantity} onChange={e => setOfferForm({ ...offerForm, availableQuantity: Number(e.target.value) })} required /></label></div>
          <label>Lead time (days)<input type="number" min="0" value={offerForm.leadDays} onChange={e => setOfferForm({ ...offerForm, leadDays: Number(e.target.value) })} required /></label>
          <label className="check"><input type="checkbox" checked={offerForm.active} onChange={e => setOfferForm({ ...offerForm, active: e.target.checked })} /> Active mapping</label>
          <div className="actions"><button className="primary" disabled={busy}>Save mapping</button>{editing.offer && <button type="button" onClick={() => { setEditing({ ...editing, offer: null }); setOfferForm(newOffer); setVendorCost('0.00'); }}>Cancel</button>}</div>
        </form></div><div className="panel list-panel"><div className="panel-heading"><h2>Sourcing options</h2><span>{offers.length} mappings</span></div>
          {offers.length === 0 ? <p className="empty">Map a vendor to a product to start sourcing.</p> : offers.map(o => <div className="row" key={o.id}><div><strong>{products.find(p => p.id === o.productId)?.name || 'Unknown product'}</strong><span>{vendors.find(v => v.id === o.vendorId)?.name || 'Unknown vendor'} · {rupees(o.costMinor)} cost · {o.availableQuantity} available · {o.leadDays} days</span>{!o.active && <small>Inactive</small>}</div><div className="row-actions"><button onClick={() => { setOfferForm(o); setVendorCost(moneyInput(o.costMinor)); setEditing({ ...editing, offer: o.id }); window.scrollTo(0, 0); }}>Edit</button><button className="danger" onClick={() => remove('sourcing', 'offers', o.id)}>Delete</button></div></div>)}</div></section>}
    </main>
  </div>;
}
