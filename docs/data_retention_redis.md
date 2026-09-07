# Redis Stream Retention & Stream Trimming

This document details the Redis stream retention architecture, safety mechanisms, and configuration options implemented in Phase DR6 for the Sri Lankan Multilingual News Intelligence Platform backend.

## Overview

Redis streams facilitate decoupled, event-driven processing across the application. Without bounded retention, streams grow indefinitely (`XLEN` increases continuously), causing unbounded memory consumption.

The `RedisStreamRetentionService` provides safe, non-destructive trimming of historical entries based on entry age, while guaranteeing that **no pending or undelivered entries are ever discarded**.

---

## Stream Classifications & Targets

| Stream Key | Type | Role | Bounded Retention Strategy | Safety Floors |
| :--- | :--- | :--- | :--- | :--- |
| `article-discovered` | Work Stream | Article processing pipeline | `XTRIM MINID` (Default: 7 days) | Strictly protected by consumer groups |
| `notification-events` | Work Stream | Notification delivery events | `XTRIM MINID` (Default: 7 days) | Strictly protected by consumer groups |
| `article-discovered-dlq` | Dead-Letter Queue | Unresolvable / error records | **NO AUTO-TRIM** (Manual resolution required) | Refused by retention service |

---

## Threshold & Safety Algorithm

Trimming uses exact `XTRIM <streamKey> MINID <finalThreshold>`. The threshold is calculated dynamically per run using the formula:

$$\text{finalThreshold} = \min(\text{ageCutoffId}, \text{streamProtectionBoundary})$$

### 1. Age Cutoff (`ageCutoffId`)
Calculated from the system clock and configured retention window:
$$\text{ageCutoffMs} = \text{now} - \text{ackedHistoryDays}$$
$$\text{ageCutoffId} = \text{RedisStreamID.fromEpochMilli}(\text{ageCutoffMs})$$

### 2. Stream Protection Boundary (`streamProtectionBoundary`)
Iterates over all consumer groups registered on the stream (via `XINFO GROUPS`):
- For each group:
  - If $\text{pendingCount} > 0$: $\text{groupBoundary} = \text{oldestPendingId}$ (via `XPENDING`)
  - If $\text{pendingCount} == 0$: $\text{groupBoundary} = \text{lastDeliveredId}$
- $\text{streamProtectionBoundary} = \min_{g \in \text{groups}}(\text{groupBoundary})$

### 3. Absolute Safety Triggers
Trimming is **immediately aborted** (`safeToTrim = false`) if:
1. Stream key is a Dead-Letter Queue (`*dlq*`).
2. Stream has **no registered consumer groups** (`UNSAFE — NO CONSUMER GROUP`).
3. Any consumer group has a boundary of `0-0` or unread pending state.
4. Pending entry body is missing or stream metadata is inconsistent (`UNSAFE — PENDING ENTRY BODY MISSING`).
5. Calculated threshold resolves to `0-0`.

---

## Configuration Properties

Configuration is managed via Spring Boot properties under the `news.retention.redis` prefix:

```yaml
news:
  retention:
    redis:
      enabled: false                # Master toggle for Redis retention service
      apply: false                  # false = Preview Mode (Read-only analysis), true = Controlled Apply
      schedule-enabled: false       # Autonomous recurring scheduler
      schedule-cron: "0 0 */6 * * *" # Every 6 hours
      article-events:
        acked-history-days: 7       # Processed history window for article-discovered
      notification-events:
        acked-history-days: 7       # Processed history window for notification-events
      resolved-dlq:
        days: 90                    # Inactive placeholder (DLQ auto-trim strictly prohibited)
```

---

## Multi-Instance Distributed Scheduler

The `RedisStreamRetentionScheduler` automatically executes stream retention every 6 hours when `schedule-enabled=true`. 

To prevent concurrent execution across multiple backend application instances:
- Uses a distributed SET-if-absent lease key in Redis (`news:retention:redis:lease`).
- Bounded 10-minute TTL prevents deadlock in case of instance crash.
- Unacquired lease skips the execution run cleanly without throwing exceptions.

---

## Operations & Verification

Before setting `apply=true` or enabling scheduled execution in any environment:
1. Verify Redis connectivity (`REDIS CONNECTION: YES`).
2. Run Preview Mode (`apply=false`) and inspect logger output:
   `redis_retention_preview stream=article-discovered xlen=... threshold=... safe=true`
3. Verify that post-trim metrics report zero lost pending or undelivered messages:
   - Pending entries lost = 0
   - Undelivered entries lost = 0
   - Consumer groups removed = 0
   - DLQ entries trimmed = 0
