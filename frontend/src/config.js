export const SERVICES = [
  { name: 'Backend', url: import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080' },
  { name: 'ML Service', url: import.meta.env.VITE_ML_SERVICE_URL || 'http://localhost:8001' },
  { name: 'RAG Service', url: import.meta.env.VITE_RAG_SERVICE_URL || 'http://localhost:8002' },
];

export const BACKEND_URL = SERVICES[0].url;
