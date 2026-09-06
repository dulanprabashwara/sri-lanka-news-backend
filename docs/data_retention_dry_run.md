# DR4A Live Verification Report — MongoDB Atlas Development Cluster

**Audit Timestamp**: `2026-09-06T23:49:49+05:30`  
**Target Backend**: `D:\sri-lanka-news\sri-lanka-news-backend`  
**Execution Mode**: **STRICT READ-ONLY (LIVE ATLAS VERIFIED)**  

---

## 1. Atlas Connection & Environment Status

- **MONGODB_URI Available**: `YES`
- **Atlas Connection**: `YES`
- **Database Name**: `sri_lanka_news`
- **Environment Classification**: `DEVELOPMENT / WORKING DATABASE` (`cluster0.arvjpdx.mongodb.net`)

---

## 2. Live DR3 Collection Summary

| Collection | Total | With `expiresAt` | Without `expiresAt` | Eligible for Backfill | Active Protected | Deferred | Immediate Expiry Candidate |
|---|---|---|---|---|---|---|---|
| `notifications` | 1 | 0 | 1 | 1 | 0 | 0 | 0 |
| `notification_events` | 2 | 0 | 2 | 2 | 0 | 0 | 0 |
| `ingestion_runs` | 196 | 0 | 196 | 196 | 0 | 0 | 0 |
| `ingestion_trigger_requests` | 13 | 0 | 13 | 13 | 0 | 0 | 0 |
| `admin_audit_events` | 1 | 0 | 1 | 1 | 0 | 0 | 0 |
| **TOTAL** | **213** | **0** | **213** | **213** | **0** | **0** | **0** |

---

## 3. Expiry Horizon Distribution (Mutually Exclusive Buckets)

Audit Timestamp: `2026-09-06T18:20:28.893Z`

| Collection | ALREADY EXPIRED | 0–7 Days | 8–30 Days | 31–90 Days | 91–180 Days | >180 Days | Total |
|---|---|---|---|---|---|---|---|
| `notifications` | 0 | 0 | 0 | 0 | 1 | 0 | 1 |
| `notification_events` | 0 | 0 | 2 | 0 | 0 | 0 | 2 |
| `ingestion_runs` | 0 | 0 | 0 | 196 | 0 | 0 | 196 |
| `ingestion_trigger_requests` | 0 | 0 | 13 | 0 | 0 | 0 | 13 |
| `admin_audit_events` | 0 | 0 | 0 | 0 | 0 | 1 | 1 |
| **TOTAL** | **0** | **0** | **15** | **196** | **1** | **1** | **213** |

---

## 4. Immediate TTL Activation Impact

- **WOULD BE ELIGIBLE FOR DELETION AFTER TTL ACTIVATION**: **0 documents**
- **Risk Assessment**: **ZERO IMMEDIATE EXPIRY RISK**. All 213 existing historical documents have candidate expiration dates in future horizons (>= 8 days out).

---

## 5. Live Index Audit

| Collection | Index Name | Keys | Unique | TTL Status | expireAfterSeconds |
|---|---|---|---|---|---|
| `notifications` | `_id_` | `_id: 1` | No | NO TTL | None |
| `notifications` | `user_createdAt_idx` | `userId: 1, createdAt: -1` | No | NO TTL | None |
| `notifications` | `user_readAt_idx` | `userId: 1, readAt: 1` | No | NO TTL | None |
| `notification_events` | `status_nextAttemptAt_idx` | `status: 1, nextAttemptAt: 1` | No | NO TTL | None |
| `ingestion_runs` | `idx_source_startedAt` | `sourceId: 1, startedAt: -1` | No | NO TTL | None |
| `ingestion_trigger_requests` | `idx_status_nextAttemptAt` | `status: 1, nextAttemptAt: 1` | No | NO TTL | None |
| `admin_audit_events` | `createdAt` | `createdAt: -1` | No | NO TTL | None |
| `analytics_events` | `receivedAt` | `receivedAt: 1` | No | **YES (30d TTL)** | `2592000` |
| `analytics_daily_visitors` | `idx_daily_visitors_unique` | `date: 1, visitorKey: 1` | Yes | NO TTL | None |

> **INDEX SAFETY CONFIRMATION**: `analytics_events.receivedAt` has live 30-day TTL index (`2592000s`). **Zero TTL indexes exist on any DR3 `expiresAt` fields**.

---

## 6. Analytics Daily Visitors Live Analysis

- **Total Documents**: `0`
- **Oldest Date**: `null`
- **Newest Date**: `null`
- **Older than 90 days**: `0`
- **Older than 180 days**: `0`
- **Older than 365 days**: `0`
- **Recommendation**: Maintain proposed 180-day retention policy (`news.retention.analytics-daily-visitors.days: 180`). Do NOT backfill yet.

---

## 7. Storage Statistics

| Collection | Count | Storage Size | Total Index Size | Avg Obj Size |
|---|---|---|---|---|
| `notifications` | 1 | 36,864 B (36 KB) | 184,320 B (180 KB) | 1,258 B |
| `notification_events` | 2 | 36,864 B (36 KB) | 110,592 B (108 KB) | 345 B |
| `ingestion_runs` | 196 | 77,824 B (76 KB) | 110,592 B (108 KB) | 468 B |
| `ingestion_trigger_requests` | 13 | 36,864 B (36 KB) | 110,592 B (108 KB) | 396 B |
| `admin_audit_events` | 1 | 36,864 B (36 KB) | 73,728 B (72 KB) | 372 B |
| `analytics_daily_visitors` | 0 | 4,096 B (4 KB) | 12,288 B (12 KB) | 0 B |
| `analytics_events` | 191 | 61,440 B (60 KB) | 237,568 B (232 KB) | 274 B |

---

## 8. Backfill Totals & Strategy Recommendations

- **TOTAL ELIGIBLE BACKFILL**: **213**
- **TOTAL ACTIVE PROTECTED**: **0**
- **TOTAL DEFERRED**: **0**
- **TOTAL IMMEDIATE EXPIRY CANDIDATES**: **0**
- **Recommended DR4B Batch Size**: **100** (or **250**). Given the small total volume of 213 eligible documents, a batch size of 100 or 250 will complete historical backfill in 1 to 2 small, safe, non-disruptive update steps.
- **Recommended Ordering**: `_id ASC` (deterministic, indexed on all MongoDB collections).
- **Idempotency Predicate**: `{ "expiresAt": null }`.

---

## 9. Zero-Write Verification

```text
============================================================
ZERO-WRITE VERIFICATION CHECKS
============================================================
MongoDB Documents Modified : 0
MongoDB Documents Deleted  : 0
MongoDB Indexes Changed   : 0
Redis Changed             : 0
Production Behavior Changed: NO
```
