FROM python:3.12-alpine
WORKDIR /app
COPY filter_server.py /app/filter_server.py
USER 1000:1000
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
CMD ["python", "-B", "/app/filter_server.py"]
