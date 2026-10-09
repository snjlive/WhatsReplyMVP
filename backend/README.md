# Private AI proxy

Python 3.11+; no dependencies. Android POSTs JSON to `https://HOST/draft` with a separate bearer access token. OpenAI key exists only in the server's environment. Responses API uses `store:false`; the proxy has no conversation database or message logging. Only incoming/sent history for the selected contact is submitted; prior drafts are excluded except the draft being revised.

Deployment requires a hosting account, HTTPS domain and an OpenAI API key with API billing. These are not provided by a ChatGPT subscription. Do not put a provider key in the Android settings or build. Configure the host's secret environment variables `OPENAI_API_KEY` and `WHATSREPLY_ACCESS_TOKEN` (32+ random characters), then run the Dockerfile behind the host's HTTPS ingress. The phone needs only the `/draft` URL and the separate access token, entered once in ⚙.

For self-hosting, `python server.py` binds loopback by default; terminate HTTPS with Caddy/nginx. The Docker image binds port 8080 for a managed HTTPS ingress. Health check: GET `/health` (liveness only; no credentials returned). Generate a device access code with `python -c 'import secrets; print(secrets.token_urlsafe(32))'`, then enter it via the hosting provider's secret manager.

The single-user proxy limits requests to 10/minute and 200/day (configurable), two concurrent generations, 100 KB bodies, 16 messages, bounded output, and 55 second provider timeouts. Counters are process-local and reset on restart: configure provider project spend limits too; do not scale this MVP to multiple replicas. Rotate access token by changing the server secret and updating phone settings. No redirect forwarding and no plaintext HTTP are accepted by Android.

Tests: `python -m unittest discover -s backend -v` from repo root. Tests use a fake provider; live model generation requires server credentials and was not verified without them.

Provider schema: https://developers.openai.com/api/docs/guides/text
