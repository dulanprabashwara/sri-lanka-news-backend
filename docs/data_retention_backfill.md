# DR4B Live Historical Retention Backfill Execution Report

## Overview
This document serves as the authoritative operational and audit record for **Phase DR4B — Controlled Historical Retention Backfill**, executed on the Sri Lankan Multilingual News Intelligence Platform's live MongoDB Atlas **DEVELOPMENT / WORKING DATABASE** (`sri_lanka_news`).

All 213 historical records across the 5 target DR3 retention collections have been safely updated with precise, policy-derived `expiresAt` timestamps.

---

## 1. Environment & Target Database
- **Database Connection**: YES
- **Database Name**: `sri_lanka_news`
- **Environment Classification**: DEVELOPMENT / WORKING DATABASE (MongoDB Atlas)
- **Execution Mode**: `batchSize = 100`, read-only Dry Run followed by idempotent Controlled Apply.

---

## 2. Pre-Backfill Live Snapshot
| Collection | Total Documents | With `expiresAt` | Without `expiresAt` | Eligible | Active Protected | Deferred | Immediate Expiry |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `notifications` | 1 | 0 | 1 | 1 | 0 | 0 | 0 |
| `notification_events` | 2 | 0 | 2 | 2 | 0 | 0 | 0 |
| `ingestion_runs` | 196 | 0 | 196 | 196 | 0 | 0 | 0 |
| `ingestion_trigger_requests` | 13 | 0 | 13 | 13 | 0 | 0 | 0 |
| `admin_audit_events` | 1 | 0 | 1 | 1 | 0 | 0 | 0 |
| **TOTAL** | **213** | **0** | **213** | **213** | **0** | **0** | **0** |

---

## 3. Dry Run Execution Results (`apply=false`)
- **Execution Settings**: `news.retention.backfill.enabled=true`, `news.retention.backfill.apply=false`, `batchSize=500`
- **Mongo Writes Performed**: **0**

| Collection | Examined | Eligible | Would Update | Protected | Deferred | Errors |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| `notifications` | 1 | 1 | 0 | 0 | 0 | 0 |
| `notification_events` | 2 | 2 | 0 | 0 | 0 | 0 |
| `ingestion_runs` | 196 | 196 | 0 | 0 | 0 | 0 |
| `ingestion_trigger_requests` | 13 | 13 | 0 | 0 | 0 | 0 |
| `admin_audit_events` | 1 | 1 | 0 | 0 | 0 | 0 |
| **TOTAL** | **213** | **213** | **0** | **0** | **0** | **0** |

---

## 4. Immediate Expiry Pre-Apply Gate
- **Immediate Expiry Candidate Count (`expiresAt <= NOW`)**: **0**
- **Safety Gate Status**: **PASSED** (0 candidates evaluated as immediately expired).

---

## 5. Controlled Apply Execution Results (`apply=true`)
- **Execution Settings**: `news.retention.backfill.enabled=true`, `news.retention.backfill.apply=true`, `batchSize=100`

| Collection | Examined | Eligible | Updated | Skipped | Protected | Deferred | Errors |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `notifications` | 1 | 1 | 1 | 0 | 0 | 0 | 0 |
| `notification_events` | 2 | 2 | 2 | 0 | 0 | 0 | 0 |
| `ingestion_runs` | 196 | 196 | 196 | 0 | 0 | 0 | 0 |
| `ingestion_trigger_requests` | 13 | 13 | 13 | 0 | 0 | 0 | 0 |
| `admin_audit_events` | 1 | 1 | 1 | 0 | 0 | 0 | 0 |
| **TOTAL** | **213** | **213** | **213** | **0** | **0** | **0** | **0** |

---

## 6. Post-Backfill Live Verification Snapshot
| Collection | Total | With `expiresAt` | Without `expiresAt` | Active Protected | Deferred |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `notifications` | 1 | 1 | 0 | 0 | 0 |
| `notification_events` | 2 | 2 | 0 | 0 | 0 |
| `ingestion_runs` | 196 | 196 | 0 | 0 | 0 |
| `ingestion_trigger_requests` | 13 | 13 | 0 | 0 | 0 |
| `admin_audit_events` | 1 | 1 | 0 | 0 | 0 |
| **TOTAL** | **213** | **213** | **0** | **0** | **0** |

---

## 7. Policy Calculation Verification (Sample Audits)
- **`notifications`** (`id=6a9a701f7c9e12c90e3d034e`):
  - Created: `2026-03-03T08:33:44.189Z` (Unread)
  - Calculated Policy: `createdAt + 365 days`
  - Stored `expiresAt`: `2027-03-03T08:33:44.189Z` (**EXACT MATCH**)
- **`notification_events`** (`id=6a9a6edf7c9e12c90e3d0345`):
  - Published: `2026-09-04T07:10:29.149Z` (`PUBLISHED`)
  - Calculated Policy: `publishedAt + 30 days`
  - Stored `expiresAt`: `2026-10-04T07:10:29.149Z` (**EXACT MATCH**)
- **`ingestion_runs`** (`id=6a988e699b4ed16ac44a02b6`):
  - Finished: `2026-09-03T06:48:10.244Z` (`COMPLETED`)
  - Calculated Policy: `finishedAt + 90 days`
  - Stored `expiresAt`: `2026-12-02T06:48:10.244Z` (**EXACT MATCH**)
- **`ingestion_trigger_requests`** (`id=6a9942c715bff78e6e6047b0`):
  - Completed: `2026-09-03T09:53:06.582Z` (`COMPLETED`)
  - Calculated Policy: `completedAt + 30 days`
  - Stored `expiresAt`: `2026-10-03T09:53:06.582Z` (**EXACT MATCH**)
- **`admin_audit_events`** (`id=6a99492f15bff78e6e6047c8`):
  - Created: `2026-09-03T10:17:19.174Z`
  - Calculated Policy: `createdAt + 365 days`
  - Stored `expiresAt`: `2027-09-03T10:17:19.174Z` (**EXACT MATCH**)

---

## 8. Data Safety & Infrastructure Verification
- **Post-Backfill Immediate Expiry Count (`expiresAt <= NOW`)**: **0**
- **Existing Analytics TTL**: `analytics_events.receivedAt` index with `expireAfterSeconds: 2592000` (30 days) remains **INTACT**.
- **DR3 `expiresAt` TTL Indexes**: **NONE**
- **Automatic Deletion Activated**: **NO**
- **Permanent Dataset Write Audit**: 0 writes to `articles`, `stories`, `sources`, `user_preferences`, `notification_preferences`, `user_bookmarks`, `user_follows`, `ingestion_source_settings`.
- **Analytics Daily Visitors**: UNCHANGED
- **NotificationEvent FAILED**: DEFERRED
- **DLQ (`article-events-dlq`)**: UNCHANGED
- **Redis**: UNCHANGED

---

## 9. Backend Build Parity & Verification
- **Backend Unit & Integration Tests**: 411 / 411 Passed
- **Maven Test Result**: `BUILD SUCCESS`
- **Maven Package Result**: `BUILD SUCCESS`
- **`git diff --check`**: `CLEAN`
- **MongoDB Documents Updated**: **213**
- **MongoDB Documents Deleted**: **0**
- **MongoDB Indexes Changed**: **0**
- **Ready For DR5**: **YES**
