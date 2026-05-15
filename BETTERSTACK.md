# Better Stack / Grafana Cloud free — quick wiring

You don't need Loki, Promtail, Vector, or any agent. The app already writes
structured JSON to stdout (`LOG_FORMAT=json`). Pick whichever tier of free
service fits.

## Better Stack — Logs (formerly Logtail)

1. Create a new Source → "Docker"
2. Better Stack gives you a `BETTER_STACK_SOURCE_TOKEN`.
3. Run the Better Stack vector container alongside your app:

```yaml
# add to docker-compose.yml
  vector:
    image: timberio/vector:0.40.0-debian
    restart: unless-stopped
    environment:
      BETTER_STACK_SOURCE_TOKEN: ${BETTER_STACK_SOURCE_TOKEN}
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - ./vector.toml:/etc/vector/vector.toml:ro
    depends_on:
      - app
```

`vector.toml`:

```toml
[sources.docker]
type = "docker_logs"

[sinks.betterstack]
type = "http"
inputs = ["docker"]
uri = "https://in.logs.betterstack.com"
encoding.codec = "json"
auth.strategy = "bearer"
auth.token = "${BETTER_STACK_SOURCE_TOKEN}"
```

That's it. App stdout flows to Better Stack with no changes inside the Java app.

## Uptime Kuma

1. Run Uptime Kuma somewhere (it's a single Docker container).
2. Add a HTTP(s) monitor → `https://your-host/actuator/health/liveness`.
3. Set interval to 60s. Done.

## Grafana Cloud free tier

The free tier gives 50 GB logs / 14 days, plus Prometheus metrics.

- **Logs**: same Vector setup as Better Stack, swap the sink to Grafana Cloud
  Loki push endpoint with the API key Grafana gives you. No need to host Loki.
- **Metrics**: Grafana Cloud has a hosted Prometheus. Point its remote-write
  scrape at `https://your-host/actuator/prometheus`. Spring already emits
  Micrometer metrics through that endpoint.

Cost at trial scale: $0.
