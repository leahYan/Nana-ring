#!/usr/bin/env python3
"""
Generate JWT tokens for Nana Ring testers and the admin account.

Run from the ring-backend directory:
    python generate_tokens.py

Reads JWT_SECRET_KEY and ALGORITHM from .env automatically via pydantic-settings.
Each run generates fresh UUIDs — save the output somewhere safe (e.g. 1Password).
The user_id in the token IS the user identity; there is no separate user table.
"""

import sys
import uuid
from pathlib import Path

# Allow running as `python generate_tokens.py` from ring-backend/
sys.path.insert(0, str(Path(__file__).parent))

from app.core.security import create_access_token  # noqa: E402 (path insert above)

# ---------------------------------------------------------------------------
# Define the 6 identities: 5 testers + 1 admin
# Customise the names before distributing tokens.
# ---------------------------------------------------------------------------
IDENTITIES = [
    {"name": "Tester-1", "is_admin": False},
    {"name": "Tester-2", "is_admin": False},
    {"name": "Tester-3", "is_admin": False},
    {"name": "Tester-4", "is_admin": False},
    {"name": "Tester-5", "is_admin": False},
    {"name": "Admin",    "is_admin": True},
]


def main():
    print("\nNana Ring — Token Generator")
    print("=" * 72)
    print("Each user_id is a fresh UUID.  Store these — they cannot be regenerated.")
    print("=" * 72)

    for identity in IDENTITIES:
        user_id = str(uuid.uuid4())
        token = create_access_token({
            "sub":      user_id,
            "name":     identity["name"],
            "is_admin": identity["is_admin"],
        })

        print(f"\nName    : {identity['name']}")
        print(f"Admin   : {identity['is_admin']}")
        print(f"User ID : {user_id}")
        print(f"Token   : {token}")

    print("\n" + "=" * 72)
    print("Paste each tester's Token into the Android app's Developer Settings.")
    print("The Admin token is for the future dashboard's admin query parameter.")
    print("=" * 72 + "\n")


if __name__ == "__main__":
    main()
