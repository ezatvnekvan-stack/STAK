FROM python:3.12-alpine
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY core/ core/
COPY server/ server/
COPY filter_server.py .
RUN mkdir -p /data /library /source
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 CMD python -c "import os,json,urllib.request; r=json.load(urllib.request.urlopen('http://127.0.0.1:'+os.getenv('STAK_BIND_PORT','8055')+'/health',timeout=5)); assert r.get('ok') is True"
STOPSIGNAL SIGTERM
CMD ["python", "-m", "server.main"]
