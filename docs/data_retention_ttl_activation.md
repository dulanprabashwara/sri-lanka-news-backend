# Phase DR5 — MongoDB TTL Activation Record

## Operational Summary

This document serves as the authoritative operational record for Phase DR5 (MongoDB TTL Activation) on the Sri Lankan Multilingual News Intelligence Platform backend.

All 5 single-field MongoDB TTL indexes were created in a staged, safe execution against the live MongoDB Atlas DEVELOPMENT / WORKING database (`sri_lanka_news`) after strict safety gate verification confirmed zero immediate-expiry candidate records.

---

## Targeted Collections & TTL Index Specifications

| Stage | Collection Name | Target Field | Index Name | Expire After Seconds | Status | Pre-Activation Count | Post-Activation Count |
|---|---|---|---|---|---|---|---|
| 1 | `admin_audit_events` | `expiresAt` | `ttl_admin_audit_events_expiresAt` | `0` | **CREATED** | 1 | 1 |
| 2 | `notifications` | `expiresAt` | `ttl_notifications_expiresAt` | `0` | **CREATED** | 1 | 1 |
| 3 | `notification_events` | `expiresAt` | `ttl_notification_events_expiresAt` | `0` | **CREATED** | 2 | 2 |
| 4 | `ingestion_trigger_requests` | `expiresAt` | `ttl_ingestion_triggers_expiresAt` | `0` | **CREATED** | 13 | 13 |
| 5 | `ingestion_runs` | `expiresAt` | `ttl_ingestion_runs_expiresAt` | `0` | **CREATED** | 196 | 196 |
| **TOTAL** | | | | | | **213** | **213** |

---

## Safety Gate & Pre-Activation Verification

Prior to index creation, safety gate checks confirmed:

1. **Immediate-Expiry Candidates**: `0` records across all 5 target collections had `expiresAt <= NOW`.
2. **Backfill Integrity**: 100% of historical records (213/213) contained valid, non-null `expiresAt` timestamps.
3. **Earliest Future Expiration Baseline**:
   - `admin_audit_events`: `2027-09-03T10:17:19.174Z`
   - `notifications`: `2027-03-03T08:33:44.189Z`
   - `notification_events`: `2026-10-04T07:10:29.149Z`
   - `ingestion_trigger_requests`: `2026-10-03T09:53:06.582Z`
   - `ingestion_runs`: `2026-12-02T05:46:16.327Z`

All future expiration dates fall in October 2026 or later, guaranteeing zero unexpected document purges upon index activation.

---

## Pre-Existing & Deferred Dataset Verification

1. **Pre-Existing Analytics TTL**:
   - `analytics_events`: TTL index on `receivedAt` (`expireAfterSeconds = 2592000` / 30 days) remains **INTACT**.
2. **Permanent Datasets**:
   - `articles`, `stories`, `sources`, `user_preferences`, `user_bookmarks`, `user_follows`, `ingestion_source_settings`: Verified **ZERO TTL INDEXES** exist.
3. **Deferred Datasets**:
   - `analytics_daily_visitors`: Deferral maintained (no TTL index created).
   - `article-events-dlq`: Count `0` (no TTL index created).

---

## Verification & Build Integrity

- **Backend Unit Tests**: 417/417 tests passed (`mvn test` including `RetentionTtlActivationServiceTest`, `RetentionBackfillServiceTest`, `MongoDbExpiryLifecycleTest`, `HistoricalRetentionDryRunUnitTest`).
- **Live Index Audit**: Executed via `mongosh` script, verifying index key specs `{ expiresAt: 1 }` and `expireAfterSeconds: 0`.
- **Git Repository**: Clean working directory (`git status` clean).
