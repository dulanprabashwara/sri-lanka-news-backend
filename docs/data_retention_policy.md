# Phase DR2 & DR3 — Data Retention Policy Foundation & MongoDB Expiry Lifecycle

## Overview

This document specifies the centralized, strongly typed retention configuration foundation introduced in Phase DR2 and the persisted metadata lifecycle transitions added in Phase DR3 for the Sri Lankan Multilingual News Intelligence Platform backend.

> **CRITICAL**: Phase DR3 established `expiresAt` metadata fields and lifecycle calculation for target Mongo entities. Phase DR4B performed controlled historical retention backfill across all 213 historical records. Phase DR5 activated single-field TTL indexes on `{ expiresAt: 1 }` with `expireAfterSeconds = 0` across all 5 target collections on MongoDB Atlas (`sri_lanka_news`). Phase DR6 introduced non-destructive, acknowledgement-aware Redis stream trimming (`article-discovered`, `notification-events`) with exact `XTRIM MINID`, pending entry protection, and distributed SET-if-absent lease scheduling. Permanent datasets, deferred datasets, and DLQ streams remain untouched.

---

## Configuration Properties Structure

Configuration prefix: `news.retention`

Defined in `lk.srilankannews.retention.RetentionProperties`:

```yaml
news:
  retention:
    notifications:
      read-days: 180         # Default: 180 days (Minimum: 30)
      unread-max-days: 365   # Default: 365 days (Minimum: 30, must be >= read-days)

    notification-outbox:
      published-days: 30     # Default: 30 days (Minimum: 7)
      failed-days: 90        # Default: 90 days (Minimum: 7, must be >= published-days)

    ingestion-runs:
      terminal-days: 90      # Default: 90 days (Minimum: 30)

    ingestion-triggers:
      processed-days: 30     # Default: 30 days (Minimum: 7)

    admin-audit:
      days: 365              # Default: 365 days (Minimum: 30)

    resolved-dlq:
      days: 90               # Default: 90 days (Minimum: 7) [FUTURE/INACTIVE POLICY]
```

---

## Validation & Safety Floors

1. **Notifications Read Retention**: `read-days` >= 30 days.
2. **Notifications Unread Max Retention**: `unread-max-days` >= `read-days`.
3. **Notification Outbox Published**: `published-days` >= 7 days.
4. **Notification Outbox Failed**: `failed-days` >= `published-days`.
5. **Ingestion Runs Terminal**: `terminal-days` >= 30 days.
6. **Ingestion Triggers Processed**: `processed-days` >= 7 days.
7. **Admin Audit**: `days` >= 30 days.
8. **Resolved DLQ**: `days` >= 7 days.

Violations during context initialization result in Spring property binding failure (`ConfigurationPropertiesBindException` or `IllegalArgumentException`).

---

## Never-Expire Dataset Registry

The following entities and collections are registered in `NeverExpireEntities` and MUST NEVER receive automatic retention expiration:

- `Article` (`articles`) — Core historical news corpus and vector embeddings.
- `Story` (`stories`) — Event clusters and neutral summaries.
- `Source` (`sources`) — Master publisher registry.
- `UserPreferences` (`user_preferences`) — Reader settings.
- `NotificationPreference` (`notification_preferences`) — Reader channel preferences.
- `UserBookmark` (`user_bookmarks`) — Reader saved articles.
- `UserFollow` (`user_follows`) — Reader subscriptions.
- `IngestionSourceSettings` (`ingestion_source_settings`) — Scraper configuration.

No retention configuration properties exist for these datasets to prevent accidental configuration.

---

## Phase DR3 — MongoDB Expiry Metadata Implementation

### Target Domain Entities & Fields

| Entity | Collection | Expiry Metadata Field | Serialization Protection |
|---|---|---|---|
| `Notification` | `notifications` | `@JsonIgnore Instant expiresAt` | Excluded from JSON DTOs |
| `NotificationEvent` | `notification_events` | `@JsonIgnore Instant expiresAt` | Excluded from JSON DTOs |
| `IngestionRun` | `ingestion_runs` | `@JsonIgnore Instant expiresAt` | Excluded from JSON DTOs |
| `IngestionTriggerRequest` | `ingestion_trigger_requests` | `@JsonIgnore Instant expiresAt` | Excluded from JSON DTOs |
| `AdminAuditEvent` | `admin_audit_events` | `@JsonIgnore Instant expiresAt` | Excluded from JSON DTOs |

---

## Terminal State Retention Rules & Calculation Logic

| Domain / Entity | Status | Expiration Clock Starts | Retention Calculation | Expiry Value |
|---|---|---|---|---|
| Notification | Unread (New) | `createdAt` | `createdAt + unread-max-days` | `createdAt + 365d` |
| Notification | Read (`readAt` set) | `readAt` | `readAt + read-days` | `readAt + 180d` |
| Outbox Event | `PENDING`, `PROCESSING`, `RETRYING` | N/A | Active / Pending state | `null` |
| Outbox Event | `PUBLISHED`, `PROCESSED` | `publishedAt` / `processedAt` | `timestamp + published-days` | `timestamp + 30d` |
| Outbox Event | `FAILED` | N/A (no `failedAt` field) | Missing terminal timestamp | `null` |
| Ingestion Run | `RUNNING` | N/A | Active lease state | `null` |
| Ingestion Run | `COMPLETED`, `FAILED`, `INTERRUPTED` | `finishedAt` | `finishedAt + terminal-days` | `finishedAt + 90d` |
| Ingestion Trigger | `PENDING`, `CLAIMED`, `RETRYING` | N/A | Active trigger state | `null` |
| Ingestion Trigger | `COMPLETED`, `FAILED`, `CANCELLED` | `completedAt` | `completedAt + processed-days` | `completedAt + 30d` |
| Admin Audit Event | All | `createdAt` | `createdAt + days` | `createdAt + 365d` |

---

## Safety & Non-Destructive Guarantees

1. **No New TTL Indexes**: Verified by Reflection assertions (`MongoDbExpiryLifecycleTest.assertTtlIndexSafety`) that zero `@Indexed(expireAfter = ...)` annotations exist on DR3 target entity classes. Pre-existing 30d TTL on `analytics_events.receivedAt` remains intact.
2. **DTO Exposure Protection**: `@JsonIgnore` guarantees `expiresAt` metadata fields are NEVER exposed in REST DTO payloads.
3. **No Active Deletion**: Data retention calculation is strictly additive timestamp metadata generation.
4. **Reactivation Safety**: If an ingestion trigger request transitions back to an active status, `expiresAt` resets to `null`.
5. **Mark-All-Read Safety**: `markAllReadForUser` targets strictly unread notifications (`readAt == null`), leaving already-read notifications untouched.

---

## Verification Suite

- `MongoDbExpiryLifecycleTest`: Safety assertions, `@JsonIgnore` DTO exposure, and retention calculations across all 5 target entities.
- `NotificationProcessingServiceTest`, `NotificationControllerTest`, `IngestionRunServiceTest`, `IngestionTriggerServiceTest`, `NotificationEventOutboxServiceTest`: Integrated regression test suites.
