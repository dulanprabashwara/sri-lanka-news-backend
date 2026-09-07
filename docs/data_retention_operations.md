# Data Retention Operational Runbook & System Baseline (DR1 – DR7)

**System**: Sri Lankan Multilingual News Intelligence Platform  
**Backend Module**: `sri-lanka-news-backend`  
**Authoritative Version**: DR7 Final Retention Hardening  

---

## 1. Executive Summary & Policy Overview

The Data Retention framework provides deterministic, automated data lifecycle management across MongoDB storage and Redis / Valkey event streams. Retention operations are designed and verified to avoid deletion of pending or undelivered work under the tested safety invariants.

### Storage & Retention Principles
1. **Zero Unintended Deletions**: Permanent domain data (articles, sources, user accounts, preferences) are strictly excluded from automated retention.
2. **MongoDB TTL Indexes**: Ephemeral collections use background MongoDB TTL threads configured with `expireAfterSeconds = 0` and explicit `expiresAt` timestamps calculated at document creation or completion time.
3. **ACK-Safe Redis Stream Trimming**: Work streams use `XTRIM MINID` based on minimum unacknowledged consumer group offsets and strict time-based history windows (floor 1 day, default 7 days).
4. **Dead-Letter Safety (DLQ)**: `article-discovered-dlq` is explicitly **excluded** from automated trimming. It must be manually resolved and inspected.
5. **Observability First**: Read-only monitoring services (`RetentionMongoStorageService`, `RetentionRedisHealthService`) and the `/api/v1/admin/retention` endpoint expose live health, index status, memory usage, stream lag, and typed system warnings.

---

## 2. Collection & Stream Retention Inventory

### MongoDB Storage Inventory

| Collection | Role | Retention Policy | Index / Trigger Field | Expiry Mechanics |
| :--- | :--- | :--- | :--- | :--- |
| `notifications` | Delivery records | 180d (read) / 365d (unread) | `{ expiresAt: 1 }`, `expireAfterSeconds = 0` | `expiresAt` computed at creation |
| `notification_events` | Outbox event log | 30d (published) / Deferred (failed) | `{ expiresAt: 1 }`, `expireAfterSeconds = 0` | `expiresAt` set upon successful dispatch |
| `ingestion_runs` | Ingestion pipeline state | 90 days | `{ expiresAt: 1 }`, `expireAfterSeconds = 0` | `expiresAt = startTime + 90d` |
| `ingestion_trigger_requests` | API trigger audit | 30 days | `{ expiresAt: 1 }`, `expireAfterSeconds = 0` | `expiresAt = requestedAt + 30d` |
| `admin_audit_events` | Governance audit log | 365 days | `{ expiresAt: 1 }`, `expireAfterSeconds = 0` | `expiresAt = timestamp + 365d` |
| `analytics_events` | Raw user analytics | 30 days | `{ receivedAt: 1 }`, `expireAfterSeconds = 2592000` | Native Mongo TTL on `receivedAt` |
| `analytics_daily_visitors` | Aggregated metrics | Permanent / Deferred | None | Permanent long-term candidate |
| `articles`, `stories`, `sources`, `notification_preferences`, `user_bookmarks`, `user_follows`, `ingestion_source_settings` | Permanent Core Domain | **NEVER EXPIRE** | **NO TTL INDEX ALLOWED** | Guaranteed permanent storage |

### Redis / Valkey Stream Inventory

| Stream Name | Category | History Target | Safety Constraint | Trimming Command |
| :--- | :--- | :--- | :--- | :--- |
| `article-discovered` | Work Stream | 7 Days | `MINID <= min(Consumer Group Last-Delivered/Pending)` | `XTRIM article-discovered MINID <threshold>` |
| `notification-events` | Work Stream | 7 Days | `MINID <= min(Consumer Group Last-Delivered/Pending)` | `XTRIM notification-events MINID <threshold>` |
| `article-discovered-dlq` | Dead Letter Queue | Manual Only | **AUTOMATIC TRIMMING INACTIVE** | Manual resolution required |

---

## 3. Observability & Monitoring via `/api/v1/admin/retention`

### Endpoint Specification
- **URL**: `GET /api/v1/admin/retention`
- **Security**: Requires verified Supabase JWT with `ADMIN_USER_IDS` / sub authorization (`@PreAuthorize("@adminAuthorization.isAdmin(authentication)")`).
- **Privacy & Safety**: Read-only operation. Exposes 0 PII and 0 credentials/connection strings.

