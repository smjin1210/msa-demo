import React, { useState, useEffect } from 'react';
import { Product, OrderCreatePayload } from '../types';

interface OrderFormProps {
  products: Product[];
  selectedProduct: Product | null;
  onOrderSubmit: (payload: OrderCreatePayload) => Promise<void>;
  isSubmitting: boolean;
}

export const OrderForm: React.FC<OrderFormProps> = ({
  products,
  selectedProduct,
  onOrderSubmit,
  isSubmitting,
}) => {
  const [productId, setProductId] = useState<number>(1);
  const [quantity, setQuantity] = useState<number>(1);
  const [customerName, setCustomerName] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    if (selectedProduct) {
      setProductId(selectedProduct.id);
      setQuantity(1);
    } else if (products.length > 0 && !products.some(p => p.id === productId)) {
      setProductId(products[0].id);
    }
  }, [selectedProduct, products]);

  const currentProduct = products.find((p) => p.id === productId);
  const totalPrice = currentProduct ? currentProduct.price * quantity : 0;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setValidationError(null);

    if (!customerName.trim()) {
      setValidationError('주문자 성함을 입력해주세요.');
      return;
    }
    if (quantity < 1) {
      setValidationError('주문 수량은 1개 이상이어야 합니다.');
      return;
    }
    if (currentProduct && quantity > currentProduct.stock) {
      setValidationError(`재고(${currentProduct.stock}개)보다 많은 수량을 주문할 수 없습니다.`);
      return;
    }

    try {
      await onOrderSubmit({
        product_id: productId,
        quantity,
        customer_name: customerName.trim(),
        notes: notes.trim() || undefined,
      });
      // 폼 초기화
      setCustomerName('');
      setNotes('');
      setQuantity(1);
    } catch (err: any) {
      setValidationError(err.message || '주문 처리 중 오류가 발생했습니다.');
    }
  };

  return (
    <div className="order-form-card">
      <div className="card-header">
        <h2>🛒 신규 주문 생성</h2>
        <p className="subtitle">상품을 선택하고 주문서를 작성하세요.</p>
      </div>

      {validationError && (
        <div className="alert alert-error">
          ⚠️ {validationError}
        </div>
      )}

      <form onSubmit={handleSubmit} className="order-form">
        <div className="form-group">
          <label htmlFor="product-select">주문 상품</label>
          <select
            id="product-select"
            value={productId}
            onChange={(e) => setProductId(Number(e.target.value))}
            className="form-control"
            disabled={isSubmitting}
          >
            {products.map((p) => (
              <option key={p.id} value={p.id} disabled={p.stock <= 0}>
                {p.name} ({p.price.toLocaleString()}원 / 재고: {p.stock}개) {p.stock <= 0 ? '[품절]' : ''}
              </option>
            ))}
          </select>
        </div>

        <div className="form-row">
          <div className="form-group flex-1">
            <label htmlFor="customer-name">주문자 성함 *</label>
            <input
              id="customer-name"
              type="text"
              placeholder="예: 홍길동"
              value={customerName}
              onChange={(e) => setCustomerName(e.target.value)}
              className="form-control"
              disabled={isSubmitting}
              required
            />
          </div>

          <div className="form-group flex-1">
            <label htmlFor="quantity">수량 *</label>
            <input
              id="quantity"
              type="number"
              min={1}
              max={currentProduct ? currentProduct.stock : 99}
              value={quantity}
              onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
              className="form-control"
              disabled={isSubmitting}
              required
            />
          </div>
        </div>

        <div className="form-group">
          <label htmlFor="order-notes">배송 요청사항 (선택)</label>
          <textarea
            id="order-notes"
            placeholder="예: 문 앞에 놓아주세요."
            rows={2}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            className="form-control"
            disabled={isSubmitting}
          />
        </div>

        <div className="price-summary">
          <div className="summary-item">
            <span>상품 단가:</span>
            <span>{currentProduct ? currentProduct.price.toLocaleString() : 0}원</span>
          </div>
          <div className="summary-item">
            <span>주문 수량:</span>
            <span>{quantity}개</span>
          </div>
          <div className="summary-total">
            <span>최종 결제 금액:</span>
            <span className="total-amount">{totalPrice.toLocaleString()}원</span>
          </div>
        </div>

        <button
          type="submit"
          className="btn btn-primary btn-block btn-lg"
          disabled={isSubmitting || !currentProduct || currentProduct.stock <= 0}
        >
          {isSubmitting ? '주문 처리 중...' : `${totalPrice.toLocaleString()}원 결제 및 주문 완료`}
        </button>
      </form>
    </div>
  );
};
