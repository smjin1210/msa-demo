import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { App } from './App';

// Global fetch mock
beforeEach(() => {
  const fetchMock = vi.fn().mockImplementation((url: string) => {
    if (url.includes('/products')) {
      return Promise.resolve({
        ok: true,
        json: async () => [
          {
            id: 1,
            name: '테스트 도서',
            description: '테스트 상품 설명',
            price: 25000,
            stock: 10,
            category: '도서',
          },
        ],
      });
    }
    if (url.includes('/orders')) {
      return Promise.resolve({
        ok: true,
        json: async () => [],
      });
    }
    return Promise.resolve({
      ok: true,
      json: async () => ({ status: 'healthy' }),
    });
  });

  vi.stubGlobal('fetch', fetchMock);
});

describe('App Component', () => {
  it('renders application title and navigation tabs', async () => {
    render(<App />);
    expect(screen.getByText('MSA 주문 시스템')).toBeInTheDocument();
    expect(screen.getByText('🛍️ 상품 및 주문')).toBeInTheDocument();
    expect(screen.getByText('📋 주문 내역')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('테스트 도서')).toBeInTheDocument();
    });
  });
});
