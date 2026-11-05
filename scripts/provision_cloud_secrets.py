"""Provision the marketplace's Neon and auth secrets in the chosen GCP project."""

import json
import os
import secrets
import shutil
import subprocess

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa


GCP_PROJECT = "project-22d3e250-6dee-4cfa-bfb"
GCP_ACCOUNT = "nobisystems.com@gmail.com"
NEON_PROJECT = "bitter-rain-34500292"
DATABASES = {"catalog": "catalog_app", "sourcing": "sourcing_app", "orders": "orders_app"}


def call(args, *, input_bytes=None):
    env = os.environ.copy()
    env.pop("DEBUG", None)
    env["NPM_CONFIG_OFFLINE"] = "false"
    result = subprocess.run(args, input=input_bytes, capture_output=True, env=env, check=False)
    if result.returncode:
        # Never include stdout or command arguments here: Neon responses may contain passwords.
        raise RuntimeError(f"Command failed ({args[0]}, exit {result.returncode})")
    return result.stdout


def gcloud(*args, input_bytes=None):
    executable = shutil.which("gcloud") or shutil.which("gcloud.cmd")
    if not executable:
        raise RuntimeError("gcloud CLI is not installed")
    return call([executable, *args, f"--project={GCP_PROJECT}", f"--account={GCP_ACCOUNT}", "--quiet"],
                input_bytes=input_bytes)


def neon_connection(database, role):
    npx = shutil.which("npx") or shutil.which("npx.cmd")
    if not npx:
        raise RuntimeError("npx is not installed")
    raw = call([npx, "--yes", "neon@latest", "connection-string",
                "--project-id", NEON_PROJECT, "--role-name", role,
                "--database-name", database, "--pooled", "--ssl", "verify-full",
                "--extended", "--output", "json", "--no-analytics", "--no-color"])
    data = json.loads(raw)
    if data.get("database") != database or data.get("role") != role or not data.get("password"):
        raise RuntimeError(f"Unexpected Neon connection details for {database}")
    if not str(data.get("host", "")).endswith(".neon.tech"):
        raise RuntimeError(f"Unexpected Neon host for {database}")
    return data


def secret_exists(name):
    executable = shutil.which("gcloud") or shutil.which("gcloud.cmd")
    result = subprocess.run([executable, "secrets", "describe", name,
                             f"--project={GCP_PROJECT}", f"--account={GCP_ACCOUNT}",
                             "--format=value(name)", "--quiet"], capture_output=True)
    if result.returncode == 0:
        return True
    if b"NOT_FOUND" in result.stderr or b"not found" in result.stderr.lower():
        return False
    raise RuntimeError(f"Could not inspect secret {name} (exit {result.returncode})")


def create_secret(name, value):
    if secret_exists(name):
        print(f"Existing secret: {name}")
        return
    gcloud("secrets", "create", name, "--replication-policy=automatic", "--data-file=-",
           input_bytes=value)
    print(f"Created secret: {name}")


def main():
    for database, role in DATABASES.items():
        connection = neon_connection(database, role)
        create_secret(f"industrial-{database}-db-password", connection["password"].encode())
        url = (f"jdbc:postgresql://{connection['host']}:5432/{database}"
               "?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory")
        print(f"{database}: DB_URL={url} DB_USER={role}")

    private_name = "industrial-auth-private-key"
    public_name = "industrial-auth-public-key"
    private_exists = secret_exists(private_name)
    public_exists = secret_exists(public_name)
    if private_exists != public_exists:
        raise RuntimeError("Auth key secrets are incomplete; repair the pair before continuing")
    if not private_exists:
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        private_pem = key.private_bytes(serialization.Encoding.PEM,
                                        serialization.PrivateFormat.PKCS8,
                                        serialization.NoEncryption())
        public_pem = key.public_key().public_bytes(serialization.Encoding.PEM,
                                                   serialization.PublicFormat.SubjectPublicKeyInfo)
        create_secret(private_name, private_pem)
        create_secret(public_name, public_pem)
    else:
        print("Existing auth key pair secrets")

    create_secret("industrial-admin-password", secrets.token_urlsafe(32).encode())


if __name__ == "__main__":
    main()
