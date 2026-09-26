FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 \
    HF_HOME=/app/data/hf \
    VECTOR_STORE_DIR=/app/data/vectors
# CPU-only torch (the default wheel bundles CUDA and is several GB larger)
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.14.0
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
EXPOSE 8002
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8002"]
