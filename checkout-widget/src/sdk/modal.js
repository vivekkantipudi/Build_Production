import './styles.css'; // Webpack will bundle the CSS

export function createModalElement(baseUrl, options) {
    const modalWrapper = document.createElement('div');
    modalWrapper.id = 'payment-gateway-modal';
    modalWrapper.setAttribute('data-test-id', 'payment-modal');

    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';

    const content = document.createElement('div');
    content.className = 'modal-content';

    const iframe = document.createElement('iframe');
    iframe.setAttribute('data-test-id', 'payment-iframe');
    
    // Note: In a real React setup, you would build CheckoutForm.jsx into an HTML file.
    // For this deliverable, we point to the HTML endpoint that renders it.
    const params = new URLSearchParams({
        key: options.key,
        orderId: options.orderId,
        amount: options.amount || '5000'
    });
    iframe.src = `${baseUrl}/checkout.html?${params.toString()}`;

    const closeBtn = document.createElement('button');
    closeBtn.setAttribute('data-test-id', 'close-modal-button');
    closeBtn.className = 'close-button';
    closeBtn.innerHTML = '×';

    content.appendChild(iframe);
    content.appendChild(closeBtn);
    overlay.appendChild(content);
    modalWrapper.appendChild(overlay);

    return { modalWrapper, closeBtn };
}