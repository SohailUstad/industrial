# GCP and Neon deployment plan

## Target checked on 2026-11-06

- gcloud account: `nobisystems.com@gmail.com` (already active)
- GCP project visible to that account: `project-22d3e250-6dee-4cfa-bfb` (`My First Project`), with billing enabled
- The saved gcloud default project is `nooriclasses`, so every deployment command must include `--project=project-22d3e250-6dee-4cfa-bfb` and `--account=nobisystems.com@gmail.com`.
- Cloud Run, Cloud Build, Artifact Registry, Secret Manager, and IAM APIs are enabled in that project.
- Neon organization: `clockskew`, Free plan. Project `bitter-rain-34500292` is in `aws-ap-southeast-1` with separate `catalog`, `sourcing`, and `orders` databases.
- The four backend services are deployed with public Cloud Run invoker access in `asia-southeast1`. Each data service has the `postgres` profile and a scoped Neon database role. Flyway created its tables in Neon.

| Service | Public Cloud Run URL |
| --- | --- |
| Auth | `https://industrial-auth-1057772201167.asia-southeast1.run.app` |
| Catalog | `https://industrial-catalog-1057772201167.asia-southeast1.run.app` |
| Sourcing | `https://industrial-sourcing-1057772201167.asia-southeast1.run.app` |
| Orders | `https://industrial-orders-1057772201167.asia-southeast1.run.app` |

Cloud Run also exposes a stable hash-based URL for each service. Use `gcloud run services describe` to read the displayed URL. Public product reads, admin JWT calls, and catalog validation during checkout have been verified. Admin routes still require a valid marketplace JWT. The React app uses the four URLs above, and the Android app uses catalog and orders. The hosted catalog currently has no products.

The backend CI/CD definition is [../cloudbuild.yaml](../cloudbuild.yaml): one `main` branch build that tests, builds, and deploys the four Spring services. A manual run of this pipeline succeeded on 2026-11-06 (Cloud Build ID `a7d013b4-37ae-40cf-9647-87a03742cadc`), and all four redeployed services returned `UP` from `/actuator/health`. The GitHub repository has not yet been connected to Cloud Build, so no automatic trigger exists. The first Cloud Run deployment was performed from local source.

## Service order and configuration

1. Choose a Neon region and a nearby Cloud Run region; confirm the placement before creating the Neon project. Create one Neon project with separate `catalog`, `sourcing`, and `orders` databases and database roles. The three databases share that project's Free plan compute and storage limits. Keep product images outside PostgreSQL.
2. Obtain each database's pooled Neon host, database name, role, and password. Set a **JDBC** URL such as `jdbc:postgresql://NEON-POOLER-HOST:5432/catalog?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`; store the password in GCP Secret Manager. The Java SSL factory uses the JVM trust store for certificate validation. Do not put passwords into source files or gcloud command arguments.
3. Enable `run.googleapis.com`, `cloudbuild.googleapis.com`, `artifactregistry.googleapis.com`, and `secretmanager.googleapis.com` on the target project. Create runtime service accounts and give them access only to the secrets they need.
4. Generate a persistent RSA signing key pair. Store the private key, public key, and admin password hash in Secret Manager. Deploy `auth-service` from its folder's Dockerfile with the key secrets mounted as files. Retrieve its Cloud Run HTTPS URL, set `JWT_ISSUER` to that URL, and update the auth service. `AUTH_DEV_EPHEMERAL_KEYS` must remain false.
5. Deploy `catalog-service` and `sourcing-service` from their own folders. Set `SPRING_PROFILES_ACTIVE=postgres`; provide each service's own `DB_URL`, `DB_USER`, and `DB_PASSWORD` through Secret Manager. Set `JWT_ISSUER` to the auth URL and `AUTH_JWKS_URI` to `AUTH_URL/.well-known/jwks.json`. Flyway runs the service's migrations on startup. A missing PostgreSQL secret fails startup rather than falling back to the local H2 profile.
6. Retrieve the catalog URL, then deploy `order-service` with its own Neon secrets, the same JWT settings, and `CATALOG_API_URL` set to the catalog URL. This order lets checkout fetch live catalog prices.
7. Verify `/actuator/health`, public product reads, admin JWT access, and order placement. Confirm each service has `SPRING_PROFILES_ACTIVE=postgres` and the expected Neon database without printing secret values. Then configure the React admin origin and build the Android release app with the catalog and order HTTPS URLs.

Cloud Run injects `PORT`; each Spring service reads it. Set a small maximum instance count while using the Neon Free plan because every instance has a Hikari connection pool. Use `--source` from each service folder to build its Dockerfile with Cloud Build and store the resulting image in Artifact Registry.

## URLs

Each backend service gets its own Cloud Run HTTPS URL after deployment. Read a URL with:

```powershell
gcloud run services describe catalog-service --region=REGION --project=project-22d3e250-6dee-4cfa-bfb --account=nobisystems.com@gmail.com --format='value(status.url)'
```

Repeat for `auth-service`, `sourcing-service`, and `order-service`. The Android app uses catalog and order URLs; the React admin app also uses auth and sourcing URLs. A single public API hostname would require an API gateway or reverse proxy, which is not in the current implementation.

The user authorized public access after the initial automatic approval rejection. All four Cloud Run services now allow unauthenticated requests through Cloud Run IAM; Spring Security still requires a marketplace JWT for `/api/admin/**`. The login and checkout routes are public. Rate limiting, customer identity, and a secure order lookup design are still needed before a production launch with real customer data.
