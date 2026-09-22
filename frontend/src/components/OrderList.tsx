import React from 'react';
import { Order } from '../types';

interface OrderListProps {
  orders: Order[];
  isLoading: boolean;
  onRefresh: () => void;
}

export const OrderList: React.FC<OrderListProps> = ({
  orders,
  isLoading,
  onRefresh,
}) => {
  const formatDate = (isoString: string) => {
    try {
      const date = new Date(isoString);
      return date.toLocaleString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="order-list-container">
      <div className="section-header">
        <div>
          <h2>📋 주문 내역 ({orders.length}건)</h2>
          <p className="subtitle">완료된 주문 기록을 실시간으로 확인합니다.</p>
        </div>
        <button
          className="btn btn-secondary"
          onClick={onRefresh}
          disabled={isLoading}
        >
          {isLoading ? '새로고침 중...' : '🔄 새로고침'}
        </button>
      </div>

      {isLoading && orders.length === 0 ? (
        <div className="loading-state">주문 내역을 불러오는 중입니다...</div>
      ) : orders.length === 0 ? (
        <div className="empty-state">
          <p>아직 접수된 주문이 없습니다.</p>
          <p className="subtext">상품 목록에서 상품을 선택하여 주문을 생성해보세요.</p>
        </div>
      ) : (
        <div className="table-responsive">
          <table className="order-table">
            <thead>
              <tr>
                <th>주문번호</th>
                <th>주문 상품</th>
                <th>단가</th>
                <th>수량</th>
                <th>결제 금액</th>
                <th>주문자</th>
                <th>상태</th>
                <th>주문 일시</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id}>
                  <td className="order-id">#{order.id}</td>
                  <td className="product-name">
                    <strong>{order.product_name}</strong>
                    {order.notes && <div className="order-notes">💬 {order.notes}</div>}
                  </td>
                  <td>{order.unit_price.toLocaleString()}원</td>
                  <td>{order.quantity}개</td>
                  <td className="order-total">{order.total_price.toLocaleString()}원</td>
                  <td>{order.customer_name}</td>
                  <td>
                    <span className="badge badge-success">{order.status}</span>
                  </td>
                  <td className="order-time">{formatDate(order.created_at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
