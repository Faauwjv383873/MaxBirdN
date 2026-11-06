#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MaxBird 24/7 Autonomous AI Agent Cloud Worker
=============================================
Executes background tasks and 24/7 scheduled routines dispatched by the MaxBird Android App:
1. Connects to the user's Personal Telegram Account via Telethon (MTProto StringSession)
   or Telegram Bot API.
2. Syncs the user's real Telegram Channels, Supergroups, and Forum Topics back to the app
   so the user can pick any Channel/Group/Topic directly from Chapter or Class screens.
3. Automatically creates Telegram Channels or Supergroups (including Subject/Chapter Forum Topics)
   when requested, and exports invite links back to Supabase & the MaxBird App.
4. Fetches course subjects, chapters, recorded/live video HLS (.m3u8) / MP4 streams, and
   Lecture Slide PDFs directly from Shikho GraphQL API using exact Android client headers & schema.
5. Downloads videos (via yt-dlp + ffmpeg with Shikho CDN Referer/Origin headers) & PDF slides
   one by one, uploads them to Telegram (including specific Forum Topics via reply_to) with rich
   Bengali captions and streaming support, immediately deletes local temp files, and updates live progress.
6. Self-chains (re-triggers itself via GitHub API) before the 6-hour GitHub Actions limit
   if more items remain in the queue, achieving true 24/7 lifetime operation.
