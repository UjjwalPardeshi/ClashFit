#!/usr/bin/env python3
"""
Check that the DEPLOYED Firestore rules do what firestore.rules claims.

`firebase deploy` reports that the rules compiled and were released. That says nothing about
whether they actually refuse what they are supposed to refuse. This signs in as a real account and
tries, against the live project, each thing the rules should allow and each thing they should
block — including the two that matter most: a profile carrying an email address, and a write to
somebody else's document.

    FIREBASE_TEST_EMAIL=you@example.com \
    FIREBASE_TEST_PASSWORD=... \
    python3 firebase/verify-rules.py

The API key and project id are read from android/local.properties, which is git-ignored. The key
is the app's public client key — it ships inside every copy of the APK and is not a secret — and
it is sent only to Google's own endpoints, which is what it is for. Credentials come from the
environment so nothing lands in the repository.

Exit code is 0 when every rule behaved as written, 1 otherwise.
"""

import json
import os
import pathlib
import re
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
PROPS = ROOT / "android/local.properties"


def config() -> tuple[str, str]:
    if not PROPS.exists():
        sys.exit(f"{PROPS} is missing. See android/README.md for what goes in it.")
    text = PROPS.read_text()
    key = re.search(r"FIREBASE_API_KEY\s*=\s*(\S+)", text)
    project = re.search(r"FIREBASE_PROJECT_ID\s*=\s*(\S+)", text)
    if not key or not project:
        sys.exit("local.properties needs FIREBASE_API_KEY and FIREBASE_PROJECT_ID.")
    return key.group(1).strip(), project.group(1).strip()


def request(method: str, url: str, body: dict | None = None, token: str | None = None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode() if body is not None else None,
        method=method,
        headers=headers,
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")


def main() -> int:
    api_key, project = config()
    email = os.environ.get("FIREBASE_TEST_EMAIL")
    password = os.environ.get("FIREBASE_TEST_PASSWORD")
    if not email or not password:
        sys.exit("Set FIREBASE_TEST_EMAIL and FIREBASE_TEST_PASSWORD.")

    status, auth = request(
        "POST",
        f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={api_key}",
        {"email": email, "password": password, "returnSecureToken": True},
    )
    if status != 200:
        sys.exit(f"Sign-in failed: {auth.get('error', {}).get('message', status)}")
    token, uid = auth["idToken"], auth["localId"]
    print(f"Signed in against {project}, uid {uid[:6]}…\n")

    base = f"https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents"

    def doc(method, path, fields=None, authed=True):
        body = {"fields": fields} if fields is not None else None
        return request(method, f"{base}/{path}", body, token if authed else None)[0]

    def s(v):
        return {"stringValue": v}

    def i(v):
        return {"integerValue": str(v)}

    # Read the real profile first so it can be put back. A verifier that leaves "Rules Check" on
    # the leaderboard has traded one problem for another.
    before_status, before = request("GET", f"{base}/users/{uid}", token=token)
    original = before.get("fields") if 200 <= before_status < 300 else None

    profile = {"displayName": s("Rules Check"), "level": i(1), "xp": i(0), "bestStreak": i(0)}
    score = {"displayName": s("Rules Check"), "level": i(1), "weeklyDamage": i(0), "weeklyCleanReps": i(0)}

    # (description, http status, should it have been allowed)
    cases: list[tuple[str, int, bool]] = [
        ("read the users collection while signed in", doc("GET", "users"), True),
        ("write my own profile", doc("PATCH", f"users/{uid}", profile), True),
        ("write my own weekly score", doc("PATCH", f"scores/2026-W36/entries/{uid}", score), True),
        ("read the users collection signed out", doc("GET", "users", authed=False), False),
        ("write a profile containing an email address",
         doc("PATCH", f"users/{uid}", {**profile, "email": s("leaked@example.com")}), False),
        ("write somebody else's profile",
         doc("PATCH", "users/somebody-elses-uid", profile), False),
        ("write somebody else's weekly score",
         doc("PATCH", "scores/2026-W36/entries/somebody-else", score), False),
        ("write a collection the rules never mention",
         doc("PATCH", "secrets/anything", {"x": s("y")}), False),
        ("write a profile with an empty display name",
         doc("PATCH", f"users/{uid}", {**profile, "displayName": s("")}), False),
    ]

    failures = []
    for name, status, want_allowed in cases:
        allowed = 200 <= status < 300
        ok = allowed == want_allowed
        if not ok:
            failures.append(name)
        print(f"  [{'PASS' if ok else 'FAIL'}] {name} "
              f"(http {status}, {'allowed' if allowed else 'refused'})")

    # Put the profile back exactly as it was, so a check costs nothing.
    if original:
        restored = doc("PATCH", f"users/{uid}", original)
        print(f"\nProfile restored ({'ok' if 200 <= restored < 300 else f'http {restored}'})")
    else:
        print("\nNo profile existed before this run; the app will write one on the next sync.")

    print(f"\n{len(cases) - len(failures)}/{len(cases)} rules behaved as written")
    for name in failures:
        print(f"  FAILED: {name}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
