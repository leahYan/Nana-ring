import requests
import time
import uuid

# ---------------------------------------------------------------------------
# Configuration
# Run `python generate_tokens.py` and paste one of the tester tokens below.
# ---------------------------------------------------------------------------
API_URL = "http://localhost/api/v1/data/batch"  # hits Nginx on port 80
TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyN2E1MzE2Yy1hYmZhLTQ0YWYtYTYxOC0wOTg2OTNkOWQyNjkiLCJuYW1lIjoiQWRtaW4iLCJpc19hZG1pbiI6dHJ1ZSwiZXhwIjoxNzc4NTY3OTc5fQ.fTNmYhlioya5puxtcvxm1U4QRtQRNhAnJq1oU6b4zEo"

# ---------------------------------------------------------------------------
# Test payload — field names match ring_data_dictionary.csv exactly
# ---------------------------------------------------------------------------
payload = {
    "heart_rate": [
        {
            "device_timestamp": int(time.time() * 1000),
            "bpm": 72,
            "rri": 850,
            "source": "manual",
            "sync_id": str(uuid.uuid4()),
            "firmware_version": "1.0.0.1",
            "hardware_version": "HW_v2",
        }
    ],
    "spo2": [
        {
            "device_timestamp": int(time.time() * 1000),
            "percent": 98,
            "reading_type": "manual",
            "sync_id": str(uuid.uuid4()),
        }
    ],
    "stress": [
        {
            "device_timestamp": int(time.time() * 1000),
            "level": 35,
            "source": "manual",
            "sync_id": str(uuid.uuid4()),
        }
    ],
    "blood_pressure": [],
    "temperature": [],
    "hrv": [],
    "activity": [],
    "activity_detail": [],
    "sleep": [],
}

# ---------------------------------------------------------------------------
# Send
# ---------------------------------------------------------------------------
headers = {"Authorization": f"Bearer {TOKEN}"}

print(f"Sending test batch to {API_URL}...")
response = requests.post(API_URL, json=payload, headers=headers)

print(f"Status : {response.status_code}")
try:
    print(f"Body   : {response.json()}")
except Exception:
    print(f"Body   : {response.text}")
