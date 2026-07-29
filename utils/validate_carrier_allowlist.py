#!/usr/bin/env python3
#
# Copyright (C) 2026 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import json
import os
import re
import sys

def validate_json_format(json_path):
    with open(json_path, 'r') as f:
        data = json.load(f)

    errors = []
    for pkg, info in data.items():
        if pkg.startswith("_comment"):
            continue
        sha_ids = info.get("callerSHA256Ids", [])
        for sha_id in sha_ids:
            if ":" in sha_id:
                errors.append(f"Package '{pkg}' has SHA256 ID with colons: '{sha_id}'. Format should be continuous hex string.")
    return errors, data

def validate_consistency(json_data, cts_path):
    if not os.path.exists(cts_path):
        return [f"CTS file not found at {cts_path}"]

    with open(cts_path, 'r') as f:
        cts_content = f.read()

    errors = []
    # Extract the buildCarrierRestrictionOperatorDetails method content or just look for strings
    for pkg, info in json_data.items():
        if pkg.startswith("_comment"):
            continue

        # Check if package name exists in CTS
        if pkg not in cts_content:
            errors.append(f"Package '{pkg}' found in JSON but missing in TelephonyManagerTest.java")
            continue

        # Check if all SHA IDs exist in CTS
        sha_ids = info.get("callerSHA256Ids", [])
        for sha_id in sha_ids:
            if sha_id not in cts_content:
                errors.append(f"SHA256 ID '{sha_id}' for package '{pkg}' missing in TelephonyManagerTest.java")
    return errors

def main():
    # Attempt to find repo root from script location: ROOT/packages/services/Telephony/utils/validate_carrier_allowlist.py
    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.abspath(os.path.join(script_dir, "..", "..", "..", ".."))

    # Fallback to REPO_ROOT or ANDROID_BUILD_TOP if repo_root doesn't seem right
    if not os.path.exists(os.path.join(repo_root, "packages/services/Telephony")):
        repo_root = os.environ.get("REPO_ROOT") or os.environ.get("ANDROID_BUILD_TOP", ".")

    telephony_path = os.path.join(repo_root, "packages/services/Telephony")
    json_path = os.path.join(telephony_path, "assets/CarrierRestrictionOperatorDetails.json")
    cts_path = os.path.join(repo_root, "cts/tests/tests/telephony/current/src/android/telephony/cts/TelephonyManagerTest.java")

    if not os.path.exists(json_path):
        # Last attempt: if we're running from within Telephony directory
        if os.path.exists("assets/CarrierRestrictionOperatorDetails.json"):
            json_path = "assets/CarrierRestrictionOperatorDetails.json"
            # We also need to find cts_path. If we're in Telephony dir, we're at <root>/packages/services/Telephony
            # so root is ../../..
            cts_path = os.path.join("../../..", "cts/tests/tests/telephony/current/src/android/telephony/cts/TelephonyManagerTest.java")
        else:
            print(f"Error: JSON file not found at {json_path}")
            sys.exit(1)

    json_errors, json_data = validate_json_format(json_path)
    consistency_errors = validate_consistency(json_data, cts_path)

    all_errors = json_errors + consistency_errors
    if all_errors:
        for error in all_errors:
            print(f"VALIDATION ERROR: {error}")
        sys.exit(1)

    print("Carrier allowlist validation passed.")
    sys.exit(0)

if __name__ == "__main__":
    main()
