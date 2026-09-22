import React, { useState, useEffect, useCallback } from 'react';
import { Product, Order, OrderCreatePayload } from './types';
import { fetchProducts, fetchOrders, createOrder } from './api';
import { ProductList } from './components/ProductList';
import { OrderForm } from './components/OrderForm';
import { OrderList } from './components/OrderList';
import './App.css';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'shop' | 'orders'>('shop');
  const [products, setProducts] = useState<Product[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [category, setCategory] = useState<string>('전체');

  const [isLoadingProducts, setIsLoadingProducts] = useState<boolean>(false);
  const [isLoadingOrders, setIsLoadingOrders] = useState<boolean>(false);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const showNotification = (type: 'success' | 'error', message: string) => {
    setNotification({ type, message });
    setTimeout(() => {
      setNotification(null);
    }, 4000);
  };

  const loadProducts = useCallback(async () => {
    setIsLoadingProducts(true);
    try {
      const data = await fetchProducts(category === '전체' ? undefined : category);
      setProducts(data);
    } catch (err: any) {
      console.error('Failed to load products:', err);
      showNotification('error', `상품 목록 로드 실패: ${err.message}`);
    } finally {
      setIsLoadingProducts(false);
    }
  }, [category]);

  const loadOrders = useCallback(async () => {
    setIsLoadingOrders(true);
    try {
      const data = await fetchOrders();
      setOrders(data);
    } catch (err: any) {
      console.error('Failed to load orders:', err);
      showNotification('error', `주문 내역 로드 실패: ${err.message}`);
    } finally {
      setIsLoadingOrders(false);
    }
  }, []);

  useEffect(() => {
    loadProducts();
  }, [loadProducts]);

  useEffect(() => {
    if (activeTab === 'orders') {
      loadOrders();
    }
  }, [activeTab, loadOrders]);

  const handleSelectProduct = (product: Product) => {
    setSelectedProduct(product);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleOrderSubmit = async (payload: OrderCreatePayload) => {
    setIsSubmitting(true);
    try {
      const created = await createOrder(payload);
      showNotification('success', `주문(#${created.id})이 성공적으로 생성되었습니다!`);
      setSelectedProduct(null);
      // 상품 및 주문 목록 갱신
      loadProducts();
      loadOrders();
      // 주문 탭으로 이동
      setActiveTab('orders');
    } catch (err: any) {
      throw err;
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="app-container">
      {/* 상단 헤더 */}
      <header className="app-header">
        <div className="header-content">
          <div className="logo-section">
            <span className="logo-icon">🚀</span>
            <div>
              <h1 className="logo-title">MSA 주문 시스템</h1>
              <span className="logo-badge">TeamCity CI/CD 데모</span>
            </div>
          </div>

          <div className="nav-tabs">
            <button
              className={`nav-tab ${activeTab === 'shop' ? 'active' : ''}`}
              onClick={() => setActiveTab('shop')}
            >
              🛍️ 상품 및 주문
            </button>
            <button
              className={`nav-tab ${activeTab === 'orders' ? 'active' : ''}`}
              onClick={() => setActiveTab('orders')}
            >
              📋 주문 내역 {orders.length > 0 && <span className="tab-count">{orders.length}</span>}
            </button>
          </div>
        </div>
      </header>

      {/* 알림 메시지 */}
      {notification && (
        <div className={`notification toast-${notification.type}`}>
          {notification.type === 'success' ? '✅ ' : '❌ '}
          {notification.message}
        </div>
      )}

      {/* 메인 컨텐츠 영역 */}
      <main className="app-main">
        {activeTab === 'shop' ? (
          <div className="shop-layout">
            <div className="shop-main">
              <ProductList
                products={products}
                isLoading={isLoadingProducts}
                onSelectProduct={handleSelectProduct}
                selectedCategory={category}
                onSelectCategory={setCategory}
              />
            </div>
            <aside className="shop-sidebar">
              <OrderForm
                products={products}
                selectedProduct={selectedProduct}
                onOrderSubmit={handleOrderSubmit}
                isSubmitting={isSubmitting}
              />
            </aside>
          </div>
        ) : (
          <OrderList
            orders={orders}
            isLoading={isLoadingOrders}
            onRefresh={loadOrders}
          />
        )}
      </main>

      {/* 푸터 */}
      <footer className="app-footer">
        <p>RKE2 v1.33.5 Cluster on AWS EC2 · FastAPI + PostgreSQL + React Nginx Architecture</p>
      </footer>
    </div>
  );
};
