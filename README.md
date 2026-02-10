## Production Payment Gateway

A resilient, asynchronous payment gateway featuring Redis-based job queues, exponential backoff retries, webhook delivery, and an embeddable JavaScript SDK. This system is designed to handle high-concurrency payment processing with "At-Least-Once" delivery guarantees.



## Setup Instructions

Prerequisites

Docker \& Docker Compose (installed and running)

Node.js v16+ (required for SDK development)

### 1. Start the Infrastructure

This command launches all required services, including:



Backend API

Worker

Redis

PostgreSQL

\# Build and start services in the background
```bash
docker-compose up -d --build



\# Verify services are running

docker ps
```
### 2. Start the Frontend SDK Server

The SDK is served separately to simulate a CDN / external host environment.


```bash
cd checkout-widget

npm install

npm run build



\# Runs on port 3001

node server.js
```

### 3. (Optional) Start Test Merchant Receiver

Use this service to verify webhook deliveries locally.


```bash
cd test-merchant

npm install



\# Runs on port 4000

node webhook-receiver.js
```

## Environment Configuration

The system is pre-configured using the docker-compose.yml file.

```md

Service	Variable	Default Value	Description

API	DATABASE\_URL	postgresql://...	Connection string for the PostgreSQL container

API	REDIS\_URL	redis://redis:6379	Connection to the Redis queue

Worker	WEBHOOK\_RETRY\_INTERVALS\_TEST	false	Set to true to use 5s retry intervals for testing

Frontend	PORT	3001	Port used to serve checkout.js
```

## API Documentation

Base URL http://localhost:8000/api/v1

Authentication

All endpoints require the following request header: X-Api-Key: key\_test\_123



1. Create Payment

Initiates an asynchronous payment request.



Endpoint POST /payments

Headers

Idempotency-Key (Optional): Unique string to prevent duplicate charges
```bash
Request Body

{

&nbsp; "amount": 5000,

&nbsp; "currency": "INR",

&nbsp; "method": "card",

&nbsp; "order\_id": "order\_12345"

}
```

Response (201 Created)
```bash
{

&nbsp; "id": "pay\_a1b2...",

&nbsp; "status": "pending",

&nbsp; "created\_at": "2026-01-23T10:00:00Z"

}
```
2. Capture Payment

Captures a successfully authorized payment.



Endpoint

POST /payments/{id}/capture



Response (200 OK)

{

&nbsp; "status": "success",

&nbsp; "captured": true

}

3. Initiate Refund

Starts an asynchronous refund process.



Endpoint
```
POST /payments/{id}/refunds
```



Request Body
```bash
{

&nbsp; "amount": 500,

&nbsp; "reason": "Customer return"

}
```

Response (201 Created)
```bash
{

&nbsp; "id": "rfnd\_x9y8...",

&nbsp; "status": "pending"

}
```

4. Get Refund Details

Retrieves the status of a refund request.



Endpoint

```
GET /refunds/{id}
```



Response (200 OK)
```
{

&nbsp; "id": "rfnd\_x9y8...",

&nbsp; "status": "processed",

&nbsp; "processed\_at": "2026-01-23T10:35:00Z"

}
```

5. Get Webhook Logs

View the history of webhook delivery attempts.



Endpoint
```
GET /webhooks
```



Response (200 OK)
```
{

&nbsp; "data": \[ ... ]

}
```

6. Manual Retry Webhook

Force an immediate retry for a failed webhook delivery.



Endpoint

POST /webhooks/{id}/retry



7. Job Queue Status (Test Endpoint)

Returns the internal state of the job queue. Required for automated evaluation.



Endpoint

GET /test/jobs/status


Response (200 OK)
```
{

&nbsp; "pending": 0,

&nbsp; "processing": 0,

&nbsp; "worker\_status": "running"

}
```

Testing Instructions

Merchant Dashboard

URL: http://localhost:3001/dashboard-webhooks.html

Features: Configure Webhook URLs, view delivery logs, and manually retry failed webhooks.

End-to-End "Happy Path"

Open the checkout page in your browser: http://localhost:3001/checkout.html.



Open the browser console (F12)



Click Pay Now



Verify the UI behavior:



Button changes to "Processing..."

Success message is displayed after completion

Check the Webhook Receiver terminal:

Confirm the payment.success event is received

Verify Asynchronous Refund

Copy the payment\_id from the successful payment step.



Run the following curl command:



curl -X POST http://localhost:8000/api/v1/payments/{PAYMENT\_ID}/refunds \\

-H "X-Api-Key: key\_test\_123" \\

-H "Content-Type: application/json" \\

-d '{"amount": 100, "reason": "Test"}'

Observe the webhook receiver for the refund.processed event.

Webhook Integration Guide

Merchants must verify the X-Webhook-Signature header to ensure webhook requests are genuine and untampered.



Signature Logic

Webhook signatures are generated using HMAC-SHA256: HMAC-SHA256( Payload\_JSON\_String, Webhook\_Secret )



Node.js Verification Example

The following example demonstrates how to verify webhook signatures using HMAC-SHA256 in Node.js.



const crypto = require('crypto');



// Middleware to capture RAW body buffer (Crucial!)

app.use(express.json({

&nbsp;   verify: (req, res, buf) => { 

&nbsp;       req.rawBody = buf; 

&nbsp;   }

}));



app.post('/webhook', (req, res) => {

&nbsp;   const signature = req.headers\['x-webhook-signature'];

&nbsp;   const secret = 'whsec\_test\_abc123'; // From Dashboard



&nbsp;   const expected = crypto

&nbsp;       .createHmac('sha256', secret)

&nbsp;       .update(req.rawBody) // Use raw buffer

&nbsp;       .digest('hex');



&nbsp;   if (signature === expected) {

&nbsp;       console.log('Verified:', req.body.event);

&nbsp;       res.sendStatus(200);

&nbsp;   } else {

&nbsp;       console.log('Forged Request');

&nbsp;       res.sendStatus(401);

&nbsp;   }

});

SDK Integration Guide

Embed the payment gateway on any website using our JavaScript SDK.



1\. Include the Script

Add the following script tag inside your HTML <body>:



<script src="http://localhost:3001/checkout.js"></script>

2\. Initialize and Open

Use the following JavaScript code to initialize the payment gateway and open the checkout modal.



const gateway = new PaymentGateway({

&nbsp;   key: 'key\_test\_123',        // Your Public API Key

&nbsp;   orderId: 'order\_999',       // Unique Order ID

&nbsp;   onSuccess: (data) => {

&nbsp;       console.log('Payment Success:', data.paymentId);

&nbsp;       // Redirect to thank you page

&nbsp;   },

&nbsp;   onFailure: (error) => {

&nbsp;       console.error('Payment Failed:', error.message);

&nbsp;   }

});



// Trigger the checkout modal

gateway.open();

Troubleshooting

500 Error on "Pay Now"

Ensure docker-compose services are running

Confirm the database is properly seeded

Try resetting all data:

docker-compose down -v

Webhook Signature Failure (401)

Ensure you are verifying the raw request body

Do not use the parsed JSON object for signature validation

CORS Errors

Confirm the API is running on port 8000 (mapped to 8080)

Ensure the frontend SDK is running on port 3001

