import React from 'react';
import { Product } from '../types';

interface ProductListProps {
  products: Product[];
  isLoading: boolean;
  onSelectProduct: (product: Product) => void;
  selectedCategory: string;
  onSelectCategory: (category: string) => void;
}

export const ProductList: React.FC<ProductListProps> = ({
  products,
  isLoading,
  onSelectProduct,
  selectedCategory,
  onSelectCategory,
}) => {
  const categories = ['전체', '도서', '굿즈', '의류'];

  return (
    <div className="product-list-container">
      <div className="section-header">
        <h2>📦 상품 목록</h2>
        <div className="category-filters">
          {categories.map((cat) => (
            <button
              key={cat}
              className={`filter-btn ${selectedCategory === cat ? 'active' : ''}`}
              onClick={() => onSelectCategory(cat)}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="loading-state">상품 정보를 불러오는 중입니다...</div>
      ) : products.length === 0 ? (
        <div className="empty-state">등록된 상품이 없습니다.</div>
      ) : (
        <div className="product-grid">
          {products.map((product) => (
            <div key={product.id} className="product-card">
              <div className="product-card-header">
                <span className="badge category-badge">{product.category}</span>
                <span className="stock-info">재고 {product.stock}개</span>
              </div>
              <h3 className="product-title">{product.name}</h3>
              <p className="product-desc">{product.description}</p>
              <div className="product-card-footer">
                <div className="product-price">
                  {product.price.toLocaleString()}
                  <span className="unit">원</span>
                </div>
                <button
                  className="btn btn-primary"
                  onClick={() => onSelectProduct(product)}
                  disabled={product.stock <= 0}
                >
                  {product.stock > 0 ? '주문하기' : '품절'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
