# Portfolio Tracker — Backend (REST API)

REST API for tracking a cryptocurrency investment portfolio: record buys and sells,
see what your holdings are worth right now, and get notified when a price or a
portfolio value crosses a threshold you set yourself.

Built as the final project for the Kodilla Java Developer bootcamp.

**Frontend repository:** [portfolio-tracker-frontend](../portfolio-tracker-frontend)
— a Vaadin application that consumes this API. The two projects share no code,
only this REST contract.

---

## What it does

You record your transactions. The application derives everything else:

- **Holdings** are calculated from the transaction ledger using the average-cost
  method — they are never stored, so the ledger stays the single source of truth.
- **Current value** comes from CoinGecko (prices in USD).
- **Value in your own currency** comes from NBP, the Polish National Bank, which
  publishes how many PLN one unit of each currency is worth.
- **Alerts** you define are checked on a schedule, and firing one writes an event
  you can review later.

Combining the two sources is the point: CoinGecko prices a portfolio in USD, NBP
converts that into the currency you actually think in. For a currency other than
PLN or USD the rate is derived as a cross rate through PLN.

> This is a tracker, not an advisor. It never suggests what to buy or sell —
> alerts are thresholds you choose for yourself.

---

## Running it

### Requirements

- **JDK 17 or newer** (the build pins a Java 17 toolchain)
- An internet connection, for CoinGecko and NBP
- No database to install — H2 runs embedded by default

### Start

```bash
./gradlew bootRun
```

The API is then at `http://localhost:8080`. On first start the application seeds
a demo user with two portfolios, four assets and six transactions, so there is
something to look at immediately.

**If port 8080 is already in use** (Apache, XAMPP and Jenkins all like it), pick
another one:

```bash
./gradlew bootRun --args='--server.port=8090'
```

If you change the port, tell the frontend where to find the API — see its README.

### Check it works

```bash
curl http://localhost:8080/v1/users
```

Then pull live market data and price the demo portfolio:

```bash
curl -X POST http://localhost:8080/v1/dashboard/refresh
```

```bash
curl http://localhost:8080/v1/portfolios/1/summary
```

### Tests

```bash
./gradlew test
```

Coverage report: `build/reports/jacoco/test/html/index.html`.
`./gradlew check` additionally **fails the build** if instruction coverage drops
below 65%.

---

## Configuration

Everything below is in `src/main/resources/application.properties`, and any of it
can be overridden on the command line with `--property=value`.

| Property | Default | Purpose |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `app.external.coingecko.base-url` | `https://api.coingecko.com/api/v3` | Price source |
| `app.external.nbp.base-url` | `https://api.nbp.pl/api` | Exchange rate source |
| `app.scheduler.enabled` | `true` | Turns all scheduled jobs on or off |
| `app.scheduler.price-refresh-ms` | `900000` | Price refresh interval (15 min) |
| `app.scheduler.price-retention-days` | `90` | How long price history is kept |
| `app.alerts.cooldown-minutes` | `60` | Minimum gap between re-firing one alert |
| `app.seed-demo-data` | `true` | Seed demo data on an empty database |
| `app.cors.allowed-origins` | `http://localhost:8081` | Where the frontend runs |

### Using MySQL instead of H2

H2 is the default so the project runs with no setup. To use MySQL, create the
schema and start with the `mysql` profile:

```bash
./gradlew bootRun --args='--spring.profiles.active=mysql --spring.datasource.username=root --spring.datasource.password=YOUR_PASSWORD'
```

See `src/main/resources/application-mysql.properties`.

---

## External APIs

Neither provider requires an API key or registration.

| Provider | Used for | Endpoint |
|---|---|---|
| **CoinGecko** | Crypto prices in USD, plus 24h change | `/simple/price` |
| **NBP** | Daily average currency table (PLN per unit) | `/exchangerates/tables/A` |

CoinGecko's free tier is rate limited. The default 15-minute refresh stays well
within it; lowering `app.scheduler.price-refresh-ms` much further may get you
throttled, which the application reports as HTTP 503 rather than failing silently.

