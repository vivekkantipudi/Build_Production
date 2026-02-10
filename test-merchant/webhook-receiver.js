const express = require('express');
const crypto = require('crypto');
const app = express();

app.use(express.json({
    verify: (req, res, buf) => {
        req.rawBody = buf;
    }
}));

app.post('/webhook', (req, res) => {
  const signature = req.headers['x-webhook-signature'];
  
  const expectedSignature = crypto
    .createHmac('sha256', 'whsec_test_abc123')
    .update(req.rawBody) 
    .digest('hex');

  if (signature !== expectedSignature) {
    console.log('Invalid signature');
    console.log('Received:', signature);
    console.log('Expected:', expectedSignature);
    return res.status(401).send('Invalid signature');
  }

  console.log('Webhook verified:', req.body.event);
  if(req.body.data && req.body.data.payment) {
      console.log('Payment ID:', req.body.data.payment.id);
  } else if (req.body.data && req.body.data.refund) { // Also log refunds
      console.log('Refund ID:', req.body.data.refund.id);
  }
  
  res.status(200).send('OK');
});

app.listen(4000, () => {
  console.log('Test merchant webhook running on port 4000');
});