const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3001;

// Helper to read file and serve
const serve = (res, filePath, type) => {
    fs.readFile(filePath, (err, data) => {
        if (err) { res.writeHead(404); res.end('Not found'); }
        else { res.writeHead(200, { 'Content-Type': type }); res.end(data); }
    });
};

const server = http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');

    if (req.url === '/checkout.js') {
        // Concatenating modal.js + PaymentGateway.js for simplicity in "bundled" output simulation
        const modalJs = fs.readFileSync(path.join(__dirname, 'src/sdk/modal.js'));
        const mainJs = fs.readFileSync(path.join(__dirname, 'src/sdk/PaymentGateway.js'));
        res.writeHead(200, { 'Content-Type': 'application/javascript' });
        res.end(modalJs + '\n' + mainJs);
    } 
    else if (req.url === '/styles.css') {
        serve(res, path.join(__dirname, 'src/sdk/styles.css'), 'text/css');
    }
    else if (req.url.startsWith('/checkout.html')) {
        serve(res, path.join(__dirname, 'src/iframe-content/checkout.html'), 'text/html');
    }
    else if (req.url.startsWith('/dashboard/webhooks')) {
        serve(res, path.join(__dirname, 'src/iframe-content/dashboard-webhooks.html'), 'text/html');
    }
    else if (req.url.startsWith('/dashboard/docs')) {
        serve(res, path.join(__dirname, 'src/iframe-content/dashboard-docs.html'), 'text/html');
    }
    else {
        res.writeHead(404); res.end('Not Found');
    }
});

server.listen(PORT, () => {
    console.log(`Frontend running on http://localhost:${PORT}`);
    console.log(`- SDK: http://localhost:${PORT}/checkout.js`);
    console.log(`- Webhooks: http://localhost:${PORT}/dashboard/webhooks`);
    console.log(`- Docs: http://localhost:${PORT}/dashboard/docs`);
});