If a provider is unreachable, the refresh endpoint reports which one failed and
still applies whatever the other one returned.

---

## API reference

All endpoints are under `/v1`. Errors always use the same body shape:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Portfolio with id 99 was not found",
  "fieldErrors": {},
  "timestamp": "2026-07-30T08:34:35.417"
}
```

| Status | Meaning |
|---|---|
| 400 | Malformed body, failed validation, missing or mistyped parameter |
| 404 | No such record |
| 409 | Would break a uniqueness rule |
| 422 | Well-formed but breaks a domain rule (e.g. selling more than you hold) |
| 503 | An external provider is unavailable |

### Users

| Method | Path |
|---|---|
| GET | `/v1/users` |
| GET | `/v1/users/{id}` |
| GET | `/v1/users/by-username/{username}` |
| POST | `/v1/users` |
| PUT | `/v1/users/{id}` |
| DELETE | `/v1/users/{id}` |

### Portfolios

| Method | Path | Notes |
|---|---|---|
| GET | `/v1/portfolios` | Optional `?userId=` |
| GET | `/v1/portfolios/{id}` | |
| GET | `/v1/portfolios/{id}/summary` | Full valuation with holdings and P&L |
| GET | `/v1/portfolios/{id}/transactions` | |
| POST | `/v1/portfolios` | |
| PUT | `/v1/portfolios/{id}` | |
| DELETE | `/v1/portfolios/{id}` | |

### Assets

| Method | Path | Notes |
|---|---|---|
| GET | `/v1/assets` | Optional `?activeOnly=true` |
| GET | `/v1/assets/{id}` | |
| GET | `/v1/assets/by-symbol/{symbol}` | |
| POST | `/v1/assets` | |
| PUT | `/v1/assets/{id}` | |
| PUT | `/v1/assets/{id}/active?value=` | Toggle scheduler price tracking |
| DELETE | `/v1/assets/{id}` | Refused (422) if transactions reference it |

### Transactions

| Method | Path | Notes |
|---|---|---|
| GET | `/v1/transactions?portfolioId=` | |
| GET | `/v1/transactions/{id}` | |
| POST | `/v1/transactions` | |
| POST | `/v1/transactions/with-summary` | Records and returns the repriced portfolio |
| PUT | `/v1/transactions/{id}` | |
| DELETE | `/v1/transactions/{id}` | |

### Alerts

| Method | Path | Notes |
|---|---|---|
| GET | `/v1/alerts` | Optional `?portfolioId=` |
| GET | `/v1/alerts/{id}` | |
| GET | `/v1/alerts/events?portfolioId=` | Alerts that have fired |
| GET | `/v1/alerts/events/unacknowledged` | |
| POST | `/v1/alerts` | |
| POST | `/v1/alerts/evaluate` | Run the rules now |
| PUT | `/v1/alerts/{id}` | |
| PUT | `/v1/alerts/events/{eventId}/acknowledge` | |
| DELETE | `/v1/alerts/{id}` | |

Alert types: `PRICE_ABOVE`, `PRICE_BELOW` (need an `assetId`), and
`PORTFOLIO_VALUE_ABOVE`, `PORTFOLIO_VALUE_BELOW` (no asset).

### Prices and exchange rates

| Method | Path | Notes |
|---|---|---|
| GET | `/v1/prices/latest` | |
| GET | `/v1/prices/assets/{assetId}/latest` | |
| GET | `/v1/prices/assets/{assetId}/history?days=` | |
| POST | `/v1/prices/refresh` | Pull from CoinGecko now |
| DELETE | `/v1/prices/history?olderThanDays=` | |
| GET | `/v1/rates` | |
| GET | `/v1/rates/{currencyCode}` | |
| GET | `/v1/rates/convert?to=` | USD to target multiplier |
| POST | `/v1/rates/refresh` | Pull from NBP now |

### Dashboard and audit

| Method | Path | Notes |
|---|---|---|
| GET | `/v1/dashboard/{userId}` | Everything one screen needs |
| POST | `/v1/dashboard/refresh` | Both providers plus alert evaluation |
| GET | `/v1/audit` | 100 most recent state changes |
| GET | `/v1/audit/by-type/{entityType}` | |

**48 endpoints**, covering GET, POST, PUT and DELETE.

---

## How it is put together

```
com.kodilla.portfolio
├── config/         Properties binding, REST clients, CORS, demo data seeding
├── controller/     REST layer plus the global exception handler
├── domain/         JPA entities
├── dto/            Request and response records (the API contract)
├── exception/      Domain exceptions, each mapped to an HTTP status
├── external/       Adapters for CoinGecko and NBP
├── facade/         PortfolioFacade
├── mapper/         Entity to DTO conversion
├── repository/     Spring Data repositories
├── scheduler/      Scheduled jobs
└── service/
    ├── alert/      Alert strategies, factory, observers, evaluator
    └── valuation/  Holdings calculation and portfolio valuation
