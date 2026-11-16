# Phase 1 performance baseline

Measurements taken while building Phase 1, kept as the "before" for later phases. Each section says what was measured, on what, and how to repeat it.

1. [Index study for the discovery search](#1-index-study-for-the-discovery-search-m4)
2. [Load test of the running stack](#2-load-test-of-the-running-stack-m8)
3. [Test coverage](#3-test-coverage-m8)

Parent document: [../plans/phase-1.md](../plans/phase-1.md).

## 1. Index study for the discovery search (M4)

**Measured on 2026-11-16.** The question: which of the indexes on `adventures` does `GET /adventures/search` actually need?

**Outcome:** the styles GIN index was dropped (migration `V5__drop_styles_gin_index.sql`). The location and date-range GiST indexes stay. The numbers below were taken after that migration, with the dropped index put back temporarily for comparison.

### Result

| Indexes available | 50 km radius | 500 km radius |
|---|---|---|
| Location GiST and date-range GiST (the schema as it is) | 1.1 ms | 1.6 ms |
| The same plus the styles GIN index (the schema before V5) | 3.7 ms | 4.1 ms |
| Location GiST only | **0.3 ms** | 7.3 ms |
| No GiST index, default settings (JIT on) | 192 ms | 183 ms |
| No GiST index, JIT off | 27 ms | 27 ms |

Times are PostgreSQL's "Execution Time" for one first-page query, the median of nine warm runs.

What the numbers say:

- **The location index is the one that matters.** Without it every search reads the whole table (4,770 buffers against 170 to 259). With it a city-sized search takes about a millisecond instead of 27 ms, and that gap grows with the table.
- **The styles GIN index made every measured query slower, so it was dropped.** There are only six styles, so a style filter matches a third to more than half of all rows. The planner still added the index to its `BitmapAnd`, read 58,010 entries, and spent 2.7 ms of the 3.7 ms doing so. The index was 4.4 MB and had to be maintained on every write.
- **The date-range index helps wide searches and costs a little on narrow ones.** At 500 km it brings 7.3 ms down to 1.6 ms. At 50 km it adds about 0.8 ms, because the planner reads 10,790 index entries to discard a few hundred rows it could have filtered directly. It stays for now; whether it earns its place depends on how wide real searches are, which the load test in M8 can show.
- **JIT hid the real cost of the sequential scan.** The planner prices the scan above `jit_above_cost` and compiles the query, which adds roughly 150 to 200 ms to a scan that itself runs in 27 ms.

### What was run

The statement is the one `DiscoveryRepository.search` sends for a first page: open trips of other people around Delhi (28.65195, 77.23149) that overlap a 15-day window starting 30 days from now and have the style `FOODIE` or `HIKING`, ordered by start date and id, limit 21.

| | 50 km | 500 km |
|---|---|---|
| Adventures inside the radius | 509 | 2,876 |
| Rows matching every filter, before the limit | 28 | 156 |

Data: 100,000 adventures from `infra/scripts/seed-adventures.sh`, spread evenly over the 5,000 most populous places, starting on a random day in the next 180 days, lasting 1 to 10 days, with 1 to 3 styles. Table size 24 MB; the location index is 6.9 MB and the date-range index 5.1 MB. The seed is random, so a repeat run has slightly different row counts.

Environment: PostgreSQL 17.12 with PostGIS 3.7.0 in Docker on an Apple M2 laptop with 8 GB of memory, default configuration (`shared_buffers` 128 MB, `jit_above_cost` 100000, up to 2 parallel workers per query).

To repeat it:

```bash
make seed-adventures
```

```bash
make index-study
```

`index-study` changes indexes inside a transaction and rolls it back, so the database is left as it was. It prints every plan three times per variant. Remove the seeded rows afterwards with `infra/scripts/seed-adventures.sh --delete`.

An earlier run on a different random seed, taken before the migration with all three indexes in place, gave the same picture: 3.6 ms with all three, 1.1 ms without the styles index, 0.3 ms with the location index only (50 km).

### Plans

The 50 km search, third run of each variant. Cost estimates and buffer lines are left out, and the WKB literal of the search point is shortened to `origin`.

The schema as it is (1.2 ms). The two GiST index scans are combined, then the style filter is checked on the rows that remain:

```
Limit  (actual time=1.166..1.187 rows=21 loops=1)
  ->  Sort  (actual time=1.160..1.161 rows=21 loops=1)
        Sort Key: a.start_date, a.id
        ->  Nested Loop  (actual time=1.100..1.152 rows=28 loops=1)
              ->  Bitmap Heap Scan on adventures a  (actual time=1.092..1.121 rows=28 loops=1)
                    Recheck Cond: (trip_dates && daterange(...))
                    Filter: (... AND (styles && '{FOODIE,HIKING}'::text[]) AND st_dwithin(...))
                    Rows Removed by Filter: 34
                    ->  BitmapAnd  (actual time=1.085..1.085 rows=0 loops=1)
                          ->  Bitmap Index Scan on adventures_location_gix  (actual time=0.095..0.095 rows=546 loops=1)
                                Index Cond: (location && _st_expand(origin, '50000'::double precision))
                          ->  Bitmap Index Scan on adventures_dates_gix  (actual time=0.988..0.988 rows=10790 loops=1)
                                Index Cond: (trip_dates && daterange(...))
              ->  Index Scan using places_pkey on places p  (actual time=0.001..0.001 rows=1 loops=28)
Execution Time: 1.213 ms
```

With the styles GIN index added back (3.7 ms). The styles scan dominates:

```
Limit  (actual time=3.702..3.724 rows=21 loops=1)
  ->  Sort  (actual time=3.696..3.698 rows=21 loops=1)
        ->  Nested Loop  (actual time=3.643..3.688 rows=28 loops=1)
              ->  Bitmap Heap Scan on adventures a  (actual time=3.637..3.656 rows=28 loops=1)
                    Recheck Cond: ((styles && '{FOODIE,HIKING}'::text[]) AND (trip_dates && daterange(...)))
                    ->  BitmapAnd  (actual time=3.630..3.630 rows=0 loops=1)
                          ->  Bitmap Index Scan on adventures_location_gix  (actual time=0.073..0.073 rows=546 loops=1)
                          ->  Bitmap Index Scan on adventures_styles_gin  (actual time=2.650..2.650 rows=58010 loops=1)
                                Index Cond: (styles && '{FOODIE,HIKING}'::text[])
                          ->  Bitmap Index Scan on adventures_dates_gix  (actual time=0.903..0.903 rows=10790 loops=1)
              ->  Index Scan using places_pkey on places p  (actual time=0.001..0.001 rows=1 loops=28)
Execution Time: 3.741 ms
```

With the location index only (0.3 ms). The index finds the 546 rows whose bounding box is near the point, and the other conditions are checked on those rows:

```
Limit  (actual time=0.254..0.274 rows=21 loops=1)
  ->  Sort  (actual time=0.252..0.253 rows=21 loops=1)
        ->  Nested Loop  (actual time=0.080..0.246 rows=28 loops=1)
              ->  Bitmap Heap Scan on adventures a  (actual time=0.078..0.223 rows=28 loops=1)
                    Filter: (... AND (styles && '{FOODIE,HIKING}'::text[]) AND (trip_dates && daterange(...)) AND st_dwithin(...))
                    Rows Removed by Filter: 518
                    ->  Bitmap Index Scan on adventures_location_gix  (actual time=0.067..0.067 rows=546 loops=1)
                          Index Cond: (location && _st_expand(origin, '50000'::double precision))
              ->  Index Scan using places_pkey on places p  (actual time=0.001..0.001 rows=1 loops=28)
Execution Time: 0.284 ms
```

No GiST index, JIT off (26.6 ms). Three processes read the whole table to find 28 rows:

```
Limit  (actual time=24.214..26.602 rows=21 loops=1)
  ->  Sort  (actual time=24.208..26.576 rows=21 loops=1)
        ->  Nested Loop  (actual time=2.535..26.561 rows=28 loops=1)
              ->  Gather  (actual time=2.530..26.501 rows=28 loops=1)
                    Workers Planned: 2
                    Workers Launched: 2
                    ->  Parallel Seq Scan on adventures a  (actual time=4.380..13.809 rows=9 loops=3)
                          Filter: (...)
                          Rows Removed by Filter: 33324
              ->  Index Scan using places_pkey on places p  (actual time=0.002..0.002 rows=1 loops=28)
Execution Time: 26.619 ms
```

### What this does not show

- The data is synthetic and evenly spread. Real trips cluster in fewer places and seasons, which changes how selective each filter is. A style that almost nobody picks would make a style index useful again.
- One connection, data in memory, on a laptop. This is the cost of one query, not throughput under load. The load test in M8 measures that.
- Only the first page was measured. Later pages add the keyset condition `(start_date, id) > (...)`, which is applied to the same few hundred rows.
- At 500 km with only the location index, most of the 7.3 ms is the start-up of a parallel worker, not reading data: the scan itself takes under 1 ms per process.
- The planner's row estimates are far off for the combined filter (it expects 1 row and gets 28 to 156). The plans it picks are still reasonable at this size.
- `adventures_open_feed_idx` on `(start_date, id)` was never chosen. Search always has a location to filter on first; that index is for a feed without one, which does not exist yet.

## 2. Load test of the running stack (M8)

**Measured on 2026-11-17**, against the Compose stack: the three services in containers, PostgreSQL and MongoDB, and k6 in a container on the same machine. This is the "before" for Phase 2, which repeats the same scripts on the same laptop.

### Result

Search, with 100,000 trips in the database. Each virtual user waits 100 to 300 ms between searches.

| Peak virtual users | Searches | Per second (average over the run) | p50 | p95 | p99 | Errors |
|---|---|---|---|---|---|---|
| 50 | 24,892 | 156 | 2.7 ms | 6.6 ms | 13.7 ms | 0% |
| 200 | 100,323 | 627 | 1.9 ms | 4.5 ms | 10.4 ms | 0% |

Accept, with 10 virtual users and no waiting. Each iteration posts a five-seat trip, sends five join requests and accepts all five.

| Chat | Accepts in 90 s | Accepts per second | Accept p50 | Accept p95 | Accept p99 | Errors |
|---|---|---|---|---|---|---|
| Healthy, called directly | 71,410 | 793 | 5.2 ms | 11.1 ms | 19.3 ms | 0% |
| Healthy, through Toxiproxy with no fault (control) | 74,880 | 832 | 5.2 ms | 11.5 ms | 18.9 ms | 0% |
| Answering 500 ms late, through Toxiproxy | 1,700 | 19 | 523 ms | 540 ms | 559 ms | 0% |
| Stopped | n/a | n/a | 502 `CHAT_UNAVAILABLE` in about 15 ms; the seat is taken | | | |

What the numbers say:

- **Search is not the bottleneck.** At 200 users the stack answered 627 searches per second with a p95 under 5 ms and was not saturated. The load was limited by the think time, not by the service.
- **An accept is exactly as slow as chat.** With chat 500 ms late, the accept p95 went from 11 ms to 540 ms and throughput fell from about 800 to 19 accepts per second, a factor of about 40. The organizer waits for a service that has nothing to do with whether the seat can be taken.
- **The proxy itself costs nothing measurable.** The control run through Toxiproxy with no fault matches the direct run, so the slow run measures the delay and not the proxy.
- **The other calls are unaffected by a slow chat.** In the slow run a join request still took 4.8 ms at the median. Creating a trip rose from 4.6 to 11.7 ms; the cause was not investigated.
- **With chat stopped, the accept fails after the seat is taken.** The caller gets a 502, the database has the seat and the member, and chat knows nothing until someone accepts again.
- **No trip was oversold.** Across all accept runs, warm-ups included, 34,187 trips were filled by 170,935 accepts. Every trip ended with exactly five seats taken and status `FULL`.

This pair, 11 ms against 540 ms at p95, is the comparison Phase 2 has to change: once the accept writes an event instead of calling chat, a slow or stopped chat should not show up in the accept at all.

### Recovery after chat comes back

After chat was started again, accepting the same request succeeded, with still exactly one seat taken. In two runs the first successful retry came 3 s and 0 s after chat reported healthy; in an earlier attempt a single retry after 8 s still failed.

The delay is DNS, not chat. While the chat container is stopped, its name does not resolve inside the Compose network, and the JVM caches a failed lookup for 10 seconds (`networkaddress.cache.negative.ttl`). Adventure keeps getting "unknown host" from its own cache until that entry expires. This is specific to how Compose handles a stopped container, but it is one more way a synchronous dependency leaks into the caller.

### What was run

| | |
|---|---|
| Data | 171,939 places (GeoNames), 100,000 seeded trips for search, 200 verified load-test users |
| Search | `infra/k6/search.js`: 15 s ramp to 40% of the peak, 60 s hold, 15 s ramp to the peak, 60 s hold, 10 s down. Random centre from 36 large cities, radius 25 to 250 km, a date window of 3 to 21 days within the next 170 days, a style filter on half the searches, a second page on a quarter |
| Accept | `infra/k6/accept.js`: 10 virtual users for 90 s, each with its own organizer and five travellers, 12 requests per iteration |
| Slow chat | Toxiproxy 2.13.0 between adventure and chat, a `latency` fault of 500 ms on the response |
| Stopped chat | `infra/scripts/chat-down-demo.sh` |

Each scenario was run once after a short warm-up run whose results were discarded.

Environment: Apple M2 laptop with 8 GB of memory; Docker with 3.9 GB for its virtual machine; no memory or CPU limits on the containers; Java 21, PostgreSQL 17.12, MongoDB 8.0, k6 1.8.3; default connection pool of 10 per service.

To repeat it, with `TRIPLOR_SEED_PASSWORD` and `TRIPLOR_INTERNAL_TOKEN` set in `.env` and the places loaded:

```bash
make up-load
```

```bash
make seed-adventures
```

```bash
make load-search
```

```bash
VUS=200 make load-search
```

```bash
make load-accept
```

```bash
infra/scripts/chat-down-demo.sh
```

Then the slow-dependency runs. `make up-chaos` restarts adventure with its chat address pointing at Toxiproxy.

```bash
make up-chaos
```

```bash
make load-accept
```

```bash
infra/scripts/chaos.sh slow 500
```

```bash
make load-accept
```

```bash
infra/scripts/chaos.sh reset
```

Afterwards, remove what the runs created (`--users` also deletes the load-test users):

```bash
infra/scripts/load-cleanup.sh --users
```

```bash
infra/scripts/seed-adventures.sh --delete
```

In the k6 summary, read `search_ms` and `accept_ms`. The built-in `http_req_duration` also contains the 200 logins done during setup.

### What this does not show

- k6, the services and both databases shared one laptop, so they competed for the same CPU and memory. The figures are for comparison with Phase 2 on this machine, not a capacity estimate.
- One run per scenario. The two healthy accept runs (793 and 832 per second) give a rough idea of the run-to-run variation; nothing smaller than that is meaningful.
- The runs are short, at most 160 seconds. Nothing here says how the system behaves after hours, with a full heap or a grown database.
- The accept test has no waiting between requests and only 10 users, so it measures the speed of the path, not behaviour under many concurrent organizers.
- All traffic comes from one k6 process over the container network, with connections kept open.

## 3. Test coverage (M8)

Line coverage from JaCoCo, measured by `./mvnw verify` on 2026-11-17 with 318 tests.

| Module | Lines covered | Build fails below |
|---|---|---|
| `libs/common` | 96.2% (101 of 105) | 94% |
| identity | 95.3% (470 of 493) | 93% |
| adventure | 98.2% (766 of 780) | 96% |
| chat | 96.2% (153 of 159) | 94% |

By package, the business logic is at or near full coverage: `adventure.trip` and `adventure.discovery` 100%, `adventure.joining` 97.8%, `chat.conversation` 97.1%, `identity.token` 97.6%. The lowest is `identity.config` at 76.3%; the uncovered lines are error paths for an unreadable signing key. Each service's `main` class accounts for two uncovered lines.

The reports are in each module's `target/site/jacoco/index.html` after a build. The thresholds are the `jacoco.line.minimum` property in each module's `pom.xml`.

Coverage says which lines ran, not whether the assertions are good. The seat-safety test is the stronger evidence: removing the `seats_taken < capacity` condition makes it fail.
