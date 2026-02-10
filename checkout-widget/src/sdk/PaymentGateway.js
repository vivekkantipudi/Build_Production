import { createModalElement } from './modal';

class PaymentGateway {
  constructor(options) {
    if (!options.key || !options.orderId) {
        console.error("PaymentGateway: Missing 'key' or 'orderId'");
        return;
    }
    this.options = options;
    this.baseUrl = 'http://localhost:3001'; 
    this.modal = null;
    this.messageListener = null;
  }

  open() {
    const { modalWrapper, closeBtn } = createModalElement(this.baseUrl, this.options);
    this.modal = modalWrapper;
    document.body.appendChild(this.modal);

    this.messageListener = this.handleMessage.bind(this);
    window.addEventListener('message', this.messageListener);

    closeBtn.addEventListener('click', () => this.close());
    const overlay = this.modal.querySelector('.modal-overlay');
    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) this.close();
    });
  }

  handleMessage(event) {
    const { type, data } = event.data;
    if (type === 'payment.success') {
      if (this.options.onSuccess) this.options.onSuccess(data);
      this.close();
    } else if (type === 'payment.failed') {
      if (this.options.onFailure) this.options.onFailure(data);
    } else if (type === 'close_modal') {
      this.close();
    }
  }

  close() {
    if (this.modal) {
      document.body.removeChild(this.modal);
      this.modal = null;
    }
    if (this.messageListener) {
        window.removeEventListener('message', this.messageListener);
    }
    if (this.options.onClose) this.options.onClose();
  }
}

export default PaymentGateway;