```

### Design patterns

Four, each solving a problem that actually came up:

**Strategy** — `service/alert/AlertStrategy` with one implementation per alert
type. Adding a new kind of alert means adding a class; nothing existing changes.

**Factory** — `AlertStrategyFactory` maps an `AlertType` to its strategy. Spring
injects every strategy bean it finds, so registration is automatic. It rejects
two strategies claiming the same type rather than silently dropping one.

**Observer** — `AlertObserver` with `AlertPublisher` as the subject. When an
alert fires, three observers react independently: one persists the event, one
writes an audit row, one logs. Evaluation knows nothing about any of them, and
one observer failing does not stop the others.

**Adapter** — `CryptoPriceProvider` and `ExchangeRateProvider` hide CoinGecko's
and NBP's JSON shapes behind provider-neutral records. Swapping providers means
one new class.

Plus **Facade** — `PortfolioFacade` gives callers one task-shaped entry point
over six collaborating services.

### Scheduler

`MarketDataScheduler` runs two jobs:

- every 15 minutes: refresh prices and exchange rates, then re-evaluate every
  active alert;
- daily at 03:00: delete price history older than the retention window.

`fixedDelay` rather than `fixedRate`, so a slow provider cannot cause runs to
pile up. Both jobs swallow exceptions — letting one escape would make Spring
stop rescheduling the job for the rest of the application's life.

### Database writes

Well over the ten required. Create, update and delete for users, portfolios,
assets, transactions and alerts (15); plus price snapshots, snapshot purging,
exchange-rate upserts, alert events, alert timestamps, event acknowledgement,
and an audit row for every one of them.

### Testing

286 tests, **91.9% instruction coverage**.

- Unit tests with mocked collaborators for every service
- `MockRestServiceServer` for both external clients, including outages and
  malformed payloads
- `@WebMvcTest` for all nine controllers, covering status codes and validation
- `@DataJpaTest` against a real database for the derived queries and constraints
- `@SpringBootTest` context load, which catches wiring mistakes mocks cannot

Tests never touch the network: the test profile disables the scheduler and points
the external base URLs at an unused port.

---

## Notes for a reviewer

Two bugs were found by smoke-testing the running application and are worth
mentioning, since the fixes shaped the code:

**A concurrency race on exchange rates.** `exchange_rates` is uniquely keyed on
(currency, date). The scheduler's first run and a manual `POST /v1/rates/refresh`
overlapped, both saw no existing row, and both inserted — the second failed on
the constraint. The constraint is right, so the fix was to make the refresh
atomic: it is serialized on a lock, and uses an explicit `TransactionTemplate` so
the commit happens *before* the lock is released. (Annotating a private helper
with `@Transactional` would not have worked at all — a self-invocation skips the
Spring proxy, so the advice never runs.)

**Client errors reported as server errors.** An unknown enum value in a request
body and a missing required query parameter both produced HTTP 500. Both are the
caller's mistake, so `GlobalExceptionHandler` now maps them to 400.
