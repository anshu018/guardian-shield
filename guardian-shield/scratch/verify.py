import urllib.request
import json
import ssl
import subprocess
import time
import os

url = "https://rrdkngvqlqecdqnpargg.supabase.co"
anon_key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJyZGtuZ3ZxbHFlY2RxbnBhcmdnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzcyMjIzNjksImV4cCI6MjA5Mjc5ODM2OX0.OdCBHvEq-W7g_Nrxz5oaXrlpNmu32X57BmOezcOO2i4"
refresh_token = "kqitzh4kfq7u"

adb_path = r"C:\Users\ash74\AppData\Local\Android\Sdk\platform-tools\adb.exe"
screenshot_path = r"C:\Users\ash74\.gemini\antigravity\brain\7f804459-a976-4ab0-9338-ee697929f7fc\parent_screen.png"

context = ssl._create_unverified_context()

# 1. Refresh parent session first to get parent_jwt
print("--> Refreshing parent session...")
refresh_url = f"{url}/auth/v1/token?grant_type=refresh_token"
headers_refresh = {
    "apikey": anon_key,
    "Content-Type": "application/json"
}
data_refresh = json.dumps({"refresh_token": refresh_token}).encode('utf-8')
req_refresh = urllib.request.Request(refresh_url, data=data_refresh, headers=headers_refresh, method="POST")

try:
    with urllib.request.urlopen(req_refresh, context=context) as response:
        res = json.loads(response.read().decode('utf-8'))
        parent_jwt = res.get("access_token")
        print("    Token refreshed successfully!")
except Exception as e:
    print("    Parent token refresh failed:", e)
    parent_jwt = None

# 2. Insert mock location using parent_jwt
if parent_jwt:
    print("--> Inserting mock location using parent JWT...")
    insert_data = json.dumps({
        "child_id": "40ba2e04-a40b-44ba-b7d1-f23497364929",
        "latitude": 30.37980,
        "longitude": 77.82700,
        "battery_percentage": 85,
        "accuracy_radius": 5.0
    }).encode('utf-8')

    insert_headers = {
        "apikey": anon_key,
        "Authorization": f"Bearer {parent_jwt}",
        "Content-Type": "application/json",
        "Prefer": "return=representation"
    }

    req_insert = urllib.request.Request(f"{url}/rest/v1/child_location", data=insert_data, headers=insert_headers, method="POST")
    try:
        with urllib.request.urlopen(req_insert, context=context) as response:
            print("    Mock location inserted successfully!")
            print(response.read().decode('utf-8'))
    except Exception as e:
        print("    Insert Failed:", e)
else:
    print("    Cannot insert mock location without parent JWT.")

# 3. Stop and start the parent app on emulator-5554
print("--> Restarting parent app on emulator-5554...")
try:
    subprocess.run([adb_path, "-s", "emulator-5554", "shell", "am", "force-stop", "com.guardianshield.parent"], check=True)
    subprocess.run([adb_path, "-s", "emulator-5554", "shell", "am", "start", "-n", "com.guardianshield.parent/.ui.auth.AuthActivity"], check=True)
    print("    App started.")
except Exception as e:
    print("    ADB App restart failed:", e)

# 4. Wait for the app to sync and update the UI
print("--> Waiting 8 seconds for dashboard to update...")
time.sleep(8)

# 5. Take a screenshot
print("--> Taking screenshot of emulator-5554...")
try:
    subprocess.run([adb_path, "-s", "emulator-5554", "shell", "screencap", "-p", "/sdcard/screen.png"], check=True)
    subprocess.run([adb_path, "-s", "emulator-5554", "pull", "/sdcard/screen.png", screenshot_path], check=True)
    print(f"    Screenshot saved to {screenshot_path}")
except Exception as e:
    print("    Screenshot capture failed:", e)

# 6. Query locations to confirm DB state
if parent_jwt:
    print("--> Querying database to show current state of locations...")
    headers_query = {
        "apikey": anon_key,
        "Authorization": f"Bearer {parent_jwt}",
        "Content-Type": "application/json"
    }
    
    req_query = urllib.request.Request(
        f"{url}/rest/v1/child_location?child_id=eq.40ba2e04-a40b-44ba-b7d1-f23497364929&order=created_at.desc&limit=3",
        headers=headers_query
    )
    try:
        with urllib.request.urlopen(req_query, context=context) as response:
            print("\nLatest DB Location entries for Aarav ( Nothing Phone 1 ):")
            print(json.dumps(json.loads(response.read().decode('utf-8')), indent=2))
    except Exception as e:
        print("    Failed to query locations:", e)
