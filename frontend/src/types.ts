export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  stock: number;
  category: string;
}

export interface OrderCreatePayload {
  product_id: number;
  quantity: number;
  customer_name: string;
  notes?: string;
}

export interface Order {
  id: number;
  product_id: number;
  product_name: string;
  unit_price: number;
  quantity: number;
  total_price: number;
  customer_name: string;
  status: string;
  notes?: string;
  created_at: string;
}

export interface HealthCheckResponse {
  status: string;
  service: string;
  version?: string;
  database?: string;
}
