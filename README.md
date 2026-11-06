# Industrial Marketplace

Fresh implementation of a product-first industrial supply marketplace. Customers see products and a single published price. Vendor records and product-to-vendor sourcing options are visible only to admins.

## Projects

| Folder | Purpose | Local port |
| --- | --- | --- |
| `catalog-service` | Public product catalog and admin product management | 8081 |
| `sourcing-service` | Private vendor directory and many-to-many product offers | 8082 |
| `order-service` | Cash-on-delivery checkout and admin order management | 8083 |
| `auth-service` | Admin login, short-lived JWT issuance, and public JWKS | 8084 |
| `admin-web` | React Create React App admin console | 3000 |
| `customer-android` | Kotlin/Compose storefront, cart, and checkout | Android emulator |

The services own separate databases. `productId` in the sourcing service is a reference to the catalog service; there is no cross-database foreign key. See [docs/architecture.md](docs/architecture.md) for the data model, service boundaries, GCP path, and launch decisions.

The checked GCP account, project, Neon prerequisites, deployment order, and Cloud Run URL command are in [docs/deployment-gcp-neon.md](docs/deployment-gcp-neon.md).

The Android and React apps now use the public Cloud Run URLs by default. The Android app calls catalog and orders; the React admin app calls auth, catalog, sourcing, and orders. The hosted catalog starts empty, so add products in the admin app before browsing or checking out as a customer. Admin credentials are stored in GCP Secret Manager, not in this repository.

To use the hosted backend from a fresh terminal, run `npm start` in `admin-web`; its default URLs already point at Cloud Run. The admin web app is served locally at `http://localhost:3000`. Open `customer-android` in Android Studio and run the debug app, or install `customer-android/app/build/outputs/apk/debug/app-debug.apk`. Both Android build types use the same hosted API defaults.

To check the signed-in Neon organization's plan and project count, run `python scripts/neon_status.py` from this folder. This read-only helper uses the Neon CLI login, downloads the CLI through `npx` if needed, and accepts `--org-id` when multiple organizations are available. Use `--json` for machine-readable output. Per-project compute, storage, and transfer usage is available in the Neon Console.

## Run locally

Prerequisites: Java 21+, Maven 3.9+, Node 18+, npm, and Android Studio with Android SDK 34 for the customer app. Use JDK 21 for the Android Gradle build.

Start each command in a separate terminal from this folder. Start auth first:

```powershell
cd auth-service
$env:ADMIN_PASSWORD = 'choose-a-local-password'
$env:AUTH_DEV_EPHEMERAL_KEYS = 'true'
mvn spring-boot:run
```

```powershell
cd catalog-service
$env:SPRING_PROFILES_ACTIVE = 'demo'
mvn spring-boot:run
```

```powershell
cd sourcing-service
mvn spring-boot:run
```

```powershell
cd order-service
mvn spring-boot:run
```

```powershell
cd admin-web
npm install
$env:REACT_APP_CATALOG_API = 'http://localhost:8081'
$env:REACT_APP_SOURCING_API = 'http://localhost:8082'
$env:REACT_APP_ORDER_API = 'http://localhost:8083'
$env:REACT_APP_AUTH_API = 'http://localhost:8084'
npm start
```

Sign in to the admin web app as `admin` with the password set in the auth-service terminal. The auth service issues a 15-minute RS256 JWT. The web app holds it in memory and sends it as a Bearer token to the three admin APIs. Restarting the local auth service generates a new development key and invalidates existing tokens.

Open `customer-android` as a separate project in Android Studio. Debug and release builds use the deployed HTTPS catalog and order URLs by default. To point an Android build at local services, pass `-PcatalogApiUrl=http://10.0.2.2:8081/ -PorderApiUrl=http://10.0.2.2:8083/`. The `demo` catalog profile loads eight sample products locally; omit it when entering your own catalog.

For a command-line Android build, set `JAVA_HOME` to JDK 21 and `ANDROID_HOME` to your Android SDK, then run `customer-android\gradlew.bat :app:assembleDebug`. Override either URL with `-PcatalogApiUrl=...` or `-PorderApiUrl=...` when needed.

The local services use in-memory H2 and reset on restart. The deployed services use separate catalog, sourcing, and orders databases in Neon PostgreSQL. Their configuration uses `SPRING_PROFILES_ACTIVE=postgres`, separate Neon credentials, and `CATALOG_API_URL` for checkout. Flyway creates the tables.

## Current scope

The Android app supports browsing, cart quantities, address entry, and order placement with **cash on delivery**. The order service recalculates prices from the catalog, rejects stale totals, stores an immutable item snapshot, and prevents duplicate orders with an idempotency key. Admins can view orders and advance their status.

Availability and vendor allocation are not reserved automatically. The first order state is `PLACED`; staff confirm stock and delivery before moving it to `CONFIRMED`. Delivery is currently free and included as zero in the total. Customer accounts, online payments, inventory synchronization, and production staff identity management remain future work.

For a deployment, do not enable `AUTH_DEV_EPHEMERAL_KEYS`. Mount a persistent RSA private key and public key into the auth-service container and set `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH`. Set a strong admin password through Secret Manager as `ADMIN_PASSWORD` or a BCrypt hash as `ADMIN_PASSWORD_HASH`. Set the same HTTPS `JWT_ISSUER` on all four services, and `AUTH_JWKS_URI` on the catalog, sourcing, and order services. The signing private key belongs only to auth-service. Do not store keys or passwords in this repository. See [docs/architecture.md](docs/architecture.md) for remaining production requirements.

The web project uses Create React App as requested. React has deprecated it for new projects; plan to migrate the admin build tooling before long-term production maintenance.
