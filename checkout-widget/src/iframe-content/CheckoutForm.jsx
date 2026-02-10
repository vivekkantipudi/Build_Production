import React, { useState, useEffect } from 'react';

const CheckoutForm = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [params, setParams] = useState({ key: '', amount: 0, orderId: '' });

  useEffect(() => {
    // Parse URL params when component mounts
    const urlParams = new URLSearchParams(window.location.search);
    setParams({
      key: urlParams.get('key'),
      amount: urlParams.get('amount'),
      orderId: urlParams.get('orderId')
    });
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const response = await fetch('http://localhost:8080/api/v1/payments', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Api-Key': params.key,
          'Idempotency-Key': 'key_' + Date.now()
        },
        body: JSON.stringify({
          amount: parseInt(params.amount),
          currency: 'INR',
          method: 'card',
          order_id: params.orderId
        })
      });

      const data = await response.json();

      if (response.ok) {
        window.parent.postMessage({ type: 'payment.success', data: { paymentId: data.id } }, '*');
      } else {
        throw new Error(data.error || 'Payment failed');
      }
    } catch (err) {
      setError(err.message);
      window.parent.postMessage({ type: 'payment.failed', data: { error: err.message } }, '*');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card" style={{ padding: '20px', border: '1px solid #eee', borderRadius: '8px', fontFamily: 'sans-serif' }}>
      <h2>Pay Securely</h2>
      {error && <div style={{ color: 'red', marginBottom: '10px' }}>{error}</div>}
      
      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '10px' }}>
          <label style={{ display: 'block', marginBottom: '5px' }}>Card Number</label>
          <input 
            type="text" 
            value="4242 4242 4242 4242" 
            disabled 
            style={{ width: '100%', padding: '10px', boxSizing: 'border-box' }}
          />
        </div>
        
        <div style={{ marginBottom: '20px' }}>
          <label style={{ display: 'block', marginBottom: '5px' }}>Expiry</label>
          <input 
            type="text" 
            value="12/25" 
            disabled 
            style={{ width: '100%', padding: '10px', boxSizing: 'border-box' }}
          />
        </div>

        <button 
          type="submit" 
          disabled={loading}
          style={{ 
            width: '100%', 
            padding: '12px', 
            background: '#007bff', 
            color: 'white', 
            border: 'none', 
            borderRadius: '4px', 
            cursor: loading ? 'not-allowed' : 'pointer' 
          }}
        >
          {loading ? 'Processing...' : 'Pay Now'}
        </button>
      </form>
    </div>
  );
};

export default CheckoutForm;