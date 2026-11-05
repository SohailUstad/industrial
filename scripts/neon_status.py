"""Read-only Neon account and project summary using the signed-in Neon CLI."""

import argparse
import json
import os
import shutil
import subprocess
import sys


FREE_PLAN = {
    "projects": 100,
    "branches_per_project": 10,
    "compute_cu_hours_per_project_month": 100,
    "postgres_storage_gb_per_project": 1,
    "postgres_storage_gb_account": 20,
    "public_transfer_gb_per_project": 5,
}
PLAN_SOURCE = "https://neon.com/docs/introduction/plans"
LIMITS_CHECKED = "2026-11-06"


def cli_prefix():
    installed = shutil.which("neon") or shutil.which("neonctl")
    if installed:
        return [installed]
    npm = shutil.which("npx") or shutil.which("npx.cmd")
    if not npm:
        raise RuntimeError("Install the Neon CLI or Node.js/npm before running this checker")
    return [npm, "--yes", "neon@latest"]


def neon_json(prefix, *args):
    env = os.environ.copy()
    env.pop("DEBUG", None)  # Keep CLI debug logs out of JSON output.
    env["NPM_CONFIG_OFFLINE"] = "false"  # npx may need to fetch the CLI.
    command = [*prefix, *args, "--output", "json", "--no-analytics", "--no-color"]
    result = subprocess.run(command, capture_output=True, text=True, env=env, check=False)
    if result.returncode:
        detail = result.stderr.strip().splitlines()[-1:] or ["Neon CLI command failed"]
        raise RuntimeError(detail[0])
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as error:
        raise RuntimeError("Neon CLI did not return JSON; check its login and version") from error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--org-id", help="Neon organization ID; required when signed into multiple organizations")
    parser.add_argument("--json", action="store_true", help="Print machine-readable JSON")
    args = parser.parse_args()

    prefix = cli_prefix()
    organizations = neon_json(prefix, "orgs", "list")
    if args.org_id:
        organization = next((item for item in organizations if item["id"] == args.org_id), None)
        if organization is None:
            raise RuntimeError("Organization ID is not available to the signed-in Neon account")
    elif len(organizations) == 1:
        organization = organizations[0]
    else:
        names = ", ".join(f"{item['name']} ({item['id']})" for item in organizations)
        raise RuntimeError(f"Specify --org-id; available organizations: {names or 'none'}")

    projects = neon_json(prefix, "projects", "list", "--org-id", organization["id"])
    summary = {
        "organization": organization["name"],
        "organization_id": organization["id"],
        "plan": organization["plan"],
        "project_count": len(projects),
        "projects": [
            {"name": item.get("name"), "id": item.get("id"), "region": item.get("region_id")}
            for item in projects
        ],
    }
    if organization["plan"] == "free":
        summary["free_plan_limits"] = FREE_PLAN
        summary["limits_source"] = PLAN_SOURCE
        summary["limits_checked"] = LIMITS_CHECKED

    if args.json:
        print(json.dumps(summary, indent=2))
        return

    print(f"Organization: {summary['organization']} ({summary['organization_id']})")
    print(f"Plan: {summary['plan']}")
    print(f"Projects: {summary['project_count']}" +
          (f" / {FREE_PLAN['projects']}" if organization["plan"] == "free" else ""))
    for item in summary["projects"]:
        print(f"  - {item['name']} ({item['id']}) in {item['region']}")
    if organization["plan"] == "free":
        print("Free limits per project: 100 CU-hours/month, 1 GB PostgreSQL storage, "
              "10 branches, 5 GB public transfer")
        print(f"Free account storage limit: {FREE_PLAN['postgres_storage_gb_account']} GB")
        print(f"Limits checked: {LIMITS_CHECKED}; source: {PLAN_SOURCE}")
    print("Per-project compute, storage, and transfer usage: check the Neon Console.")


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        print(f"Neon status: {error}", file=sys.stderr)
        sys.exit(1)
