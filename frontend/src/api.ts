import { Product, Order, OrderCreatePayload, HealthCheckResponse } from './types';

const API_BASE = '/api';

export async function fetchProducts(category?: string): Promise<Product[]> {
  const url = category ? `${API_BASE}/products?category=${encodeURIComponent(category)}` : `${API_BASE}/products`;
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`상품 목록 조회 실패 (${response.status})`);
  }
  return response.json();
}

export async function fetchProductById(id: number): Promise<Product> {
  const response = await fetch(`${API_BASE}/products/${id}`);
  if (!response.ok) {
    throw new Error(`상품 상세 조회 실패 (${response.status})`);
  }
  return response.json();
}

export async function fetchOrders(): Promise<Order[]> {
  const response = await fetch(`${API_BASE}/orders`);
  if (!response.ok) {
    throw new Error(`주문 내역 조회 실패 (${response.status})`);
  }
  return response.json();
}

export async function createOrder(payload: OrderCreatePayload): Promise<Order> {
  const response = await fetch(`${API_BASE}/orders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => null);
    const detail = errorData?.detail || `주문 생성 실패 (${response.status})`;
    throw new Error(detail);
  }

  return response.json();
}

export async function checkHealth(service: 'frontend' | 'product' | 'order'): Promise<HealthCheckResponse> {
  let endpoint = '/health';
  if (service === 'product') endpoint = '/api/products/health'; // via proxy or backend
  if (service === 'order') endpoint = '/api/orders/health';
  
  const response = await fetch(endpoint);
  if (!response.ok) {
    throw new Error(`${service} 헬스체크 실패 (${response.status})`);
  }
  return response.json();
}
