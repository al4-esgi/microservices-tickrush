FROM python:3.12-slim AS base
WORKDIR /app
ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app

FROM base AS test
COPY requirements-dev.txt .
RUN pip install --no-cache-dir -r requirements-dev.txt
COPY tests ./tests
RUN pytest -q && touch /tmp/tests-passed

FROM base AS runtime
COPY --from=test /tmp/tests-passed /tmp/tests-passed
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
