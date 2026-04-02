# Run Alert Platform TODO

## Primary (Recommended - Docker)
- [x] Start Docker Desktop application
- [x] Run: docker-compose up --build -d (BUILDING - wait 10-20min for first run)
- [ ] Verify: docker-compose ps (all Up)
- [ ] Logs: docker-compose logs alert-platform
- [ ] Access: http://localhost:8080 (admin/metrics endpoints)
- [ ] Test ingestion: curl -X POST http://localhost:8080/ingestion/events -H \"Content-Type: application/json\" -d '{\"appId\":\"test\",\"metric\":\"cpu\",\"value\":90,\"outcome\":\"CRITICAL\"}'

## Alternative (Maven direct - requires local Postgres/Redis/Kafka)
- [x] cd alert-platform
- [x] mvn spring-boot:run (from root: mvn -f alert-platform/pom.xml spring-boot:run)
- [x] Ignore dep connection errors for basic HTTP test

## Notifications (optional)
- Set env: MAIL_USERNAME, MAIL_PASSWORD, SLACK_WEBHOOK_URL

**Progress: Docker build running ✓**