"""

import asyncio
import base64
import datetime
import hashlib
import json
import os
import re
import subprocess
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import requests
from telethon import TelegramClient, functions, types
from telethon.errors import FloodWaitError, SessionPasswordNeededError
from telethon.sessions import StringSession
from telethon.tl.types import DocumentAttributeVideo

START_TIME = time.time()
# Re-trigger self after 4 hours 45 minutes (17100 seconds) so we never hit GitHub's 6h hard timeout
MAX_RUNTIME_SECONDS = 17100

DEFAULT_TELEGRAM_API_ID = "38634154"
DEFAULT_TELEGRAM_API_HASH = "a4194e506db4ad97f1dba20ec28ac383"

SHIKHO_GRAPHQL_URL = "https://api.shikho.com/graphql"
SHIKHO_APP_USER_AGENT = (
    "Shikho/(607) 6.0.7 (Android 12; V2029; vivo 2027; en; WIFI; edac7970-6299-4e14-9b98-8c64d3ca4c09)"
)
SHIKHO_CDN_USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)"
SHIKHO_REFERER = "https://shikho.com/"
SHIKHO_ORIGIN = "https://shikho.com"

WORK_DIR = Path("/tmp/maxbird_agent_work")
WORK_DIR.mkdir(parents=True, exist_ok=True)


def log(msg: str) -> None:
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    print(f"[{now}] {msg}", flush=True)


def seal_secret(plain: str) -> str:
    if not plain:
        return ""
    if plain.startswith("ENC1:"):
        return plain
    key = hashlib.sha256(DEFAULT_TELEGRAM_API_HASH.encode("utf-8")).digest()
    raw = plain.encode("utf-8")
    xored = bytes(b ^ key[i % len(key)] for i, b in enumerate(raw))
    return "ENC1:" + base64.b64encode(xored).decode("ascii")


def unseal_secret(value: str) -> str:
    if not value:
        return ""
    if not value.startswith("ENC1:"):
        return value
    try:
        key = hashlib.sha256(DEFAULT_TELEGRAM_API_HASH.encode("utf-8")).digest()
        raw = base64.b64decode(value[5:])
        plain = bytes(b ^ key[i % len(key)] for i, b in enumerate(raw))
        return plain.decode("utf-8")
    except Exception:
        return ""


def decode_payload_env() -> Dict[str, Any]:
    raw_b64 = os.environ.get("INPUT_TASK_PAYLOAD_B64", "").strip()
    if not raw_b64:
        return {}
    try:
        decoded = base64.b64decode(raw_b64).decode("utf-8")
        return json.loads(decoded)
    except Exception as exc:
        log(f"⚠️ Warning: Could not decode INPUT_TASK_PAYLOAD_B64: {exc}")
        return {}


class GitHubAuthBridge:
    """
    Zero-SQL-dependency bridge using `scripts/agent_auth_state.json` via the GitHub Contents API.
    Guarantees that Telegram OTP, StringSession, Channels/Groups/Topics list, and live task progress
    work 100% reliably.
    """

    def __init__(self, token: str = "", repository: str = "", branch: str = "main"):
        self.token = (token or os.environ.get("GITHUB_TOKEN", "")).strip()
        self.repository = (repository or os.environ.get("GITHUB_REPOSITORY", "")).strip()
        self.branch = (branch or "main").strip() or "main"
        self.path = "scripts/agent_auth_state.json"
        self.enabled = bool(self.token and self.repository and "/" in self.repository)
        self.headers = {
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/vnd.github+json",
            "Cache-Control": "no-cache",
        }

    def read_state(self) -> Dict[str, Any]:
        if not self.enabled:
            return {}
        api_url = (
            f"https://api.github.com/repos/{self.repository}/contents/{self.path}"
            f"?ref={self.branch}&_t={int(time.time() * 1000)}"
        )
        try:
            r = requests.get(api_url, headers=self.headers, timeout=12)
            if r.ok:
                data = r.json()
                content_b64 = data.get("content", "")
                if content_b64:
                    raw_json = base64.b64decode(content_b64).decode("utf-8")
                    parsed = json.loads(raw_json)
                    if isinstance(parsed, dict):
                        for k in ("telegram_session_string", "telegram_partial_session", "telegram_2fa_password"):
                            if parsed.get(k):
                                parsed[k] = unseal_secret(str(parsed[k]))
                        return parsed
        except Exception as exc:
            log(f"GitHubAuthBridge read note: {exc}")
        return {}

    def write_state(self, patch: Dict[str, Any]) -> bool:
        if not self.enabled:
            return False
        api_url = f"https://api.github.com/repos/{self.repository}/contents/{self.path}"
        existing_sha = ""
        merged: Dict[str, Any] = {}
        try:
            r = requests.get(
                f"{api_url}?ref={self.branch}&_t={int(time.time() * 1000)}",
                headers=self.headers,
                timeout=12,
            )
            if r.ok:
                data = r.json()
                existing_sha = str(data.get("sha") or "")
                content_b64 = data.get("content", "")
                if content_b64:
                    merged = json.loads(base64.b64decode(content_b64).decode("utf-8"))
        except Exception:
            pass

        for k, v in patch.items():
            if k in ("telegram_session_string", "telegram_partial_session", "telegram_2fa_password") and v:
                merged[k] = seal_secret(str(v))
            else:
                merged[k] = v
        merged["updated_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()

        encoded = base64.b64encode(json.dumps(merged, indent=2, ensure_ascii=False).encode("utf-8")).decode("ascii")
        body: Dict[str, Any] = {
            "message": "chore(ai-agent): sync telegram session & task state",
            "content": encoded,
            "branch": self.branch,
        }
        if existing_sha:
            body["sha"] = existing_sha

        try:
            put_r = requests.put(api_url, headers=self.headers, json=body, timeout=15)
            return put_r.ok or put_r.status_code in (200, 201)
        except Exception as exc:
            log(f"GitHubAuthBridge write note: {exc}")
            return False


class SupabaseBridge:
    def __init__(self, url: str, anon_key: str):
        self.url = url.strip().rstrip("/")
        self.anon_key = anon_key.strip()
        self.enabled = bool(self.url and self.anon_key)
        self.headers = {
            "apikey": self.anon_key,
            "Authorization": f"Bearer {self.anon_key}",
            "Content-Type": "application/json",
            "Prefer": "return=representation",
        }

    def get_agent_config(self, device_hash: str = "") -> Dict[str, Any]:
        if not self.enabled:
            return {}
        clean_hash = (device_hash or "").strip()
        urls_to_try = []
        if clean_hash:
            urls_to_try.append(
                f"{self.url}/rest/v1/ai_agent_configs?device_hash=eq.{clean_hash}&order=updated_at.desc&limit=1"
            )
            urls_to_try.append(
                f"{self.url}/rest/v1/ai_agent_configs?user_phone=eq.{clean_hash}&order=updated_at.desc&limit=1"
            )
        urls_to_try.append(f"{self.url}/rest/v1/ai_agent_configs?order=updated_at.desc&limit=1")

        for u in urls_to_try:
            try:
                r = requests.get(u, headers=self.headers, timeout=12)
                if r.ok and isinstance(r.json(), list) and r.json():
                    row = r.json()[0]
                    for k in ("telegram_session_string", "telegram_partial_session", "telegram_2fa_password"):
                        if row.get(k):
                            row[k] = unseal_secret(str(row[k]))
                    return row
            except Exception:
                pass
        return {}

    def update_agent_config(self, device_hash: str, patch: Dict[str, Any]) -> None:
        if not self.enabled:
            return
        clean_hash = (device_hash or "GLOBAL_DEFAULT").strip() or "GLOBAL_DEFAULT"
        allowed_cols = {
            "user_phone",
            "device_hash",
            "github_pat",
            "github_owner",
            "github_repo",
            "github_branch",
            "github_username",
            "github_avatar_url",
            "telegram_api_id",
            "telegram_api_hash",
            "telegram_phone",
            "telegram_session_string",
            "telegram_bot_token",
            "telegram_default_chat_id",
            "telegram_account_name",
            "telegram_auth_state",
            "telegram_auth_message",
            "telegram_phone_code_hash",
            "telegram_partial_session",
            "telegram_otp_code",
            "telegram_2fa_password",
            "updated_at",
        }
        safe_patch: Dict[str, Any] = {
            "device_hash": clean_hash,
            "updated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        }
        for k, v in patch.items():
            if k == "default_telegram_chat_id":
                safe_patch["telegram_default_chat_id"] = v
            elif k in allowed_cols:
                safe_patch[k] = v

        try:
            r = requests.patch(
                f"{self.url}/rest/v1/ai_agent_configs?device_hash=eq.{clean_hash}",
                headers=self.headers,
                json=safe_patch,
                timeout=12,
            )
            if r.ok and isinstance(r.json(), list) and len(r.json()) > 0:
                return

            r2 = requests.patch(
                f"{self.url}/rest/v1/ai_agent_configs?user_phone=eq.{clean_hash}",
                headers=self.headers,
                json=safe_patch,
                timeout=12,
            )
            if r2.ok and isinstance(r2.json(), list) and len(r2.json()) > 0:
                return

            insert_payload = dict(safe_patch)
            insert_payload["user_phone"] = clean_hash
            insert_payload["device_hash"] = clean_hash
            hdrs = dict(self.headers)
            hdrs["Prefer"] = "resolution=merge-duplicates"
            r3 = requests.post(
                f"{self.url}/rest/v1/ai_agent_configs?on_conflict=user_phone",
                headers=hdrs,
                json=insert_payload,
                timeout=12,
            )
            if r3.ok:
                return

            requests.post(
                f"{self.url}/rest/v1/ai_agent_configs?on_conflict=device_hash",
                headers=hdrs,
                json=insert_payload,
                timeout=12,
            )
        except Exception as exc:
            log(f"Supabase update_agent_config warning: {exc}")

    def fetch_pending_tasks(self, specific_task_id: str = "") -> List[Dict[str, Any]]:
        if not self.enabled:
            return []
        try:
            if specific_task_id and specific_task_id != "ALL_PENDING":
                endpoint = f"{self.url}/rest/v1/ai_agent_tasks?id=eq.{specific_task_id}&limit=1"
            else:
                endpoint = (
                    f"{self.url}/rest/v1/ai_agent_tasks"
                    f"?status=in.(QUEUED,RUNNING,RESUMING)&order=created_at.asc&limit=10"
                )
            r = requests.get(endpoint, headers=self.headers, timeout=15)
            if r.ok and isinstance(r.json(), list):
                return r.json()
        except Exception as exc:
            log(f"Supabase fetch_pending_tasks warning: {exc}")
        return []

    def fetch_enabled_schedules(self) -> List[Dict[str, Any]]:
        if not self.enabled:
            return []
        try:
            r = requests.get(
                f"{self.url}/rest/v1/ai_agent_schedules?is_enabled=eq.true&order=created_at.asc",
                headers=self.headers,
                timeout=15,
            )
            if r.ok and isinstance(r.json(), list):
                return r.json()
        except Exception as exc:
            log(f"Supabase fetch_enabled_schedules warning: {exc}")
        return []

    def update_task(self, task_id: str, patch: Dict[str, Any]) -> None:
        if not self.enabled or not task_id:
            return
        patch["updated_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        try:
            requests.patch(
                f"{self.url}/rest/v1/ai_agent_tasks?id=eq.{task_id}",
                headers=self.headers,
                json=patch,
                timeout=15,
            )
        except Exception as exc:
            log(f"Supabase update_task warning: {exc}")

    def update_schedule(self, schedule_id: str, patch: Dict[str, Any]) -> None:
        if not self.enabled or not schedule_id:
            return
        patch["updated_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        try:
            requests.patch(
                f"{self.url}/rest/v1/ai_agent_schedules?id=eq.{schedule_id}",
                headers=self.headers,
                json=patch,
                timeout=15,
            )
        except Exception as exc:
            log(f"Supabase update_schedule warning: {exc}")

    def is_item_already_uploaded(
        self, program_id: str, lesson_id: str, item_type: str, chat_id: str
    ) -> bool:
        if not self.enabled or not lesson_id or not chat_id:
            return False
        try:
            r = requests.get(
                f"{self.url}/rest/v1/ai_agent_uploaded_items"
                f"?program_id=eq.{program_id}&lesson_id=eq.{lesson_id}"
                f"&item_type=eq.{item_type}&telegram_chat_id=eq.{chat_id}&select=id&limit=1",
                headers=self.headers,
                timeout=12,
            )
            if r.ok and isinstance(r.json(), list) and len(r.json()) > 0:
                return True
        except Exception:
            pass
        return False

    def record_uploaded_item(
        self,
        task_id: str,
        program_id: str,
        subject_code: str,
        chapter_id: str,
        lesson_id: str,
        item_type: str,
        title: str,
        chat_id: str,
        message_id: int,
    ) -> None:
        if not self.enabled:
            return
        payload = {
            "task_id": task_id,
            "program_id": program_id,
            "subject_code": subject_code,
            "chapter_id": chapter_id,
            "lesson_id": lesson_id,
            "item_type": item_type,
            "title": title,
            "telegram_chat_id": str(chat_id),
            "telegram_message_id": int(message_id or 0),
        }
        try:
            hdrs = dict(self.headers)
            hdrs["Prefer"] = "resolution=merge-duplicates"
            requests.post(
                f"{self.url}/rest/v1/ai_agent_uploaded_items",
                headers=hdrs,
                json=payload,
                timeout=12,
            )
        except Exception as exc:
            log(f"record_uploaded_item warning: {exc}")

    def notify_task_completion(self, title: str, message: str, invite_link: str = "") -> None:
        if not self.enabled:
            return
        try:
            rpc_payload = {
                "p_title": title,
                "p_message": message,
                "p_event_type": "AI_AGENT_TASK_COMPLETED",
                "p_extra_data": {
                    "notification_type": "AI_AGENT_TASK_COMPLETED",
                    "event_type": "AI_AGENT_TASK_COMPLETED",
                    "type": "AI_AGENT_TASK_COMPLETED",
                    "deeplink": invite_link or "ai_agent",
                    "url": invite_link or "",
                },
            }
            requests.post(
                f"{self.url}/rest/v1/rpc/notify_admin_via_onesignal",
                headers=self.headers,
                json=rpc_payload,
                timeout=15,
            )
        except Exception as exc:
            log(f"notify_task_completion warning: {exc}")


from concurrent.futures import ThreadPoolExecutor, as_completed
import shutil
import urllib.parse


class ShikhoClient:
    """
    Mirrors `ShikhoApiService.kt` and `CourseRepository.kt` 100% accurately so that every
    recorded/live class `playback_url`, Animated Lesson `playback_url`, and `study_materials` PDF
    is resolved without GraphQL validation errors.
    """

    def __init__(self, access_token: str):
        self.access_token = access_token.strip()
        self.session = requests.Session()
        self.session.headers.update(
            {
                "Content-Type": "application/json",
                "Accept": "application/json",
                "User-Agent": SHIKHO_APP_USER_AGENT,
                "Build-Version": "(607) 6.0.7",
                "X-User-Timezone": "Asia/Dhaka",
            }
        )
        if self.access_token:
            self.session.headers["Authorization"] = f"Bearer {self.access_token}"

    def fetch_lessons_for_program(
        self, program_id: str, phase_id: str = "", subject_code: str = "", chapter_id: str = ""
    ) -> List[Dict[str, Any]]:
        all_items: List[Dict[str, Any]] = []
        seen_ids = set()

        def add_rows(rows: Any) -> None:
            if not isinstance(rows, list):
                return
            for item in rows:
                if isinstance(item, dict):
                    iid = str(item.get("id") or item.get("content_id") or "").strip()
                    if iid and iid not in seen_ids:
                        seen_ids.add(iid)
                        all_items.append(item)

        lesson_fields = """
          id
          title
          content_id
          content_type
          access_level
          start_time
          end_time
          subject_id
          subject_name
          batch_id
          chapter_id
          icon
          color_code
          user_activity_state
          live_class {
            id
            playback_url
            start_time
            end_time
          }
        """

        # 1. Phase-wise query (exact schema from CourseRepository.kt)
        if phase_id and program_id:
            q_phase = f"""
            query GetUpcomingLessonsPhaseWise($program_id: String!, $phase_id: String!) {{
              studentSpecificLessons(program_id: $program_id, phase_id: $phase_id) {{
                data {{
                  {lesson_fields}
                }}
              }}
            }}
            """
            try:
                r = self.session.post(
                    SHIKHO_GRAPHQL_URL,
                    json={
                        "operationName": "GetUpcomingLessonsPhaseWise",
                        "query": q_phase,
                        "variables": {"program_id": program_id, "phase_id": phase_id},
                    },
                    timeout=35,
                )
                if r.ok:
                    add_rows((((r.json().get("data") or {}).get("studentSpecificLessons") or {}).get("data")))
            except Exception as exc:
                log(f"GetUpcomingLessonsPhaseWise note: {exc}")

            # 1b. Wide date-window phase query to include all past recorded classes
            q_phase_date = f"""
            query GetStudentLessonsPhaseDate($program_id: String!, $phase_id: String!, $start_date: String, $end_date: String) {{
              studentSpecificLessons(program_id: $program_id, phase_id: $phase_id, start_date: $start_date, end_date: $end_date) {{
                data {{
                  {lesson_fields}
                }}
              }}
            }}
            """
            try:
                r = self.session.post(
                    SHIKHO_GRAPHQL_URL,
                    json={
                        "operationName": "GetStudentLessonsPhaseDate",
                        "query": q_phase_date,
                        "variables": {
                            "program_id": program_id,
                            "phase_id": phase_id,
                            "start_date": "2020-01-01",
                            "end_date": "2030-12-31",
                        },
                    },
                    timeout=35,
                )
                if r.ok:
                    add_rows((((r.json().get("data") or {}).get("studentSpecificLessons") or {}).get("data")))
            except Exception as exc:
                log(f"GetStudentLessonsPhaseDate note: {exc}")

        # 2. Program-wide query without phase_id
        if program_id:
            q_all = f"""
            query GetUpcomingLessons($program_id: String!) {{
              studentSpecificLessons(program_id: $program_id) {{
                data {{
                  {lesson_fields}
                }}
              }}
            }}
            """
            try:
                r = self.session.post(
                    SHIKHO_GRAPHQL_URL,
                    json={
                        "operationName": "GetUpcomingLessons",
                        "query": q_all,
                        "variables": {"program_id": program_id},
                    },
                    timeout=35,
                )
                if r.ok:
                    add_rows((((r.json().get("data") or {}).get("studentSpecificLessons") or {}).get("data")))
            except Exception as exc:
                log(f"GetUpcomingLessons note: {exc}")

            q_date = f"""
            query GetStudentLessonsDateOnly($program_id: String!, $start_date: String, $end_date: String) {{
              studentSpecificLessons(program_id: $program_id, start_date: $start_date, end_date: $end_date) {{
                data {{
                  {lesson_fields}
                }}
              }}
            }}
            """
            try:
                r = self.session.post(
                    SHIKHO_GRAPHQL_URL,
                    json={
                        "operationName": "GetStudentLessonsDateOnly",
                        "query": q_date,
                        "variables": {
                            "program_id": program_id,
                            "start_date": "2020-01-01",
                            "end_date": "2030-12-31",
                        },
                    },
                    timeout=35,
                )
                if r.ok:
                    add_rows((((r.json().get("data") or {}).get("studentSpecificLessons") or {}).get("data")))
            except Exception as exc:
                log(f"GetStudentLessonsDateOnly note: {exc}")

        # 3. Filter by non-exam, subject, and chapter
        filtered: List[Dict[str, Any]] = []
        subj_q = subject_code.strip().lower()
        chap_q = chapter_id.strip().lower()
        now_ms = int(time.time() * 1000)

        for item in all_items:
            ctype = str(item.get("content_type") or "").lower()
            if "exam" in ctype or "modeltest" in ctype or "quiz" in ctype or item.get("model_test"):
                continue
            # Skip strictly future upcoming classes that haven't started yet unless they already have a playback_url
            live_obj = item.get("live_class") if isinstance(item.get("live_class"), dict) else {}
            has_pb = bool(str(live_obj.get("playback_url") or item.get("video_url") or "").strip())
            start_raw = str(item.get("start_time") or live_obj.get("start_time") or "").strip()
            if start_raw.isdigit() and int(start_raw) > now_ms + 3600_000 and not has_pb:
                continue

            if subj_q:
                s_code = str(item.get("subject_code") or "").lower()
                s_id = str(item.get("subject_id") or "").lower()
                s_name = str(item.get("subject_name") or "").lower()
                if subj_q not in (s_code, s_id) and subj_q not in s_name:
                    continue
            if chap_q:
                c_id = str(item.get("chapter_id") or "").lower()
                c_name = str(item.get("chapter_name") or "").lower()
                if chap_q != c_id and chap_q not in c_name:
                    continue
            filtered.append(item)

        # 4. If a chapter_id was requested and no live classes matched, also check Animated Lessons (GetTopics)
        if chap_q and not filtered:
            filtered.extend(self.fetch_animated_topics_for_chapter(chapter_id))

        return filtered

    def fetch_animated_topics_for_chapter(self, chapter_id: str) -> List[Dict[str, Any]]:
        if not chapter_id:
            return []
        q_topics = """
        query GetTopics($chapter_id: ID!) {
          topics(chapter_id: $chapter_id) {
            id
            name
            no
            description
            videos {
              data {
                id
                playback_url
              }
            }
          }
        }
        """
        out: List[Dict[str, Any]] = []
        try:
            r = self.session.post(
                SHIKHO_GRAPHQL_URL,
                json={
                    "operationName": "GetTopics",
                    "query": q_topics,
                    "variables": {"chapter_id": chapter_id},
                },
                timeout=25,
            )
            if r.ok:
                topics = (r.json().get("data") or {}).get("topics") or []
                for idx, t in enumerate(topics, start=1):
                    if not isinstance(t, dict):
                        continue
                    v_list = ((t.get("videos") or {}).get("data")) or []
                    pb = ""
                    for v in v_list:
                        if isinstance(v, dict) and str(v.get("playback_url") or "").strip():
                            pb = str(v.get("playback_url")).strip()
                            break
                    if pb:
                        out.append(
                            {
                                "id": str(t.get("id") or f"topic_{idx}"),
                                "title": str(t.get("name") or f"অ্যানিমেটেড লেসন {idx}"),
                                "content_type": "AnimatedLesson",
                                "chapter_id": chapter_id,
                                "video_url": pb,
                            }
                        )
        except Exception as exc:
            log(f"fetch_animated_topics_for_chapter note: {exc}")
        return out

    def resolve_stream_and_slide(self, lesson: Dict[str, Any]) -> Tuple[str, List[Dict[str, str]], str]:
        """
        Resolves the HLS/MP4 `playback_url`, `study_materials` PDFs, and `teacher` name for a lesson.
        Mirrors the exact 4-stage fallback from `CourseRepository.getLiveClassDetails` in Android.
        """
        live_obj = lesson.get("live_class") if isinstance(lesson.get("live_class"), dict) else {}
        video_url = (
            str(lesson.get("video_url") or "")
            or str(live_obj.get("playback_url") or "")
            or str(live_obj.get("recording_url") or "")
            or str(lesson.get("playback_url") or "")
            or str(lesson.get("stream_url") or "")
        ).strip()
        if video_url == "null":
            video_url = ""

        slides: List[Dict[str, str]] = []
        primary_slide = (
            str(lesson.get("slide_url") or "")
            or str(live_obj.get("slide_url") or "")
            or str(lesson.get("pdf_url") or "")
        ).strip()
        if primary_slide and primary_slide != "null":
            slides.append({"title": f"{lesson.get('title', 'লেকচার')} - স্লাইড", "url": primary_slide})

        for att in (lesson.get("slides") or lesson.get("attachments") or []):
            if isinstance(att, dict):
                u = str(att.get("url") or att.get("file_url") or att.get("download_url") or "").strip()
                if u and u != "null" and all(s["url"] != u for s in slides):
                    slides.append(
                        {
                            "title": str(att.get("title") or att.get("name") or "লেকচার স্লাইড"),
                            "url": u,
                        }
                    )

        teacher_name = str(lesson.get("teacher_name") or "").strip()
        if not teacher_name and isinstance(live_obj.get("teacher"), dict):
            teacher_name = str(live_obj["teacher"].get("name") or "").strip()

        candidate_ids: List[str] = []
        raw_candidates = list(lesson.get("candidate_ids") or []) + [
            lesson.get("content_id"),
            lesson.get("live_class_id"),
            live_obj.get("id"),
            lesson.get("id"),
            lesson.get("session_id"),
        ]
        for raw_cid in raw_candidates:
            cid = str(raw_cid or "").strip()
            if cid and cid != "null" and cid not in candidate_ids:
                candidate_ids.append(cid)

        q_stage1 = (
            "GetAcademicLiveClassDetails",
            """
            query GetAcademicLiveClassDetails($id: String!) {
              academicProgramLiveClass(id: $id) {
                id
                title
                playback_url
                start_time
                end_time
                on_going
                study_materials {
                  file_url
                  id
                  name
                }
                subject {
                  code
                  display_bn
                }
                chapter {
                  id
                  name
                  no
                }
                teacher {
                  id
                  name
                }
              }
            }
            """,
        )
        q_stage2 = (
            "GetAcademicLiveClassMinimal",
            """
            query GetAcademicLiveClassMinimal($id: String!) {
              academicProgramLiveClass(id: $id) {
                id
                title
                playback_url
                start_time
                end_time
                study_materials {
                  file_url
                  id
                  name
                }
              }
            }
            """,
        )
        q_stage3 = (
            "GetAcademicLiveClassUltraMinimal",
            """
            query GetAcademicLiveClassUltraMinimal($id: String!) {
              academicProgramLiveClass(id: $id) {
                id
                title
                playback_url
              }
            }
            """,
        )
        q_stage4 = (
            "GetAcademicLiveClassByIdType",
            """
            query GetAcademicLiveClassByIdType($id: ID!) {
              academicProgramLiveClass(id: $id) {
                id
                title
                playback_url
                study_materials {
                  file_url
                  id
                  name
                }
              }
            }
            """,
        )

        if candidate_ids and (not video_url or len(slides) == 0):
            for cid in candidate_ids:
                for op_name, query_str in (q_stage1, q_stage2, q_stage3, q_stage4):
                    try:
                        payload = {
                            "operationName": op_name,
                            "query": query_str,
                            "variables": {"id": cid},
                        }
                        r = self.session.post(SHIKHO_GRAPHQL_URL, json=payload, timeout=22)
                        if not r.ok:
                            continue
                        res = r.json()
                        detail = (res.get("data") or {}).get("academicProgramLiveClass")
                        if isinstance(detail, dict):
                            pb = str(detail.get("playback_url") or "").strip()
                            if pb and pb != "null" and not video_url:
                                video_url = pb

                            t_obj = detail.get("teacher")
                            if isinstance(t_obj, dict) and t_obj.get("name") and not teacher_name:
                                teacher_name = str(t_obj.get("name") or "").strip()

                            subj_obj = detail.get("subject")
                            if isinstance(subj_obj, dict) and subj_obj.get("display_bn") and not lesson.get("subject_name"):
                                lesson["subject_name"] = str(subj_obj.get("display_bn") or "").strip()

                            chap_obj = detail.get("chapter")
                            if isinstance(chap_obj, dict) and chap_obj.get("name") and not lesson.get("chapter_name"):
                                lesson["chapter_name"] = str(chap_obj.get("name") or "").strip()

                            for mat in detail.get("study_materials") or []:
                                if isinstance(mat, dict):
                                    u = str(mat.get("file_url") or mat.get("url") or "").strip()
                                    if u and u != "null" and all(s["url"] != u for s in slides):
                                        slides.append(
                                            {
                                                "title": str(mat.get("name") or f"{lesson.get('title', 'লেকচার')} - স্লাইড"),
                                                "url": u,
                                            }
                                        )
                            if video_url or slides:
                                break
                    except Exception as exc:
                        log(f"{op_name} note for {cid}: {exc}")
                if video_url or slides:
                    break

        return video_url, slides, (teacher_name or "শিক্ষক")


def sanitize_filename(name: str) -> str:
    cleaned = re.sub(r'[\\/*?:"<>|]+', "_", name).strip()
    return cleaned[:100] or "lecture"


def resolve_candidate_video_urls(base_url: str, quality: str = "720p") -> List[str]:
    clean = base_url.strip()
    if not clean:
        return []
    urls = [clean]
    q_folder = "720p"
    if "1080" in quality:
        q_folder = "1080p"
    elif "480" in quality:
        q_folder = "480p"
    elif "360" in quality:
        q_folder = "360p"

    if "/playlist.m3u8" in clean:
        rewritten = clean.replace("/playlist.m3u8", f"/{q_folder}/media.m3u8")
        if rewritten not in urls:
            urls.append(rewritten)
        if q_folder != "720p":
            fb720 = clean.replace("/playlist.m3u8", "/720p/media.m3u8")
            if fb720 not in urls:
                urls.append(fb720)
        fb480 = clean.replace("/playlist.m3u8", "/480p/media.m3u8")
        if fb480 not in urls:
            urls.append(fb480)
    return urls


def _resolve_relative_url(base_url: str, relative_path: str) -> str:
    rel = relative_path.strip()
    if rel.startswith("http://") or rel.startswith("https://"):
        return rel
    return urllib.parse.urljoin(base_url, rel)


def _fetch_text_with_shikho_headers(url: str, timeout: int = 25) -> str:
    headers = {
        "User-Agent": SHIKHO_CDN_USER_AGENT,
        "Referer": SHIKHO_REFERER,
        "Origin": SHIKHO_ORIGIN,
        "Accept": "*/*",
    }
    r = requests.get(url, headers=headers, timeout=timeout)
    r.raise_for_status()
    return r.text


def _download_binary_with_retries(url: str, dest: Path, retries: int = 3) -> bool:
    headers = {
        "User-Agent": SHIKHO_CDN_USER_AGENT,
        "Referer": SHIKHO_REFERER,
        "Origin": SHIKHO_ORIGIN,
        "Accept": "*/*",
    }
    for attempt in range(retries):
        try:
            with requests.get(url, headers=headers, stream=True, timeout=40) as r:
                r.raise_for_status()
                with open(dest, "wb") as f:
                    for chunk in r.iter_content(chunk_size=65536):
                        if chunk:
                            f.write(chunk)
            if dest.exists() and dest.stat().st_size > 0:
                return True
        except Exception:
            if attempt < retries - 1:
                time.sleep(0.6 * (attempt + 1))
    return False


def _download_media_playlist_segments(playlist_url: str, playlist_text: str, seg_dir: Path, prefix: str) -> Optional[Path]:
    """
    Downloads all segments + #EXT-X-MAP + #EXT-X-KEY from a media .m3u8 playlist in parallel
    (20 threads) and writes a local .m3u8 file pointing to the downloaded local files.
    Exact port of `AppFileDownloadManager.kt` so Shikho CDN streams never fail.
    """
    seg_dir.mkdir(parents=True, exist_ok=True)
    lines = playlist_text.splitlines()
    download_jobs: List[Tuple[str, Path]] = []
    rewritten_lines: List[str] = []
    seg_index = 0
    uri_attr_regex = re.compile(r'URI="([^"]+)"')

    for raw_line in lines:
        line = raw_line.strip()
        if not line:
            continue
        if line.startswith("#"):
            if line.startswith("#EXT-X-KEY") or line.startswith("#EXT-X-MAP"):
                m = uri_attr_regex.search(line)
                if m and m.group(1):
                    orig_uri = m.group(1)
                    abs_uri = _resolve_relative_url(playlist_url, orig_uri)
                    ext = "key" if line.startswith("#EXT-X-KEY") else "mp4"
                    local_name = f"{prefix}_init_{seg_index}.{ext}"
                    seg_index += 1
                    local_file = seg_dir / local_name
                    download_jobs.append((abs_uri, local_file))
                    rewritten_lines.append(line.replace(f'URI="{orig_uri}"', f'URI="{local_file.name}"'))
                else:
                    rewritten_lines.append(line)
            else:
                rewritten_lines.append(line)
        else:
            abs_seg_url = _resolve_relative_url(playlist_url, line)
            clean_path = abs_seg_url.split("?")[0].lower()
            ext = "m4s" if clean_path.endswith(".m4s") else ("aac" if clean_path.endswith(".aac") else "ts")
            local_name = f"{prefix}_seg_{seg_index:05d}.{ext}"
            seg_index += 1
            local_file = seg_dir / local_name
            download_jobs.append((abs_seg_url, local_file))
            rewritten_lines.append(local_file.name)

    if not download_jobs:
        return None

    log(f"⬇️ [Native HLS] Downloading {len(download_jobs)} '{prefix}' segments with 20 parallel workers...")
    failed_count = 0
    with ThreadPoolExecutor(max_workers=20) as pool:
        futures = {pool.submit(_download_binary_with_retries, url, dst): (url, dst) for url, dst in download_jobs}
        for fut in as_completed(futures):
            ok = False
            try:
                ok = fut.result()
            except Exception:
                ok = False
            if not ok:
                failed_count += 1

    if failed_count > max(3, len(download_jobs) // 10):
        log(f"⚠️ Too many failed segments ({failed_count}/{len(download_jobs)}) for {prefix}")
        return None

    local_m3u8 = seg_dir / f"{prefix}_local.m3u8"
    local_m3u8.write_text("\n".join(rewritten_lines) + "\n", encoding="utf-8")
    return local_m3u8


def download_shikho_hls_native(video_url: str, output_path: Path, quality: str = "720p") -> bool:
    """
    Mirrors `AppFileDownloadManager.kt`'s exact HLS & MP4 download engine:
    - Handles direct .mp4 files
    - Handles master .m3u8 playlists with separate #EXT-X-MEDIA:TYPE=AUDIO tracks
    - Downloads segments concurrently with Shikho headers and remuxes into a faststart .mp4 via ffmpeg.
    """
    clean_url = video_url.strip()
    if not clean_url:
        return False

    # Direct MP4 download if not HLS
    if ".m3u8" not in clean_url.lower():
        log(f"⬇️ [Direct MP4] Downloading {clean_url[:90]}...")
        return _download_binary_with_retries(clean_url, output_path, retries=3) and output_path.stat().st_size > 20_000

    temp_dir = WORK_DIR / f"hls_tmp_{int(time.time() * 1000)}"
    temp_dir.mkdir(parents=True, exist_ok=True)
    try:
        master_text = _fetch_text_with_shikho_headers(clean_url)
        video_playlist_url = clean_url
        audio_playlist_url = ""

        target_height = 720
        if "1080" in quality:
            target_height = 1080
        elif "480" in quality:
            target_height = 480
        elif "360" in quality:
            target_height = 360

        if "#EXT-X-STREAM-INF" in master_text:
            lines = [l.strip() for l in master_text.splitlines() if l.strip()]
            audio_groups: Dict[str, str] = {}
            first_audio_uri = ""
            uri_re = re.compile(r'URI="([^"]+)"')
            grp_re = re.compile(r'GROUP-ID="([^"]+)"')
            res_re = re.compile(r"RESOLUTION=(\d+)x(\d+)")

            for line in lines:
                if line.startswith("#EXT-X-MEDIA") and "TYPE=AUDIO" in line:
                    u_m = uri_re.search(line)
                    g_m = grp_re.search(line)
                    if u_m:
                        resolved_a = _resolve_relative_url(clean_url, u_m.group(1))
                        if not first_audio_uri:
                            first_audio_uri = resolved_a
                        if g_m:
                            audio_groups[g_m.group(1)] = resolved_a

            variants: List[Tuple[int, str, str]] = []
            i = 0
            while i < len(lines):
                line = lines[i]
                if line.startswith("#EXT-X-STREAM-INF"):
                    r_m = res_re.search(line)
                    h = int(r_m.group(2)) if r_m else 720
                    a_grp_m = re.search(r'AUDIO="([^"]+)"', line)
                    a_grp = a_grp_m.group(1) if a_grp_m else ""
                    j = i + 1
                    while j < len(lines) and lines[j].startswith("#"):
                        j += 1
                    if j < len(lines):
                        v_uri = _resolve_relative_url(clean_url, lines[j])
                        variants.append((h, v_uri, a_grp))
                    i = j
                i += 1

            if variants:
                # Pick variant closest to target_height
                variants.sort(key=lambda item: abs(item[0] - target_height))
                chosen_h, video_playlist_url, chosen_agrp = variants[0]
                audio_playlist_url = audio_groups.get(chosen_agrp) or first_audio_uri
                log(f"🎯 Selected HLS variant {chosen_h}p (separate audio: {'YES' if audio_playlist_url else 'NO'})")

        video_m3u8_text = (
            master_text if video_playlist_url == clean_url else _fetch_text_with_shikho_headers(video_playlist_url)
        )
        local_video_m3u8 = _download_media_playlist_segments(video_playlist_url, video_m3u8_text, temp_dir, "vid")
        if not local_video_m3u8:
            return False

        local_audio_m3u8: Optional[Path] = None
        if audio_playlist_url:
            try:
                audio_m3u8_text = _fetch_text_with_shikho_headers(audio_playlist_url)
                local_audio_m3u8 = _download_media_playlist_segments(
                    audio_playlist_url, audio_m3u8_text, temp_dir, "aud"
                )
            except Exception as a_exc:
                log(f"Audio playlist download note: {a_exc}")

        if output_path.exists():
            output_path.unlink()

        ffmpeg_cmd = [
            "ffmpeg",
            "-y",
            "-allowed_extensions",
            "ALL",
            "-protocol_whitelist",
            "file,http,https,tcp,tls,crypto,data",
            "-i",
            str(local_video_m3u8),
        ]
        if local_audio_m3u8 and local_audio_m3u8.exists():
            ffmpeg_cmd.extend(
                [
                    "-allowed_extensions",
                    "ALL",
                    "-protocol_whitelist",
                    "file,http,https,tcp,tls,crypto,data",
                    "-i",
                    str(local_audio_m3u8),
                    "-map",
                    "0:v:0",
                    "-map",
                    "1:a:0",
                ]
            )
        ffmpeg_cmd.extend(
            [
                "-c",
                "copy",
                "-bsf:a",
                "aac_adtstoasc",
                "-movflags",
                "+faststart",
                str(output_path),
            ]
        )
        res = subprocess.run(ffmpeg_cmd, cwd=str(temp_dir), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if res.returncode == 0 and output_path.exists() and output_path.stat().st_size > 20_000:
            size_mb = output_path.stat().st_size / (1024 * 1024)
            log(f"✅ Video downloaded & remuxed via Native Shikho HLS Engine ({size_mb:.2f} MB)")
            return True
        else:
            log(f"ℹ️ Native HLS ffmpeg mux note: {(res.stdout or '')[-350:].strip()}")
            return False
    except Exception as exc:
        log(f"ℹ️ Native HLS engine note: {exc}")
        return False
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)


def download_video_file(video_url: str, output_path: Path, quality: str = "720p") -> bool:
    if output_path.exists():
        output_path.unlink()

    # 1. Primary: Native Shikho HLS / MP4 Downloader (exact port of AppFileDownloadManager.kt)
    if download_shikho_hls_native(video_url, output_path, quality):
        return True

    # 2. Secondary: yt-dlp with Shikho headers
    height = "720"
    if "1080" in quality:
        height = "1080"
    elif "480" in quality:
        height = "480"
    elif "360" in quality:
        height = "360"

    candidate_urls = resolve_candidate_video_urls(video_url, quality)
    format_selector = f"bestvideo[height<={height}]+bestaudio/best[height<={height}]/best"

    for candidate in candidate_urls:
        if output_path.exists():
            output_path.unlink()

        cmd = [
            "yt-dlp",
            "--no-part",
            "--no-playlist",
            "--no-check-certificates",
            "--concurrent-fragments",
            "10",
            "--user-agent",
            SHIKHO_CDN_USER_AGENT,
            "--add-header",
            f"Referer:{SHIKHO_REFERER}",
            "--add-header",
            f"Origin:{SHIKHO_ORIGIN}",
            "-f",
            format_selector,
            "--merge-output-format",
            "mp4",
            "-o",
            str(output_path),
            candidate,
        ]
        log(f"⬇️ [yt-dlp] Downloading video ({quality}): {candidate[:95]}...")
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if res.returncode == 0 and output_path.exists() and output_path.stat().st_size > 20_000:
            size_mb = output_path.stat().st_size / (1024 * 1024)
            log(f"✅ Video downloaded via yt-dlp ({size_mb:.2f} MB)")
            return True
        else:
            tail = (res.stdout or "")[-350:].strip()
            log(f"ℹ️ yt-dlp attempt note: {tail}")

        # 3. Fallback: direct ffmpeg HLS/MP4 remux with Shikho CDN headers
        if output_path.exists():
            output_path.unlink()
        ffmpeg_headers = f"Referer: {SHIKHO_REFERER}\r\nOrigin: {SHIKHO_ORIGIN}\r\n"
        ffmpeg_cmd = [
            "ffmpeg",
            "-y",
            "-user_agent",
            SHIKHO_CDN_USER_AGENT,
            "-headers",
            ffmpeg_headers,
            "-i",
            candidate,
            "-c",
            "copy",
            "-bsf:a",
            "aac_adtstoasc",
            "-movflags",
            "+faststart",
            str(output_path),
        ]
        log(f"⬇️ [ffmpeg] Downloading HLS/MP4 stream: {candidate[:95]}...")
        res2 = subprocess.run(ffmpeg_cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if res2.returncode == 0 and output_path.exists() and output_path.stat().st_size > 20_000:
            size_mb = output_path.stat().st_size / (1024 * 1024)
            log(f"✅ Video downloaded via ffmpeg ({size_mb:.2f} MB)")
            return True
        else:
            tail2 = (res2.stdout or "")[-350:].strip()
            log(f"⚠️ ffmpeg attempt note: {tail2}")

    return False


def get_video_metadata(video_path: Path) -> Tuple[int, int, int]:
    """
    Extracts (duration_sec, width, height) using ffprobe so Telegram renders the video
    as a proper streamable player with accurate duration & aspect ratio.
    """
    try:
        cmd = [
            "ffprobe",
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "stream=width,height,duration:format=duration",
            "-of",
            "json",
            str(video_path),
        ]
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=15)
        if res.returncode == 0 and res.stdout:
            info = json.loads(res.stdout)
            streams = info.get("streams") or []
            fmt = info.get("format") or {}
            width = int((streams[0].get("width") if streams else 0) or 1280)
            height = int((streams[0].get("height") if streams else 0) or 720)
            dur_raw = (streams[0].get("duration") if streams else None) or fmt.get("duration") or 0
            duration = max(1, int(float(dur_raw)))
            return duration, width, height
    except Exception:
        pass
    return 0, 1280, 720


def download_pdf_file(pdf_url: str, output_path: Path) -> bool:
    if output_path.exists():
        output_path.unlink()
    headers = {
        "User-Agent": SHIKHO_CDN_USER_AGENT,
        "Referer": SHIKHO_REFERER,
        "Origin": SHIKHO_ORIGIN,
    }
    try:
        with requests.get(pdf_url, headers=headers, stream=True, timeout=60) as r:
            r.raise_for_status()
            with open(output_path, "wb") as f:
                for chunk in r.iter_content(chunk_size=65536):
                    if chunk:
                        f.write(chunk)
        return output_path.exists() and output_path.stat().st_size > 100
    except Exception as exc:
        log(f"⚠️ Failed to download slide PDF ({pdf_url[:80]}): {exc}")
        return False


async def fetch_and_sync_telegram_dialogs(
    client: TelegramClient,
    gh_bridge: GitHubAuthBridge,
) -> List[Dict[str, Any]]:
    """
    Fetches all Telegram Channels and Groups (including Forum Topics inside Supergroups)
    from the user's connected Telegram account and saves them to `scripts/agent_auth_state.json`
    so the Android app can display a real Channel / Group / Topic picker sheet!
    """
    dialogs_data: List[Dict[str, Any]] = []
    try:
        dialogs = await client.get_dialogs(limit=120)
        for d in dialogs:
            if not (d.is_channel or d.is_group):
                continue
            entity = d.entity
            title = str(getattr(d, "name", "") or getattr(entity, "title", "") or "").strip()
            if not title:
                continue

            is_megagroup = bool(getattr(entity, "megagroup", False))
            is_forum = bool(getattr(entity, "forum", False))
            is_broadcast = bool(getattr(entity, "broadcast", False))
            creator = bool(getattr(entity, "creator", False))
            admin_rights = getattr(entity, "admin_rights", None)

            # For broadcast channels, only include channels where the user is creator or has post_messages admin rights
            if is_broadcast and not creator:
                if not admin_rights or not getattr(admin_rights, "post_messages", False):
                    continue

            raw_id = getattr(entity, "id", 0)
            if not raw_id:
                continue
            if d.is_channel or is_megagroup:
                peer_id = f"-100{raw_id}" if not str(raw_id).startswith("-100") else str(raw_id)
            else:
                peer_id = f"-{raw_id}" if not str(raw_id).startswith("-") else str(raw_id)

            topics_list: List[Dict[str, Any]] = []
            if is_forum:
                try:
                    topics_res = await client(
                        functions.channels.GetForumTopicsRequest(
                            channel=entity,
                            offset_date=None,
                            offset_id=0,
                            offset_topic=0,
                            limit=100,
                        )
                    )
                    for t in getattr(topics_res, "topics", []) or []:
                        t_id = getattr(t, "id", None)
                        t_title = str(getattr(t, "title", "") or "").strip()
                        if t_id and t_title:
                            topics_list.append({"id": int(t_id), "title": t_title})
                except Exception as t_exc:
                    log(f"Forum topics fetch note for '{title}': {t_exc}")

            dialogs_data.append(
                {
                    "id": peer_id,
                    "title": title,
                    "username": str(getattr(entity, "username", "") or ""),
                    "is_channel": is_broadcast and not is_megagroup,
                    "is_megagroup": is_megagroup or d.is_group,
                    "is_forum": is_forum,
                    "can_post": True,
                    "topics": topics_list,
                }
            )

        gh_bridge.write_state({"telegram_dialogs": dialogs_data})
        log(f"✅ Synced {len(dialogs_data)} Telegram Channels/Groups (with Forum Topics) to cloud state.")
    except Exception as exc:
        log(f"⚠️ fetch_and_sync_telegram_dialogs warning: {exc}")
    return dialogs_data


def trigger_self_continuation(
    task_id: str,
    payload_b64: str,
    agent_cfg: Optional[Dict[str, Any]] = None,
) -> bool:
    cfg = agent_cfg or {}
    gh_token = (
        str(cfg.get("github_pat") or "").strip()
        or os.environ.get("GITHUB_TOKEN", "").strip()
    )
    owner = str(cfg.get("github_owner") or "").strip()
    repo_name = str(cfg.get("github_repo") or "").strip()
    repo = f"{owner}/{repo_name}" if (owner and repo_name) else os.environ.get("GITHUB_REPOSITORY", "").strip()
    branch = str(cfg.get("github_branch") or "main").strip() or "main"
    if not gh_token or not repo:
        return False
    try:
        url = f"https://api.github.com/repos/{repo}/actions/workflows/maxbird-ai-agent.yml/dispatches"
        headers = {
            "Authorization": f"Bearer {gh_token}",
            "Accept": "application/vnd.github+json",
        }
        body = {
            "ref": branch,
            "inputs": {
                "task_id": task_id,
                "task_payload_b64": payload_b64,
            },
        }
        r = requests.post(url, headers=headers, json=body, timeout=20)
        log(f"🔄 Self-chained next GitHub Actions workflow run (HTTP {r.status_code})")
        return r.status_code in (200, 201, 204)
    except Exception as exc:
        log(f"⚠️ Failed to self-chain workflow: {exc}")
        return False


async def complete_sign_in_and_persist(
    client: TelegramClient,
    device_hash: str,
    api_id: int,
    api_hash: str,
    phone: str,
    otp_code: str,
    two_fa_password: str,
    phone_code_hash: str,
    gh_bridge: GitHubAuthBridge,
    supabase: SupabaseBridge,
) -> bool:
    try:
        if otp_code:
            await client.sign_in(
                phone=phone,
                code=otp_code,
                phone_code_hash=phone_code_hash,
            )
        elif two_fa_password:
            await client.sign_in(password=two_fa_password)
    except SessionPasswordNeededError:
        if two_fa_password:
            await client.sign_in(password=two_fa_password)
        else:
            saved_partial = client.session.save()
            need_2fa_patch = {
                "device_hash": device_hash,
                "telegram_partial_session": saved_partial,
                "telegram_auth_state": "NEED_2FA",
                "telegram_auth_message": "🔒 আপনার টেলিগ্রামে Two-Step (2FA) পাসওয়ার্ড চালু আছে। 2FA পাসওয়ার্ড বক্সে পাসওয়ার্ড লিখে ভেরিফাই চাপুন।",
            }
            gh_bridge.write_state(need_2fa_patch)
            supabase.update_agent_config(device_hash, need_2fa_patch)
            log("🔒 2FA password required; waiting for 2FA password...")
            return False

    final_string_session = client.session.save()
    me = await client.get_me()
    first_name = getattr(me, "first_name", "") or "Telegram User"
    username = getattr(me, "username", "") or ""
    account_display = f"{first_name} (@{username})" if username else first_name

    try:
        await client.send_message(
            "me",
            f"✅ **MaxBird 24/7 Autonomous AI Agent** আপনার টেলিগ্রাম একাউন্টে ({account_display}) সফলভাবে কানেক্ট হয়েছে!\n"
            f"এখন আপনি যেকোনো অধ্যায় বা ক্লাস থেকে সরাসরি আপনার যেকোনো চ্যানেল, গ্রুপ বা টপিকে ভিডিও ও স্লাইড পাঠাতে পারবেন।",
        )
    except Exception:
        pass

    dialogs_data = await fetch_and_sync_telegram_dialogs(client, gh_bridge)

    success_patch = {
        "device_hash": device_hash,
        "telegram_api_id": str(api_id),
        "telegram_api_hash": api_hash,
        "telegram_phone": phone,
        "telegram_session_string": final_string_session,
        "telegram_account_name": account_display,
        "telegram_auth_state": "CONNECTED",
        "telegram_auth_message": f"✅ টেলিগ্রাম সফলভাবে কানেক্ট হয়েছে! ({account_display})",
        "telegram_partial_session": "",
        "telegram_otp_code": "",
        "telegram_2fa_password": "",
        "telegram_dialogs": dialogs_data,
    }
    gh_bridge.write_state(success_patch)
    supabase.update_agent_config(device_hash, success_patch)
    log(f"✅ Permanent STRING_SESSION generated & saved for {account_display}!")
    return True


async def handle_telegram_auth_flow(
    auth_action: str,
    auth_payload: Dict[str, Any],
    agent_cfg: Dict[str, Any],
    gh_bridge: GitHubAuthBridge,
    supabase: SupabaseBridge,
) -> None:
    gh_state = gh_bridge.read_state()
    device_hash = (
        str(auth_payload.get("device_hash") or "")
        or str(agent_cfg.get("device_hash") or "")
        or str(gh_state.get("device_hash") or "GLOBAL_DEFAULT")
    ).strip()

    api_id_raw = (
        str(auth_payload.get("telegram_api_id") or "")
        or str(agent_cfg.get("telegram_api_id") or "")
        or str(gh_state.get("telegram_api_id") or "")
        or DEFAULT_TELEGRAM_API_ID
    ).strip()
    api_hash = (
        str(auth_payload.get("telegram_api_hash") or "")
        or str(agent_cfg.get("telegram_api_hash") or "")
        or str(gh_state.get("telegram_api_hash") or "")
        or DEFAULT_TELEGRAM_API_HASH
    ).strip()

    if auth_action == "TELEGRAM_FETCH_DIALOGS":
        session_str = unseal_secret(
            (
                str(auth_payload.get("telegram_session_string") or "")
                or str(gh_state.get("telegram_session_string") or "")
                or str(agent_cfg.get("telegram_session_string") or "")
            ).strip()
        )
        if not api_id_raw.isdigit() or not api_hash or not session_str:
            log("⚠️ Cannot fetch dialogs: missing session_string")
            return
        async with TelegramClient(StringSession(session_str), int(api_id_raw), api_hash) as client:
            await fetch_and_sync_telegram_dialogs(client, gh_bridge)
        return

    phone = (
        str(auth_payload.get("telegram_phone") or "")
        or str(agent_cfg.get("telegram_phone") or "")
        or str(gh_state.get("telegram_phone") or "")
    ).strip()
    initial_two_fa = (
        str(auth_payload.get("telegram_2fa_password") or "")
        or str(agent_cfg.get("telegram_2fa_password") or "")
        or str(gh_state.get("telegram_2fa_password") or "")
    ).strip()

    if not api_id_raw.isdigit() or not api_hash or not phone:
        msg = "❌ টেলিগ্রাম ফোন নাম্বার সঠিক নয়। কান্ট্রি কোডসহ নাম্বার দিন (যেমন: +88017XXXXXXXX)।"
        log(msg)
        err_patch = {"device_hash": device_hash, "telegram_auth_state": "ERROR", "telegram_auth_message": msg}
        gh_bridge.write_state(err_patch)
        supabase.update_agent_config(device_hash, err_patch)
        return

    api_id = int(api_id_raw)

    if auth_action == "TELEGRAM_SEND_CODE":
        log(f"📲 Connecting & sending Telegram login code to {phone} (API_ID={api_id})...")
        client = TelegramClient(StringSession(), api_id, api_hash)
        try:
            await client.connect()
            sent = await client.send_code_request(phone)
            partial_session = client.session.save()
            waiting_patch = {
                "device_hash": device_hash,
                "telegram_api_id": str(api_id),
                "telegram_api_hash": api_hash,
                "telegram_phone": phone,
                "telegram_phone_code_hash": sent.phone_code_hash,
                "telegram_partial_session": partial_session,
                "telegram_otp_code": "",
                "telegram_auth_state": "WAITING_FOR_OTP",
                "telegram_auth_message": "✅ আপনার টেলিগ্রাম অ্যাপে ৫ সংখ্যার OTP কোড গেছে! নিচে কোডটি লিখে 'ওটিপি ভেরিফাই ও কানেক্ট করুন' চাপুন।",
            }
            gh_bridge.write_state(waiting_patch)
            supabase.update_agent_config(device_hash, waiting_patch)
            log("✅ OTP sent! Staying connected live on the same socket for up to 240s waiting for user OTP...")

            deadline = time.time() + 240
            signed_in_otp = False
            while time.time() < deadline:
                await asyncio.sleep(2.5)
                latest_gh = gh_bridge.read_state()
                latest_supa = supabase.get_agent_config(device_hash)

                otp_code = (
                    str(latest_gh.get("telegram_otp_code") or "").strip()
                    or str(latest_supa.get("telegram_otp_code") or "").strip()
                )
                two_fa = (
                    str(latest_gh.get("telegram_2fa_password") or "").strip()
                    or str(latest_supa.get("telegram_2fa_password") or "").strip()
                    or initial_two_fa
                )

                if otp_code and not signed_in_otp:
                    log(f"🔐 Received OTP code live ({len(otp_code)} chars)! Signing in on live connection...")
                    done = await complete_sign_in_and_persist(
                        client=client,
                        device_hash=device_hash,
                        api_id=api_id,
                        api_hash=api_hash,
                        phone=phone,
                        otp_code=otp_code,
                        two_fa_password=two_fa,
                        phone_code_hash=sent.phone_code_hash,
                        gh_bridge=gh_bridge,
                        supabase=supabase,
                    )
                    if done:
                        return
                    signed_in_otp = True

                elif signed_in_otp and two_fa:
                    log("🔐 Received 2FA password live! Completing 2FA sign-in...")
                    done = await complete_sign_in_and_persist(
                        client=client,
                        device_hash=device_hash,
                        api_id=api_id,
                        api_hash=api_hash,
                        phone=phone,
                        otp_code="",
                        two_fa_password=two_fa,
                        phone_code_hash=sent.phone_code_hash,
                        gh_bridge=gh_bridge,
                        supabase=supabase,
                    )
                    if done:
                        return

        except Exception as exc:
            err_msg = f"❌ টেলিগ্রাম ভেরিফিকেশন ত্রুটি: {exc}"
            log(err_msg)
            err_patch = {
                "device_hash": device_hash,
                "telegram_auth_state": "ERROR",
                "telegram_auth_message": err_msg,
            }
            gh_bridge.write_state(err_patch)
            supabase.update_agent_config(device_hash, err_patch)
        finally:
            await client.disconnect()

    elif auth_action == "TELEGRAM_SIGN_IN":
        otp_code = (
            str(auth_payload.get("telegram_otp_code") or "")
            or str(gh_state.get("telegram_otp_code") or "")
            or str(agent_cfg.get("telegram_otp_code") or "")
        ).strip()
        two_fa_password = (
            str(auth_payload.get("telegram_2fa_password") or "")
            or str(gh_state.get("telegram_2fa_password") or "")
            or str(agent_cfg.get("telegram_2fa_password") or "")
        ).strip()
        phone_code_hash = (
            str(auth_payload.get("telegram_phone_code_hash") or "")
            or str(gh_state.get("telegram_phone_code_hash") or "")
            or str(agent_cfg.get("telegram_phone_code_hash") or "")
        ).strip()
        partial_session = (
            unseal_secret(str(auth_payload.get("telegram_partial_session") or ""))
            or unseal_secret(str(gh_state.get("telegram_partial_session") or ""))
            or unseal_secret(str(agent_cfg.get("telegram_partial_session") or ""))
        ).strip()

        existing_session = (
            unseal_secret(str(gh_state.get("telegram_session_string") or ""))
            or unseal_secret(str(agent_cfg.get("telegram_session_string") or ""))
        ).strip()
        if existing_session and (
            gh_state.get("telegram_auth_state") == "CONNECTED"
            or agent_cfg.get("telegram_auth_state") == "CONNECTED"
        ):
            log("✅ Session already connected by live worker! Exiting cleanly.")
            return

        if not partial_session or not phone_code_hash:
            err_msg = "❌ ওটিপি সেশনের মেয়াদ শেষ হয়েছে। অনুগ্রহ করে আবার 'ধাপ ১: টেলিগ্রামে OTP কোড পাঠান' চাপুন।"
            log(err_msg)
            err_patch = {"device_hash": device_hash, "telegram_auth_state": "ERROR", "telegram_auth_message": err_msg}
            gh_bridge.write_state(err_patch)
            supabase.update_agent_config(device_hash, err_patch)
            return

        log(f"🔐 Verifying Telegram OTP (fallback run) for {phone}...")
        client = TelegramClient(StringSession(partial_session), api_id, api_hash)
        try:
            await client.connect()
            await complete_sign_in_and_persist(
                client=client,
                device_hash=device_hash,
                api_id=api_id,
                api_hash=api_hash,
                phone=phone,
                otp_code=otp_code,
                two_fa_password=two_fa_password,
                phone_code_hash=phone_code_hash,
                gh_bridge=gh_bridge,
                supabase=supabase,
            )
        except Exception as exc:
            err_msg = f"❌ ওটিপি ভেরিফাই ব্যর্থ: {exc}"
            log(err_msg)
            err_patch = {"device_hash": device_hash, "telegram_auth_state": "ERROR", "telegram_auth_message": err_msg}
            gh_bridge.write_state(err_patch)
            supabase.update_agent_config(device_hash, err_patch)
        finally:
            await client.disconnect()


async def ensure_telegram_destination(
    client: TelegramClient,
    task: Dict[str, Any],
    supabase: SupabaseBridge,
) -> Tuple[Any, str]:
    """
    Ensures the target Telegram Channel or Supergroup exists.
    If the user selected an existing Channel/Group (`target_chat_id` is non-empty and `create_new_channel` is False),
    resolves that exact entity (warming up dialogs cache first if needed).
    Otherwise creates a new Channel or Supergroup on the user's Telegram account.
    """
    existing_chat_id = str(task.get("target_chat_id") or "").strip()
    create_new = bool(task.get("create_new_channel", False))
    existing_invite = str(task.get("telegram_invite_link") or "").strip()
    channel_title = (
        str(task.get("target_channel_title") or "").strip()
        or str(task.get("program_title") or "").strip()
        or "MaxBird Course Archive"
    )
    subject_title = str(task.get("subject_title") or "").strip()
    if subject_title and subject_title not in channel_title and create_new:
        channel_title = f"{channel_title} — {subject_title}"

    if existing_chat_id and not create_new:
        try:
            entity_key: Any = int(existing_chat_id) if existing_chat_id.lstrip("-").isdigit() else existing_chat_id
            try:
                entity = await client.get_entity(entity_key)
            except Exception:
                # Warm up Telethon entity cache by fetching dialogs
                await client.get_dialogs(limit=120)
                entity = await client.get_entity(entity_key)
            if not existing_invite:
                username = getattr(entity, "username", "")
                if username:
                    existing_invite = f"https://t.me/{username}"
            return entity, existing_invite
        except Exception as exc:
            log(f"Existing chat_id {existing_chat_id} lookup note: {exc}. Falling back to channel creation...")

    if existing_chat_id and create_new:
        # Check if task already created its channel in an earlier run
        try:
            entity_key = int(existing_chat_id) if existing_chat_id.lstrip("-").isdigit() else existing_chat_id
            entity = await client.get_entity(entity_key)
            return entity, existing_invite
        except Exception:
            pass

    about_text = (
        f"📚 কোর্স: {task.get('program_title') or channel_title}\n"
        f"🤖 স্বয়ংক্রিয়ভাবে সাজানো ভিডিও লেকচার ও লেকচার স্লাইড আর্কাইভ।"
    )
    use_megagroup = bool(task.get("use_forum_topics", False))

    result = await client(
        functions.channels.CreateChannelRequest(
            title=channel_title[:120],
            about=about_text[:240],
            megagroup=use_megagroup,
            broadcast=not use_megagroup,
        )
    )
    created_chat = result.chats[0]
    peer_id = f"-100{created_chat.id}"

    invite_link = ""
    try:
        invite_res = await client(
            functions.messages.ExportChatInviteRequest(peer=created_chat)
        )
        invite_link = getattr(invite_res, "link", "") or ""
    except Exception as exc:
        log(f"Invite link export note: {exc}")

    header_msg = (
        f"🎓 **{channel_title}**\n"
        f"━━━━━━━━━━━━━━━━━━━━━━\n"
        f"✅ এই চ্যানেলে স্বয়ংক্রিয়ভাবে সকল ক্লাসের **HD ভিডিও** এবং **লেকচার স্লাইড (PDF)** ধারাবাহিকভাবে আপলোড করা হচ্ছে।\n"
        f"📅 শুরু হয়েছে: {datetime.datetime.now().strftime('%d %b %Y')}"
    )
    try:
        sent_header = await client.send_message(created_chat, header_msg)
        await client.pin_message(created_chat, sent_header.id, notify=False)
    except Exception:
        pass

    supabase.update_task(
        str(task.get("id") or ""),
        {
            "target_chat_id": peer_id,
            "telegram_invite_link": invite_link,
            "target_channel_title": channel_title,
        },
    )
    task["target_chat_id"] = peer_id
    task["telegram_invite_link"] = invite_link
    log(f"✅ Created Telegram Channel/Group '{channel_title}' (ID: {peer_id}, Link: {invite_link})")
    return created_chat, invite_link


async def send_with_flood_protection(coro_factory):
    while True:
        try:
            return await coro_factory()
        except FloodWaitError as fw:
            wait_sec = int(fw.seconds) + 5
            log(f"⏳ Telegram FloodWait triggered. Sleeping safely for {wait_sec}s...")
            await asyncio.sleep(wait_sec)


async def execute_task_async(
    task: Dict[str, Any],
    agent_cfg: Dict[str, Any],
    supabase: SupabaseBridge,
    gh_bridge: GitHubAuthBridge,
    payload_b64: str,
) -> None:
    task_id = str(task.get("id") or "manual_task")
    task_type = str(task.get("task_type") or "COURSE_TELEGRAM_UPLOAD").upper()
    program_id = str(task.get("program_id") or "").strip()
    phase_id = str(task.get("phase_id") or "").strip()
    subject_code = str(task.get("subject_code") or "").strip()
    chapter_id = str(task.get("chapter_id") or "").strip()
    video_quality = str(task.get("video_quality") or "720p").strip()
    include_videos = bool(task.get("include_videos", True))
    include_slides = bool(task.get("include_slides", True))
    target_topic_id = int(task.get("target_topic_id") or 0)
    target_topic_title = str(task.get("target_topic_title") or "").strip()

    def sync_task_progress(patch: Dict[str, Any]) -> None:
        supabase.update_task(task_id, patch)
        active_task = {
            "id": task_id,
            "status": patch.get("status") or task.get("status") or "RUNNING",
            "completed_items": patch.get("completed_items", task.get("completed_items", 0)),
            "total_items": patch.get("total_items", task.get("total_items", 0)),
            "current_item_title": patch.get("current_item_title") or task.get("current_item_title") or "",
            "status_message": patch.get("status_message") or task.get("status_message") or "",
            "target_chat_id": patch.get("target_chat_id") or task.get("target_chat_id") or "",
            "telegram_invite_link": patch.get("telegram_invite_link") or task.get("telegram_invite_link") or "",
            "github_run_id": os.environ.get("GITHUB_RUN_ID", ""),
        }
        task.update(active_task)
        gh_bridge.write_state({"active_task": active_task})

    api_id_raw = (
        str(task.get("telegram_api_id") or "")
        or str(agent_cfg.get("telegram_api_id") or "")
        or os.environ.get("TELEGRAM_API_ID", "")
        or DEFAULT_TELEGRAM_API_ID
    ).strip()
    api_hash = unseal_secret(
        (
            str(task.get("telegram_api_hash") or "")
            or str(agent_cfg.get("telegram_api_hash") or "")
            or os.environ.get("TELEGRAM_API_HASH", "")
            or DEFAULT_TELEGRAM_API_HASH
        ).strip()
    )
    session_str = unseal_secret(
        (
            str(task.get("telegram_session_string") or "")
            or str(agent_cfg.get("telegram_session_string") or "")
            or os.environ.get("TELEGRAM_SESSION_STRING", "")
        ).strip()
    )
    shikho_token = unseal_secret(
        (
            str(task.get("shikho_access_token") or "")
            or str(agent_cfg.get("shikho_access_token") or "")
            or os.environ.get("SHIKHO_ACCESS_TOKEN", "")
        ).strip()
    )

    if not api_id_raw.isdigit() or not api_hash or not session_str:
        err = "টেলিগ্রাম SESSION_STRING এখনো তৈরি করা হয়নি। অনুগ্রহ করে অ্যাপের '🔌 একাউন্টস' ট্যাব থেকে আপনার ফোন নাম্বার ও ওটিপি দিয়ে অটোমেটিক কানেক্ট করে নিন।"
        log(f"❌ {err}")
        sync_task_progress({"status": "FAILED", "status_message": err})
        return

    api_id = int(api_id_raw)
    sync_task_progress(
        {
            "status": "RUNNING",
            "github_run_id": os.environ.get("GITHUB_RUN_ID", ""),
            "status_message": "টেলিগ্রাম অ্যাকাউন্ট ও কোর্সের সার্ভারের সাথে সংযোগ স্থাপন করা হচ্ছে...",
        }
    )

    async with TelegramClient(StringSession(session_str), api_id, api_hash) as tg_client:
        me = await tg_client.get_me()
        log(f"🤖 Connected to Personal Telegram Account: {getattr(me, 'first_name', 'User')} (@{getattr(me, 'username', '')})")

        # Keep user's Channels, Groups & Forum Topics synced for the in-app picker
        await fetch_and_sync_telegram_dialogs(tg_client, gh_bridge)

        target_entity, invite_link = await ensure_telegram_destination(tg_client, task, supabase)
        chat_id_str = str(task.get("target_chat_id") or "")
        topic_suffix = f" (টপিক: {target_topic_title})" if target_topic_title else ""
        sync_task_progress(
            {
                "status": "RUNNING",
                "target_chat_id": chat_id_str,
                "telegram_invite_link": invite_link,
                "status_message": f"টেলিগ্রাম গন্তব্য প্রস্তুত{topic_suffix}। লেকচার ও স্লাইড লোড হচ্ছে...",
            }
        )

        if task_type == "CREATE_CHANNEL_ONLY":
            done_msg = f"টেলিগ্রাম চ্যানেল সফলভাবে তৈরি হয়েছে! লিংক: {invite_link or 'প্রাইভেট চ্যানেল'}"
            sync_task_progress(
                {
                    "status": "COMPLETED",
                    "completed_items": 1,
                    "total_items": 1,
                    "status_message": done_msg,
                }
            )
            supabase.notify_task_completion("✅ টেলিগ্রাম চ্যানেল প্রস্তুত!", done_msg, invite_link)
            return

        extra = task.get("extra_payload_json") or {}
        if isinstance(extra, str):
            try:
                extra = json.loads(extra)
            except Exception:
                extra = {}

        bundled_lessons = extra.get("lessons") if isinstance(extra, dict) else None

        # Also check `scripts/active_task_payload.json` written by the Android app prior to dispatch
        if not (isinstance(bundled_lessons, list) and len(bundled_lessons) > 0):
            active_payload_file = Path(__file__).resolve().parent / "active_task_payload.json"
            if active_payload_file.exists():
                try:
                    file_data = json.loads(active_payload_file.read_text(encoding="utf-8"))
                    if isinstance(file_data, dict):
                        file_lessons = file_data.get("lessons") or (file_data.get("extra_payload_json") or {}).get("lessons")
                        if isinstance(file_lessons, list) and len(file_lessons) > 0:
                            bundled_lessons = file_lessons
                            log(f"📂 Loaded {len(bundled_lessons)} pre-resolved lessons from scripts/active_task_payload.json")
                except Exception as exc:
                    log(f"active_task_payload.json read note: {exc}")

        shikho_client = ShikhoClient(shikho_token)

        if isinstance(bundled_lessons, list) and len(bundled_lessons) > 0:
            lessons = bundled_lessons
            log(f"📦 Using {len(lessons)} lessons provided directly in task payload.")
        else:
            lessons = shikho_client.fetch_lessons_for_program(
                program_id=program_id,
                phase_id=phase_id,
                subject_code=subject_code,
                chapter_id=chapter_id,
            )
            log(f"📚 Fetched {len(lessons)} lessons from Shikho GraphQL API for program {program_id}.")

        total_lessons = len(lessons)
        if total_lessons == 0:
            err_msg = "⚠️ এই কোর্সে বা অধ্যায়ে আপলোড করার মতো কোনো রেকর্ড করা ক্লাস পাওয়া যায়নি।"
            log(err_msg)
            sync_task_progress({"status": "FAILED", "status_message": err_msg})
            return

        sync_task_progress(
            {
                "total_items": total_lessons,
                "status_message": f"মোট {total_lessons}টি লেকচার পাওয়া গেছে। ভিডিও ও স্লাইড ডাউনলোড ও আপলোড শুরু হচ্ছে...",
            }
        )

        completed_count = 0
        uploaded_videos_count = 0
        uploaded_slides_count = 0
        skipped_upcoming_count = 0
        reply_to_msg_id = target_topic_id if target_topic_id > 0 else None

        for idx, lesson in enumerate(lessons, start=1):
            if time.time() - START_TIME >= MAX_RUNTIME_SECONDS:
                log("⏱️ Approaching GitHub Actions 6-hour limit. Saving state and triggering continuation run...")
                sync_task_progress(
                    {
                        "status": "RESUMING",
                        "completed_items": completed_count,
                        "status_message": f"৬ ঘণ্টার সেশন রিস্টার্ট হচ্ছে ({completed_count}/{total_lessons} সম্পন্ন)...",
                    }
                )
                trigger_self_continuation(task_id, payload_b64, agent_cfg)
                return

            lesson_id = str(lesson.get("id") or f"lesson_{idx}")
            lesson_title = str(lesson.get("title") or f"লেকচার {idx}").strip()
            video_url, slides, teacher_name = shikho_client.resolve_stream_and_slide(lesson)
            subj_name = str(lesson.get("subject_name") or task.get("subject_title") or "বিষয়").strip()
            chap_name = str(lesson.get("chapter_name") or task.get("chapter_title") or "অধ্যায়").strip()

            log(
                f"🔍 Lesson [{idx}/{total_lessons}] '{lesson_title}' -> "
                f"video_url={'YES' if video_url else 'NONE'}, slides={len(slides)}"
            )

            if not video_url and not slides:
                skipped_upcoming_count += 1
                log(f"⏭️ Skipping [{idx}/{total_lessons}] '{lesson_title}' (Upcoming class / no recording or slide yet)")
                continue

            caption_base = (
                f"🎬 **লেকচার #{idx}: {lesson_title}**\n"
                f"📘 বিষয়: {subj_name}\n"
                f"📂 অধ্যায়: {chap_name}\n"
                f"👨‍🏫 শিক্ষক: {teacher_name}\n"
                f"📚 কোর্স: {task.get('program_title') or ''}"
            ).strip()

            lesson_uploaded_any = False
            dedup_scope = f"{chat_id_str}_topic_{target_topic_id}" if target_topic_id > 0 else chat_id_str

            # 1. Upload Video if enabled
            if include_videos and video_url:
                # For single-lesson or chapter manual sends (total_lessons <= 25), always upload even if previously sent
                already_done = (
                    total_lessons > 25
                    and supabase.is_item_already_uploaded(program_id, lesson_id, "VIDEO", dedup_scope)
                )
                if already_done:
                    lesson_uploaded_any = True
                    uploaded_videos_count += 1
                else:
                    sync_task_progress(
                        {
                            "completed_items": completed_count,
                            "total_items": total_lessons,
                            "current_item_title": f"[{idx}/{total_lessons}] 🎬 ভিডিও ডাউনলোড ও আপলোড: {lesson_title}",
                            "status_message": f"ভিডিও ডাউনলোড ও টেলিগ্রামে আপলোড হচ্ছে ({idx}/{total_lessons}): {lesson_title}",
                        }
                    )
                    video_file = WORK_DIR / f"{sanitize_filename(lesson_title)}_{idx}.mp4"
                    try:
                        if download_video_file(video_url, video_file, video_quality):
                            dur, w, h = get_video_metadata(video_file)
                            attrs = [
                                DocumentAttributeVideo(
                                    duration=dur,
                                    w=w,
                                    h=h,
                                    supports_streaming=True,
                                )
                            ]
                            sent_vid = await send_with_flood_protection(
                                lambda: tg_client.send_file(
                                    target_entity,
                                    file=str(video_file),
                                    caption=caption_base,
                                    supports_streaming=True,
                                    attributes=attrs,
                                    reply_to=reply_to_msg_id,
                                )
                            )
                            supabase.record_uploaded_item(
                                task_id=task_id,
                                program_id=program_id,
                                subject_code=subject_code,
                                chapter_id=chapter_id,
                                lesson_id=lesson_id,
                                item_type="VIDEO",
                                title=lesson_title,
                                chat_id=dedup_scope,
                                message_id=getattr(sent_vid, "id", 0),
                            )
                            uploaded_videos_count += 1
                            lesson_uploaded_any = True
                            log(f"✅ Uploaded video [{idx}/{total_lessons}]: {lesson_title}")
                            await asyncio.sleep(4)
                        else:
                            log(f"⚠️ Could not download video stream for '{lesson_title}' ({video_url[:80]})")
                    except Exception as exc:
                        log(f"⚠️ Video upload warning for {lesson_title}: {exc}")
                    finally:
                        if video_file.exists():
                            video_file.unlink()

            # 2. Upload Lecture Slides (PDFs) if enabled
            if include_slides and slides:
                for s_idx, slide in enumerate(slides, start=1):
                    slide_url = str(slide.get("url") or "").strip()
                    slide_title = str(slide.get("title") or f"{lesson_title} - স্লাইড {s_idx}").strip()
                    slide_key = f"{lesson_id}_slide_{s_idx}"
                    if not slide_url:
                        continue
                    already_slide = (
                        total_lessons > 25
                        and supabase.is_item_already_uploaded(program_id, slide_key, "SLIDE", dedup_scope)
                    )
                    if already_slide:
                        lesson_uploaded_any = True
                        uploaded_slides_count += 1
                        continue

                    sync_task_progress(
                        {
                            "completed_items": completed_count,
                            "total_items": total_lessons,
                            "current_item_title": f"[{idx}/{total_lessons}] 📄 স্লাইড আপলোড: {slide_title}",
                            "status_message": f"লেকচার স্লাইড (PDF) টেলিগ্রামে আপলোড হচ্ছে ({idx}/{total_lessons}): {slide_title}",
                        }
                    )
                    pdf_file = WORK_DIR / f"{sanitize_filename(slide_title)}_{idx}_{s_idx}.pdf"
                    try:
                        if download_pdf_file(slide_url, pdf_file):
                            slide_caption = (
                                f"📄 **লেকচার স্লাইড: {slide_title}**\n"
                                f"🎬 লেকচার #{idx}: {lesson_title}\n"
                                f"📘 {subj_name} • {chap_name}\n"
                                f"👨‍🏫 শিক্ষক: {teacher_name}"
                            )
                            sent_doc = await send_with_flood_protection(
                                lambda: tg_client.send_file(
                                    target_entity,
                                    file=str(pdf_file),
                                    caption=slide_caption,
                                    reply_to=reply_to_msg_id,
                                )
                            )
                            supabase.record_uploaded_item(
                                task_id=task_id,
                                program_id=program_id,
                                subject_code=subject_code,
                                chapter_id=chapter_id,
                                lesson_id=slide_key,
                                item_type="SLIDE",
                                title=slide_title,
                                chat_id=dedup_scope,
                                message_id=getattr(sent_doc, "id", 0),
                            )
                            uploaded_slides_count += 1
                            lesson_uploaded_any = True
                            log(f"📄 Uploaded slide [{idx}/{total_lessons}]: {slide_title}")
                            await asyncio.sleep(3)
                    except Exception as exc:
                        log(f"⚠️ Slide upload warning for {slide_title}: {exc}")
                    finally:
                        if pdf_file.exists():
                            pdf_file.unlink()

            if lesson_uploaded_any:
                completed_count += 1
                sync_task_progress(
                    {
                        "completed_items": completed_count,
                        "total_items": total_lessons,
                        "current_item_title": f"✅ [{completed_count}] {lesson_title}",
                        "status_message": (
                            f"আপলোড চলছে: 🎬 {uploaded_videos_count}টি ভিডিও ও 📄 {uploaded_slides_count}টি স্লাইড আপলোড হয়েছে।"
                        ),
                    }
                )

        if uploaded_videos_count == 0 and uploaded_slides_count == 0:
            fail_msg = (
                f"⚠️ কোনো ফাইল আপলোড করা যায়নি (মোট {total_lessons}টি লেকচার চেক করা হয়েছে, "
                f"যার মধ্যে {skipped_upcoming_count}টি আপকামিং বা রেকর্ডিং এখনো যুক্ত হয়নি)।"
            )
            log(fail_msg)
            sync_task_progress(
                {
                    "status": "FAILED",
                    "completed_items": 0,
                    "total_items": total_lessons,
                    "current_item_title": "কোনো ভিডিও/স্লাইড পাওয়া যায়নি",
                    "status_message": fail_msg,
                }
            )
            return

        final_msg = (
            f"✅ টাস্ক সম্পন্ন! মোট 🎬 {uploaded_videos_count}টি ভিডিও এবং 📄 {uploaded_slides_count}টি লেকচার স্লাইড "
            f"টেলিগ্রামে{topic_suffix} সফলভাবে আপলোড করা হয়েছে।"
            + (f" চ্যানেল লিংক: {invite_link}" if invite_link else "")
        )
        sync_task_progress(
            {
                "status": "COMPLETED",
                "completed_items": max(completed_count, 1),
                "total_items": max(completed_count, 1),
                "current_item_title": "সম্পন্ন ✅",
                "status_message": final_msg,
            }
        )
        supabase.notify_task_completion(
            title=f"✅ টেলিগ্রাম আপলোড সম্পন্ন: {task.get('title') or 'কোর্স আপলোড'}",
            message=final_msg,
            invite_link=invite_link,
        )


async def main_async() -> None:
    payload = decode_payload_env()
    bridge_cfg: Dict[str, Any] = {}
    bridge_path = Path(__file__).resolve().parent / "agent_runtime_bridge.json"
    if bridge_path.exists():
        try:
            bridge_cfg = json.loads(bridge_path.read_text(encoding="utf-8"))
        except Exception:
            bridge_cfg = {}

    supabase_url = (
        payload.get("supabase_url")
        or os.environ.get("SUPABASE_URL", "")
        or bridge_cfg.get("supabase_url", "")
    ).strip()
    supabase_key = (
        payload.get("supabase_anon_key")
        or os.environ.get("SUPABASE_ANON_KEY", "")
        or bridge_cfg.get("supabase_anon_key", "")
    ).strip()
    device_hash = (
        str(payload.get("device_hash") or "").strip()
        or str(bridge_cfg.get("device_hash") or "").strip()
    )

    supabase = SupabaseBridge(supabase_url, supabase_key)
    gh_bridge = GitHubAuthBridge(
        token=str(payload.get("github_pat") or os.environ.get("GITHUB_TOKEN", "")),
        repository=str(payload.get("github_repository") or os.environ.get("GITHUB_REPOSITORY", "")),
        branch=str(payload.get("github_branch") or "main"),
    )
    agent_cfg = supabase.get_agent_config(device_hash=device_hash)
    gh_state = gh_bridge.read_state()
    if gh_state:
        for k, v in gh_state.items():
            if v and not agent_cfg.get(k):
                agent_cfg[k] = unseal_secret(str(v)) if isinstance(v, str) else v

    if payload.get("agent_config") and isinstance(payload["agent_config"], dict):
        merged = dict(agent_cfg)
        for k, v in payload["agent_config"].items():
            if v:
                merged[k] = unseal_secret(str(v)) if isinstance(v, str) else v
        agent_cfg = merged

    # 0. Check if this run is an interactive in-app Telegram OTP / StringSession or Dialogs sync request
    auth_action = str(payload.get("auth_action") or "").strip().upper()
    if auth_action in ("TELEGRAM_SEND_CODE", "TELEGRAM_SIGN_IN", "TELEGRAM_FETCH_DIALOGS"):
        auth_payload = payload.get("auth_payload") if isinstance(payload.get("auth_payload"), dict) else {}
        await handle_telegram_auth_flow(
            auth_action=auth_action,
            auth_payload=auth_payload,
            agent_cfg=agent_cfg,
            gh_bridge=gh_bridge,
            supabase=supabase,
        )
        return

    input_task_id = os.environ.get("INPUT_TASK_ID", "ALL_PENDING").strip()

    # 1. If a direct task was passed in workflow_dispatch payload, run it immediately
    if payload.get("task") and isinstance(payload["task"], dict):
        await execute_task_async(
            task=payload["task"],
            agent_cfg=agent_cfg,
            supabase=supabase,
            gh_bridge=gh_bridge,
            payload_b64=os.environ.get("INPUT_TASK_PAYLOAD_B64", ""),
        )
        return

    # 2. Otherwise check Supabase pending/resuming tasks
    pending_tasks = supabase.fetch_pending_tasks(input_task_id)
    for task in pending_tasks:
        task_device_hash = str(task.get("created_by_device_hash") or device_hash).strip()
        task_cfg = supabase.get_agent_config(device_hash=task_device_hash) if task_device_hash else agent_cfg
        await execute_task_async(
            task=task,
            agent_cfg=task_cfg or agent_cfg,
            supabase=supabase,
            gh_bridge=gh_bridge,
            payload_b64="",
        )

    # 3. If triggered by hourly schedule cron, also check enabled recurring schedules
    if os.environ.get("TRIGGER_EVENT") == "schedule":
        schedules = supabase.fetch_enabled_schedules()
        for sched in schedules:
            sched_id = str(sched.get("id") or "")
            sched_device_hash = str(sched.get("created_by_device_hash") or device_hash).strip()
            sched_cfg = supabase.get_agent_config(device_hash=sched_device_hash) if sched_device_hash else agent_cfg
            synthetic_task = {
                "id": f"sched_run_{sched_id}_{int(time.time())}",
                "title": sched.get("title") or "দৈনিক রুটিন সিঙ্ক",
                "task_type": "COURSE_TELEGRAM_UPLOAD",
                "program_id": sched.get("program_id") or "",
                "program_title": sched.get("program_title") or "",
                "phase_id": sched.get("phase_id") or "",
                "subject_code": sched.get("subject_code") or "",
                "target_chat_id": sched.get("target_chat_id") or "",
                "target_channel_title": sched.get("target_channel_title") or "",
                "include_videos": sched.get("include_videos", True),
                "include_slides": sched.get("include_slides", True),
            }
            if synthetic_task["program_id"]:
                await execute_task_async(
                    task=synthetic_task,
                    agent_cfg=sched_cfg or agent_cfg,
                    supabase=supabase,
                    gh_bridge=gh_bridge,
                    payload_b64="",
                )
                supabase.update_schedule(
                    sched_id,
                    {
                        "last_run_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                        "last_run_status": "সফলভাবে সিঙ্ক সম্পন্ন ✅",
                    },
                )


if __name__ == "__main__":
    asyncio.run(main_async())
