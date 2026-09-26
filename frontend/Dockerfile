# Production build: compile the bundle with Node, serve the static files with nginx.
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG VITE_BACKEND_URL=http://localhost:8080
ARG VITE_ML_SERVICE_URL=http://localhost:8001
ARG VITE_RAG_SERVICE_URL=http://localhost:8002
ENV VITE_BACKEND_URL=$VITE_BACKEND_URL \
    VITE_ML_SERVICE_URL=$VITE_ML_SERVICE_URL \
    VITE_RAG_SERVICE_URL=$VITE_RAG_SERVICE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
