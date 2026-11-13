#!/usr/bin/env python3
import hashlib
import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE_URL = os.environ.get("DEMO_BASE_URL", "http://localhost:8080")
CHUNK_SIZE = 64 * 1024
ACCESS_TOKEN = None


def call(method, path, body=None, content_type="application/json", headers=None):
    data = body
    request_headers = dict(headers or {})
    if ACCESS_TOKEN:
        request_headers["Authorization"] = "Bearer " + ACCESS_TOKEN
    if body is not None and content_type == "application/json" and not isinstance(body, bytes):
        data = json.dumps(body).encode("utf-8")
    if data is not None:
        request_headers["Content-Type"] = content_type
    request = urllib.request.Request(BASE_URL + path, data=data, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = response.read()
            if response.headers.get("Content-Type", "").startswith("application/json"):
                return json.loads(payload)
            return payload
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"{method} {path} -> {error.code}: {error.read().decode('utf-8')}") from error


def main():
    global ACCESS_TOKEN
    login = call("POST", "/api/v1/auth/login", {
        "username": os.environ.get("ADMIN_USERNAME", "admin"),
        "password": os.environ.get("ADMIN_PASSWORD", "robotwin-local-admin"),
    })
    ACCESS_TOKEN = login["accessToken"]
    suffix = str(int(time.time()))
    model = call("POST", "/api/v1/models", {
        "name": "pi05-bread",
        "version": "demo-" + suffix,
        "baseModel": "pi0.5",
        "artifactUri": "local://models/demo-" + suffix,
        "artifactSha256": "a" * 64,
    })
    evaluation = call("POST", "/api/v1/evaluations", {
        "modelVersionId": model["id"],
        "name": "Demo evaluation / seed 42",
        "sceneSeeds": [2, 3, 4, 8, 11],
        "inferenceSeed": 42,
        "maxSteps": 400,
        "numInferenceSteps": 10,
        "maxAttempts": 2,
        "parameters": {"executor": "demo"},
    })
    evaluation_id = evaluation["id"]
    call("POST", f"/api/v1/evaluations/{evaluation_id}/start")
    for _ in range(100):
        evaluation = call("GET", f"/api/v1/evaluations/{evaluation_id}")
        if evaluation["status"] in ("SUCCEEDED", "FAILED"):
            break
        time.sleep(0.05)
    if evaluation["status"] != "SUCCEEDED":
        raise RuntimeError(f"Demo evaluation did not succeed: {evaluation}")
    episodes = call("GET", f"/api/v1/evaluations/{evaluation_id}/episodes")
    audit = call("GET", f"/api/v1/audit-logs/EVALUATION/{evaluation_id}")

    imported_evaluation = call("POST", "/api/v1/evaluations", {
        "modelVersionId": model["id"],
        "name": "Imported local score_audit / seed 42",
        "sceneSeeds": [2, 3, 4, 8, 11],
        "inferenceSeed": 42,
        "maxSteps": 400,
    })
    score_audit = json.loads(Path("examples/score_audit.json").read_text(encoding="utf-8"))
    imported = call("POST", f"/api/v1/evaluations/{imported_evaluation['id']}/results", score_audit)

    content = bytes((index * 31) % 256 for index in range(CHUNK_SIZE * 2 + 321))
    digest = hashlib.sha256(content).hexdigest()
    upload = call("POST", "/api/v1/uploads", {
        "fileName": "demo-artifact.bin",
        "totalSize": len(content),
        "chunkSize": CHUNK_SIZE,
        "sha256": digest,
    })
    upload_id = upload["uploadId"]
    parts = [content[:CHUNK_SIZE], content[CHUNK_SIZE:CHUNK_SIZE * 2], content[CHUNK_SIZE * 2:]]
    for part_number in (2, 1, 3):
        chunk = parts[part_number - 1]
        call("PUT", f"/api/v1/uploads/{upload_id}/chunks/{part_number}", chunk,
             "application/octet-stream", {"X-Chunk-SHA256": hashlib.sha256(chunk).hexdigest()})
    duplicate = parts[1]
    resumed = call("PUT", f"/api/v1/uploads/{upload_id}/chunks/2", duplicate,
                   "application/octet-stream", {"X-Chunk-SHA256": hashlib.sha256(duplicate).hexdigest()})
    assert resumed["uploadedBytes"] == len(content)
    completed = call("POST", f"/api/v1/uploads/{upload_id}/complete")
    downloaded = call("GET", f"/api/v1/uploads/{upload_id}/content")
    assert hashlib.sha256(downloaded).hexdigest() == digest

    print(json.dumps({
        "demoEvaluation": {
            "id": evaluation_id,
            "status": evaluation["status"],
            "episodes": len(episodes),
            "successfulTasks": evaluation["successCount"],
            "auditEvents": [event["action"] for event in audit],
            "note": "Synthetic executor output, not an official RoboTwin score",
        },
        "importedLocalAudit": {
            "id": imported["id"],
            "totalScore": imported["totalScore"],
            "successCount": imported["successCount"],
            "meanSuccessSteps": imported["meanSuccessSteps"],
            "note": "Local historical evaluation data, not an official ranking",
        },
        "upload": {
            "id": upload_id,
            "status": completed["status"],
            "chunks": completed["receivedParts"],
            "sha256": completed["actualSha256"],
            "downloadVerified": True,
        },
        "swagger": BASE_URL + "/swagger-ui.html",
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
