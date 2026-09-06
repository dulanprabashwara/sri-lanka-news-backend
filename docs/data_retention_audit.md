# Phase DR1 — Storage & Retention Audit

## Executive Summary

Phase DR1 establishes an authoritative, repository-grounded audit of all data storage structures across the Sri Lankan Multilingual News Intelligence Platform. This includes 17 MongoDB collections, 3 Redis Streams, Redis Feed Caches, and background scheduled rollups.

> **IMPORTANT**: DR1 is strictly an audit and design phase. No database indexes were created, no data was deleted, no Redis streams were trimmed, and no backend application code was modified.

---

## Repositories & Components Inspected

- **Backend Repository** (`D:\sri-lanka-news\sri-lanka-news-backend`): Spring Boot 3.5.16, `@Document` models, Mongo repositories, Redis stream configurations, scheduled jobs, security audit logs.
- **Ingestion Repository** (`D:\sri-lanka-news\sri-lanka-news-ingestion`): Python 3.12, APScheduler, source leases, execution history payload formatting.
- **Frontend Repository** (`D:\sri-lanka-news\sri-lanka-news-frontend`): Notification semantics, account preferences, DNT/GPC privacy headers.

---

## MongoDB Collection Inventory

| Collection Name | Document Class | Primary Purpose | Important Timestamps | Current TTL | Current Cleanup Logic | Storage Growth Risk | Retention Classification |
|---|---|---|---|---|---|---|---|
| `articles` | `Article` | Extracted publisher articles, AI enrichment, vector embeddings, translations | `publishedAt`, `discoveredAt`, `createdAt`, `updatedAt` | **NONE** | **NONE** | **HIGH** | **PERMANENT** |
| `stories` | `Story` | Clustered real-world events, summaries, timelines, media | `firstPublishedAt`, `lastPublishedAt`, `createdAt`, `updatedAt` | **NONE** | **NONE** | **MEDIUM-HIGH** | **PERMANENT** |
| `sources` | `Source` | Publisher registry specifications, reliability & operational status | `createdAt`, `updatedAt`, `lastScrapedAt` | **NONE** | **NONE** | **LOW** | **PERMANENT** |
| `user_preferences` | `UserPreferences` | Reader account preferences, language, category choices, analytics opt-in | `createdAt`, `updatedAt` | **NONE** | **NONE** | **MEDIUM** | **USER-CONTROLLED** |
| `user_bookmarks` | `UserBookmark` | Reader saved articles library | `createdAt` | **NONE** | **NONE** | **MEDIUM** | **USER-CONTROLLED** |
| `user_follows` | `UserFollow` | Reader followed publisher sources and topic tags | `createdAt` | **NONE** | **NONE** | **MEDIUM** | **USER-CONTROLLED** |
| `notifications` | `Notification` | Reader in-app notifications & email delivery status | `createdAt`, `readAt`, `emailDelivery.sentAt` | **NONE** | **NONE** | **HIGH** | **TERMINAL-STATE RETENTION** |
| `notification_preferences` | `NotificationPreference` | User notification channel preferences, quiet hours, topic filters | `createdAt`, `updatedAt` | **NONE** | **NONE** | **MEDIUM** | **USER-CONTROLLED** |
| `notification_events` | `NotificationEvent` | Outbox pattern event logs for notification dispatching pipeline | `occurredAt`, `publishedAt`, `processedAt` | **NONE** | **NONE** | **HIGH** | **TERMINAL-STATE RETENTION** |
| `ingestion_source_settings` | `IngestionSourceSettings` | Source scheduling parameters and scraper configuration | `createdAt`, `updatedAt` | **NONE** | **NONE** | **LOW** | **PERMANENT** |
| `ingestion_source_leases` | `IngestionSourceLease` | Active single-instance worker locks | `expiresAt` | **NONE** | Operational lease check (`expiresAt < now`) | **LOW** | **TEMPORARY** |
| `ingestion_runs` | `IngestionRun` | Operational history log of ingestion attempts & article metrics | `scheduledFor`, `startedAt`, `finishedAt`, `createdAt` | **NONE** | **NONE** | **HIGH** | **TERMINAL-STATE RETENTION** |
| `ingestion_trigger_requests` | `IngestionTriggerRequest` | Admin "Run Now" trigger request queue | `requestedAt`, `processedAt` | **NONE** | **NONE** | **MEDIUM** | **TERMINAL-STATE RETENTION** |
| `analytics_events` | `AnalyticsEvent` | Raw pseudonymous reader engagement telemetry events | `occurredAt`, `receivedAt` | **`30d`** (`@Indexed(expireAfter = "30d")`) | MongoDB native TTL index (`30d` on `receivedAt`) | **BOUNDED** | **TEMPORARY (TTL EXISTENT)** |
| `analytics_daily_metrics` | `AnalyticsDailyMetric` | Aggregated daily reader engagement metrics by route/category | `date`, `createdAt`, `updatedAt` | **NONE** | Scheduled `AnalyticsRollupService` | **LOW** | **LONG-TERM AGGREGATE** |
| `analytics_daily_visitors` | `AnalyticsDailyVisitor` | Daily hashed visitor tracking for unique reader counts | `date`, `createdAt` | **NONE** | Scheduled `AnalyticsRollupService` | **MEDIUM** | **LONG-TERM AGGREGATE** |
| `admin_audit_events` | `AdminAuditEvent` | Append-only security audit log tracking admin operations | `createdAt` | **NONE** | **NONE** | **MEDIUM** | **SECURITY/AUDIT RETENTION** |