### Sample Response Structure
```json
{
  "mongo": {
    "available": true,
    "dataSize": 15420982,
    "storageSize": 20480000,
    "indexSize": 4120900,
    "collections": [
      {
        "name": "articles",
        "documentCount": 42150,
        "sizeBytes": 12400500,
        "totalIndexSizeBytes": 2048000
      }
    ]
  },
  "mongoTtlIndexHealth": [
    {
      "collectionName": "notifications",
      "expectedKey": "expiresAt",
      "status": "HEALTHY",
      "message": "TTL index exists with expireAfterSeconds=0"
    }
  ],
  "mongoTtlDocumentHealth": [
    {
      "collectionName": "notifications",
      "totalDocuments": 1250,
      "withExpiresAt": 1250,
      "withoutExpiresAt": 0,
      "expiredAwaitingCleanup": 0,
      "missingExpiryClassification": "ACTIVE PROTECTED"
    }
  ],
  "redisMemory": {
    "available": true,
    "usedMemory": 14205800,
    "usedMemoryHuman": "13.55M",
    "maxmemory": 1073741824,
    "maxmemoryHuman": "1.00G",
    "maxmemoryPolicy": "noeviction"
  },
  "redisStreams": [
    {
      "streamName": "article-discovered",
      "xlen": 737,
      "firstEntryId": "1788416371059-0",
      "oldestEntryAge": "PT48H12M",
      "lastEntryId": "1788549120000-0",
      "newestEntryAge": "PT1M",
      "consumerGroups": [
        {
          "groupName": "article-processing-group",
          "consumerCount": 2,
          "pendingCount": 0,
          "minPendingId": null,
          "oldestPendingAge": null
        }
      ],
      "status": "HEALTHY",
      "available": true
    }
  ],
  "redisScheduler": {
    "retentionEnabled": true,
    "applyMode": false,
    "scheduleEnabled": false,
    "cronSchedule": "0 0 */6 * * *",
    "status": "IMPLEMENTED / MANUAL ACTIVATION REQUIRED"
  },
  "warnings": []
}
```

---

## 4. Emergency Operational Runbook & Alert Playbook

### Alert Code: `MONGO_TTL_INDEX_MISSING`
- **Severity**: CRITICAL
- **Cause**: A retention collection (`notifications`, `notification_events`, `ingestion_runs`, `ingestion_trigger_requests`, `admin_audit_events`) is missing its `{ expiresAt: 1 }` TTL index.
- **Action**: Run `RetentionTtlActivationService.activateAllTtlIndexes()` or trigger backend startup TTL initialization. Do not drop or recreate core indexes manually.

### Alert Code: `REDIS_PENDING_OLD`
- **Severity**: WARNING (>1 day) / CRITICAL (>3 days)
- **Cause**: A consumer group has unacknowledged (pending) stream messages older than 1 or 3 days.
- **Impact**: Retention protection boundary halts `XTRIM` at the minimum pending entry ID to prevent unacknowledged message loss.
- **Action**:
  1. Inspect stream pending entries using Redis CLI: `XPENDING article-discovered <group-name> - + 10`.
  2. Inspect consumer logs for pipeline stuckness or unhandled processing exceptions.
  3. Acknowledge stuck entries once safely reprocessed: `XACK article-discovered <group-name> <message-id>`.

### Alert Code: `DLQ_NONEMPTY`
- **Severity**: WARNING
- **Cause**: `article-discovered-dlq` contains failed event records.
- **Impact**: Automatic trimming is disabled on DLQ streams to protect failure history.
- **Action**:
  1. Fetch sample DLQ records: `XRANGE article-discovered-dlq - + COUNT 10`.
  2. Inspect payload error causes.
  3. Replay valid records or purge resolved dead-letters manually via operational script.

---

## 5. Controlled Activation & Scheduler Guide

By default, automated background trimming is disabled:
```yaml
news:
  retention:
    redis:
      enabled: true
      apply: false
      schedule-enabled: false
      schedule-cron: "0 0 */6 * * *"
```

### Transition to Autonomous Trimming
When production stability is established and verified via `/api/v1/admin/retention`:
1. Set environment variables:
   ```env
   NEWS_RETENTION_REDIS_APPLY=true
   NEWS_RETENTION_REDIS_SCHEDULE_ENABLED=true
   ```
2. Restart backend nodes or reload Spring configuration.
3. Monitor `redis_retention_trimmed` log statements every 6 hours.

---

## 6. Baseline Verification Sign-off

- **DR1 Storage Audit**: Completed ✅
- **DR2 Configuration**: Completed ✅
- **DR3 Mongo Expiry Lifecycle**: Completed ✅
- **DR4 Backfill**: Completed ✅
- **DR5 Mongo TTL Activation**: Verified Live ✅
- **DR6 Redis Retention & ACK-Safe Trimming**: Applied & Reconciled ✅
- **DR7 Storage Monitoring & Hardening**: Completed & Verified ✅