---

## Existing TTL Indexes & Scheduled Cleanup

### 1. MongoDB Native TTL Index
- `analytics_events`: `@Indexed(expireAfter = "30d") Instant receivedAt`. Automatically purged by MongoDB after 30 days.

### 2. Scheduled Cleanup / Rollup Jobs
- `AnalyticsRollupService`: `@Scheduled(cron = "0 5 0 * * *")`. Performs daily aggregation of `analytics_events` into `analytics_daily_metrics` and `analytics_daily_visitors`.
- `PublicAiRateLimitFilter`: Periodically clears expired rate-limit windows (`removeExpired`) every 256 requests.

---

## Never-Expire Data Safety List

The following core domain entities **MUST NEVER** receive automatic TTL expiration:

1. `Article` (`articles`) — Durable repository of news reports and vector embeddings.
2. `Story` (`stories`) — Durable multi-publisher event clusters and neutral summaries.
3. `Source` (`sources`) — Master publisher registry metadata.
4. `UserPreferences` (`user_preferences`) — Reader explicit settings.
5. `NotificationPreference` (`notification_preferences`) — Reader notification settings.
6. `UserBookmark` (`user_bookmarks`) — Reader explicitly saved articles.
7. `UserFollow` (`user_follows`) — Reader explicitly followed topics/sources.
8. `IngestionSourceSettings` (`ingestion_source_settings`) — Admin scraper setup.

---

## Redis Inventory & Stream Safety

| Name / Key Prefix | Structure | Purpose | Producer | Consumer | Current TTL / Trimming | Growth Risk | Stream Safety Classification |
|---|---|---|---|---|---|---|---|
| `article-events` | Stream | Async queue for discovered article processing | Ingestion API | `RedisArticleStreamListener` | **UNBOUNDED** | **HIGH** | **REQUIRES ACK-AWARE TRIMMING** |
| `article-events-dlq` | Stream | Dead-letter queue for failed processing attempts | `RedisArticleEventStream` | Admin inspection | **UNBOUNDED** | **MEDIUM** | **SAFE TO AGE-TRIM (90d)** |
| `notification-events` | Stream | Notification outbox dispatching stream | `NotificationEventPublisher` | `NotificationEventConsumer` | **UNBOUNDED** | **HIGH** | **REQUIRES ACK-AWARE TRIMMING** |
| `news:feed:generation` | String | Counter for invalidating public feed cache generations | Feed cache | Feed cache | **PERMANENT (0 TTL)** | **NONE** | N/A |
| `news:feed:{gen}:{hash}` | String | Paged ArticleResponse feed JSON cache | Feed cache | Feed cache | **Short TTL** | **LOW** | N/A |

---

## Proposed Retention Matrix for Future Implementation (Phase DR2+)

| Dataset / Collection | Recommended Retention | Expiration Trigger / Strategy | Safety Justification |
|---|---|---|---|
| **Raw Analytics** (`analytics_events`) | **30 Days** | Active TTL index on `receivedAt` | Daily metrics already rolled up into `analytics_daily_metrics`. |
| **Notifications** (`notifications`) | **180 Days** | Explicit `expiresAt` set upon `readAt` or terminal state | Preserves 6 months of reader notification history. |
| **Notification Outbox** (`notification_events`) | **30 Days** | Explicit `expiresAt` set when status becomes `PUBLISHED` or `FAILED` | Prevents outbox pipeline buildup after processing completes. |
| **Ingestion Runs** (`ingestion_runs`) | **90 Days** | Explicit `expiresAt` set when run enters `COMPLETED` or `FAILED` | Preserves 3 months of operational health history for admin diagnostics. |
| **Trigger Requests** (`ingestion_trigger_requests`) | **30 Days** | Explicit `expiresAt` set when status becomes `PROCESSED` | Cleans up manual run request logs. |
| **Resolved DLQ** (`article-events-dlq`) | **90 Days** | Scheduled trimming / age-based trimming | Allows 90 days for admin inspection before cleanup. |
| **Unresolved DLQ** | **NO AUTOMATIC TTL** | Manual admin resolution | Prevents loss of unanalyzed pipeline failure events. |
| **Admin Audit Events** (`admin_audit_events`) | **365 Days** | Configurable `expiresAt` | Meets regulatory and security compliance audit retention standards. |

---

## Recommended DR2 Configuration Properties

Proposed configuration keys for Spring Boot (`application.yml`):

```yaml
news:
  retention:
    analytics-raw-days: 30
    notifications-days: 180
    notification-outbox-days: 30
    ingestion-runs-days: 90
    ingestion-triggers-days: 30
    resolved-dlq-days: 90
    admin-audit-days: 365
```

---

## Verification Audit Confirmation

- **Application Code Changed**: `NONE`
- **Database Changed**: `NONE`
- **Mongo Indexes Changed**: `NONE`
- **Redis Changed**: `NONE`
- **Dependencies Changed**: `NONE`
