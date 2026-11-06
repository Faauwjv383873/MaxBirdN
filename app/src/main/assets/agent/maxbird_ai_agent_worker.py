#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MaxBird 24/7 Autonomous AI Agent Cloud Worker
=============================================
Executes background tasks and 24/7 scheduled routines dispatched by the MaxBird Android App:
1. Connects to the user's Personal Telegram Account via Telethon (MTProto StringSession)
   or Telegram Bot API.
2. Automatically creates Telegram Channels or Supergroups (including Subject/Chapter Forum Topics)
   and exports invite links back to Supabase & the MaxBird App.
3. Fetches course subjects, chapters, recorded/live video HLS (.m3u8) / MP4 streams, and
   Lecture Slide PDFs directly from Shikho GraphQL API.
4. Downloads videos (via yt-dlp + ffmpeg) & PDF slides one by one, uploads them to Telegram
   with rich Bengali captions and streaming support, immediately deletes local temp files,
   and updates live progress in Supabase.
5. Self-chains (re-triggers itself via GitHub API) before the 6-hour GitHub Actions limit
   if more items remain in the queue, achieving true 24/7 lifetime operation.
6. Dispatches completion Push Notifications via Supabase / OneSignal RPC.
"""

import asyncio
import base64
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import requests
from telethon import TelegramClient, functions, types
from telethon.errors import FloodWaitError, SessionPasswordNeededError
from telethon.sessions import StringSession

START_TIME = time.time()
# Re-trigger self after 4 hours 45 minutes (17100 seconds) so we never hit GitHub's 6h hard timeout
MAX_RUNTIME_SECONDS = 17100

DEFAULT_TELEGRAM_API_ID = "38634154"
DEFAULT_TELEGRAM_API_HASH = "a4194e506db4ad97f1dba20ec28ac383"

SHIKHO_GRAPHQL_URL = "https://api.shikho.com/graphql"
WORK_DIR = Path("/tmp/maxbird_agent_work")
WORK_DIR.mkdir(parents=True, exist_ok=True)


def log(msg: str) -> None:
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    print(f"[{now}] {msg}", flush=True)


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
        try:
            if clean_hash:
                r = requests.get(
                    f"{self.url}/rest/v1/ai_agent_configs?or=(device_hash.eq.{clean_hash},updated_by_device_hash.eq.{clean_hash})&order=updated_at.desc&limit=1",
                    headers=self.headers,
                    timeout=15,
                )
                if r.ok and isinstance(r.json(), list) and r.json():
                    return r.json()[0]

            r_fallback = requests.get(
                f"{self.url}/rest/v1/ai_agent_configs?order=updated_at.desc&limit=1",
                headers=self.headers,
                timeout=15,
            )
            if r_fallback.ok and isinstance(r_fallback.json(), list) and r_fallback.json():
                return r_fallback.json()[0]
        except Exception as exc:
            log(f"Supabase get_agent_config warning: {exc}")
        return {}

    def update_agent_config(self, device_hash: str, patch: Dict[str, Any]) -> None:
        if not self.enabled:
            return
        clean_hash = (device_hash or "GLOBAL_DEFAULT").strip() or "GLOBAL_DEFAULT"
        patch["updated_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        patch["updated_by_device_hash"] = clean_hash
        try:
            # First try PATCH by device_hash or updated_by_device_hash
            r = requests.patch(
                f"{self.url}/rest/v1/ai_agent_configs?or=(device_hash.eq.{clean_hash},updated_by_device_hash.eq.{clean_hash})",
                headers=self.headers,
                json=patch,
                timeout=15,
            )
            if r.ok and isinstance(r.json(), list) and len(r.json()) > 0:
                return
            # Fallback: patch id=1 or insert
            r2 = requests.patch(
                f"{self.url}/rest/v1/ai_agent_configs?id=eq.1",
                headers=self.headers,
                json=patch,
                timeout=15,
            )
            if r2.ok and isinstance(r2.json(), list) and len(r2.json()) > 0:
                return
            insert_payload = dict(patch)
            insert_payload["device_hash"] = clean_hash
            hdrs = dict(self.headers)
            hdrs["Prefer"] = "resolution=merge-duplicates"
            requests.post(
                f"{self.url}/rest/v1/ai_agent_configs",
                headers=hdrs,
                json=insert_payload,
                timeout=15,
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


class ShikhoClient:
    def __init__(self, access_token: str):
        self.access_token = access_token.strip()
        self.session = requests.Session()
        self.session.headers.update(
            {
                "Content-Type": "application/json",
                "Accept": "application/json",
                "User-Agent": "ShikhoAndroid/4.6.2 (Android 14; MaxBird-AI-Agent)",
                "x-vendor": "BD",
            }
        )
        if self.access_token:
            self.session.headers["Authorization"] = f"Bearer {self.access_token}"

    def graphql(self, operation_name: str, query: str, variables: Dict[str, Any]) -> Dict[str, Any]:
        payload = {
            "operationName": operation_name,
            "query": query,
            "variables": variables,
        }
        r = self.session.post(SHIKHO_GRAPHQL_URL, json=payload, timeout=30)
        r.raise_for_status()
        return r.json()

    def fetch_lessons_for_program(
        self, program_id: str, phase_id: str = "", subject_code: str = "", chapter_id: str = ""
    ) -> List[Dict[str, Any]]:
        query = """
        query GetProgramLessons($program_id: ID!, $phase_id: String) {
          getAcademicProgramLessonList(academic_program_id: $program_id, phase_id: $phase_id) {
            id
            title
            type
            subject_code
            subject_name
            chapter_id
            chapter_name
            serial
            content_id
            video_url
            slide_url
            teacher {
              name
            }
            live_class {
              id
              title
              recording_url
              hls_url
              slide_url
            }
            attachments {
              id
              title
              url
            }
          }
        }
        """
        variables: Dict[str, Any] = {"program_id": program_id}
        if phase_id:
            variables["phase_id"] = phase_id
        try:
            data = self.graphql("GetProgramLessons", query, variables)
            lessons = (data.get("data") or {}).get("getAcademicProgramLessonList") or []
            if not isinstance(lessons, list):
                lessons = []
        except Exception as exc:
            log(f"GraphQL GetProgramLessons fallback check: {exc}")
            lessons = []

        # Filter by subject_code and chapter_id if specified
        filtered = []
        for item in lessons:
            if not isinstance(item, dict):
                continue
            if subject_code and item.get("subject_code") and item.get("subject_code") != subject_code:
                continue
            if chapter_id and item.get("chapter_id") and item.get("chapter_id") != chapter_id:
                continue
            filtered.append(item)
        return filtered

    def resolve_stream_and_slide(self, lesson: Dict[str, Any]) -> Tuple[str, List[Dict[str, str]]]:
        live_obj = lesson.get("live_class") or {}
        video_url = (
            lesson.get("video_url")
            or live_obj.get("hls_url")
            or live_obj.get("recording_url")
            or lesson.get("stream_url")
            or ""
        ).strip()

        slides: List[Dict[str, str]] = []
        primary_slide = (
            lesson.get("slide_url")
            or live_obj.get("slide_url")
            or lesson.get("pdf_url")
            or ""
        ).strip()
        if primary_slide:
            slides.append({"title": f"{lesson.get('title', 'লেকচার')} - স্লাইড", "url": primary_slide})

        for att in lesson.get("attachments") or []:
            if isinstance(att, dict):
                u = (att.get("url") or att.get("download_url") or "").strip()
                if u and all(s["url"] != u for s in slides):
                    slides.append({"title": att.get("title") or "লেকচার নোট", "url": u})

        # If video_url is not embedded in list item, try single lesson detail query
        lesson_id = (lesson.get("id") or "").strip()
        if not video_url and lesson_id:
            detail_query = """
            query GetLessonDetail($id: ID!) {
              getLessonById(id: $id) {
                id
                title
                video_url
                slide_url
                live_class {
                  hls_url
                  recording_url
                  slide_url
                }
                attachments {
                  id
                  title
                  url
                }
              }
            }
            """
            try:
                res = self.graphql("GetLessonDetail", detail_query, {"id": lesson_id})
                detail = (res.get("data") or {}).get("getLessonById") or {}
                if isinstance(detail, dict):
                    dl_live = detail.get("live_class") or {}
                    video_url = (
                        detail.get("video_url")
                        or dl_live.get("hls_url")
                        or dl_live.get("recording_url")
                        or ""
                    ).strip()
                    dl_slide = (detail.get("slide_url") or dl_live.get("slide_url") or "").strip()
                    if dl_slide and all(s["url"] != dl_slide for s in slides):
                        slides.append({"title": f"{lesson.get('title', 'লেকচার')} - স্লাইড", "url": dl_slide})
                    for att in detail.get("attachments") or []:
                        if isinstance(att, dict):
                            u = (att.get("url") or "").strip()
                            if u and all(s["url"] != u for s in slides):
                                slides.append({"title": att.get("title") or "লেকচার শিট", "url": u})
            except Exception as exc:
                log(f"Detail query info for {lesson_id}: {exc}")

        return video_url, slides


def sanitize_filename(name: str) -> str:
    cleaned = re.sub(r'[\\/*?:"<>|]+', "_", name).strip()
    return cleaned[:100] or "lecture"


def download_video_file(video_url: str, output_path: Path, quality: str = "720p") -> bool:
    if output_path.exists():
        output_path.unlink()

    height = "720"
    if "1080" in quality:
        height = "1080"
    elif "480" in quality:
        height = "480"
    elif "360" in quality:
        height = "360"

    format_selector = f"bestvideo[height<={height}]+bestaudio/best[height<={height}]/best"
    cmd = [
        "yt-dlp",
        "--no-part",
        "--no-playlist",
        "--concurrent-fragments",
        "8",
        "-f",
        format_selector,
        "--merge-output-format",
        "mp4",
        "-o",
        str(output_path),
        video_url,
    ]
    log(f"⬇️ Downloading video ({quality}): {video_url[:80]}...")
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if res.returncode == 0 and output_path.exists() and output_path.stat().st_size > 1024:
        return True

    # Fallback direct ffmpeg copy for raw HLS / MP4 streams
    ffmpeg_cmd = [
        "ffmpeg",
        "-y",
        "-i",
        video_url,
        "-c",
        "copy",
        "-bsf:a",
        "aac_adtstoasc",
        str(output_path),
    ]
    res2 = subprocess.run(ffmpeg_cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    return res2.returncode == 0 and output_path.exists() and output_path.stat().st_size > 1024


def download_pdf_file(pdf_url: str, output_path: Path) -> bool:
    if output_path.exists():
        output_path.unlink()
    try:
        with requests.get(pdf_url, stream=True, timeout=60) as r:
            r.raise_for_status()
            with open(output_path, "wb") as f:
                for chunk in r.iter_content(chunk_size=65536):
                    if chunk:
                        f.write(chunk)
        return output_path.exists() and output_path.stat().st_size > 100
    except Exception as exc:
        log(f"⚠️ Failed to download slide PDF: {exc}")
        return False


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


async def handle_telegram_auth_flow(
    auth_action: str,
    auth_payload: Dict[str, Any],
    agent_cfg: Dict[str, Any],
    supabase: SupabaseBridge,
) -> None:
    """
    Handles automatic in-app Telegram StringSession generation:
    - TELEGRAM_SEND_CODE: Sends OTP to user's Telegram phone and saves partial StringSession + phone_code_hash to Supabase.
    - TELEGRAM_SIGN_IN: Completes sign_in with OTP (and optional 2FA password), generates final StringSession, and saves it to Supabase under device_hash.
    """
    device_hash = (
        str(auth_payload.get("device_hash") or "")
        or str(agent_cfg.get("device_hash") or "")
        or str(agent_cfg.get("updated_by_device_hash") or "GLOBAL_DEFAULT")
    ).strip()

    api_id_raw = (
        str(auth_payload.get("telegram_api_id") or "")
        or str(agent_cfg.get("telegram_api_id") or "")
        or DEFAULT_TELEGRAM_API_ID
    ).strip()
    api_hash = (
        str(auth_payload.get("telegram_api_hash") or "")
        or str(agent_cfg.get("telegram_api_hash") or "")
        or DEFAULT_TELEGRAM_API_HASH
    ).strip()
    phone = (
        str(auth_payload.get("telegram_phone") or "")
        or str(agent_cfg.get("telegram_phone") or "")
    ).strip()

    if not api_id_raw.isdigit() or not api_hash or not phone:
        msg = "❌ টেলিগ্রাম API_ID, API_HASH বা ফোন নাম্বার সঠিক নয়।"
        log(msg)
        supabase.update_agent_config(
            device_hash,
            {"telegram_auth_state": "ERROR", "telegram_auth_message": msg},
        )
        return

    api_id = int(api_id_raw)

    if auth_action == "TELEGRAM_SEND_CODE":
        log(f"📲 Sending Telegram login code to {phone} (API_ID={api_id})...")
        client = TelegramClient(StringSession(), api_id, api_hash)
        try:
            await client.connect()
            sent = await client.send_code_request(phone)
            partial_session = client.session.save()
            supabase.update_agent_config(
                device_hash,
                {
                    "telegram_api_id": str(api_id),
                    "telegram_api_hash": api_hash,
                    "telegram_phone": phone,
                    "telegram_phone_code_hash": sent.phone_code_hash,
                    "telegram_partial_session": partial_session,
                    "telegram_auth_state": "WAITING_FOR_OTP",
                    "telegram_auth_message": "✅ আপনার টেলিগ্রাম অ্যাপে ৫ সংখ্যার ওটিপি (OTP) কোড পাঠানো হয়েছে! নিচে কোডটি লিখে ভেরিফাই চাপুন।",
                },
            )
            log("✅ OTP sent and partial session saved to Supabase.")
        except Exception as exc:
            err_msg = f"❌ ওটিপি পাঠাতে সমস্যা হয়েছে: {exc}"
            log(err_msg)
            supabase.update_agent_config(
                device_hash,
                {
                    "telegram_auth_state": "ERROR",
                    "telegram_auth_message": err_msg,
                },
            )
        finally:
            await client.disconnect()

    elif auth_action == "TELEGRAM_SIGN_IN":
        otp_code = (
            str(auth_payload.get("telegram_otp_code") or "")
            or str(agent_cfg.get("telegram_otp_code") or "")
        ).strip()
        two_fa_password = (
            str(auth_payload.get("telegram_2fa_password") or "")
            or str(agent_cfg.get("telegram_2fa_password") or "")
        ).strip()
        phone_code_hash = (
            str(auth_payload.get("telegram_phone_code_hash") or "")
            or str(agent_cfg.get("telegram_phone_code_hash") or "")
        ).strip()
        partial_session = (
            str(auth_payload.get("telegram_partial_session") or "")
            or str(agent_cfg.get("telegram_partial_session") or "")
        ).strip()

        if not partial_session or not phone_code_hash:
            err_msg = "❌ ওটিপি সেশন পাওয়া যায়নি। অনুগ্রহ করে পুনরায় 'ওটিপি পাঠান' বাটনে চাপুন।"
            log(err_msg)
            supabase.update_agent_config(
                device_hash,
                {"telegram_auth_state": "ERROR", "telegram_auth_message": err_msg},
            )
            return

        log(f"🔐 Verifying Telegram OTP for {phone} and generating permanent STRING_SESSION...")
        client = TelegramClient(StringSession(partial_session), api_id, api_hash)
        try:
            await client.connect()
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
                    supabase.update_agent_config(
                        device_hash,
                        {
                            "telegram_partial_session": saved_partial,
                            "telegram_auth_state": "NEED_2FA",
                            "telegram_auth_message": "🔒 আপনার টেলিগ্রামে Two-Step Verification (2FA) পাসওয়ার্ড চালু আছে। নিচে 2FA পাসওয়ার্ডটি লিখে ভেরিফাই চাপুন।",
                        },
                    )
                    log("🔒 2FA password required; notified app via Supabase.")
                    return

            final_string_session = client.session.save()
            me = await client.get_me()
            first_name = getattr(me, "first_name", "") or "Telegram User"
            username = getattr(me, "username", "") or ""
            account_display = f"{first_name} (@{username})" if username else first_name

            supabase.update_agent_config(
                device_hash,
                {
                    "telegram_api_id": str(api_id),
                    "telegram_api_hash": api_hash,
                    "telegram_phone": phone,
                    "telegram_session_string": final_string_session,
                    "telegram_account_name": account_display,
                    "telegram_auth_state": "CONNECTED",
                    "telegram_auth_message": f"✅ অটোমেটিক STRING_SESSION তৈরি ও সেভ সম্পন্ন! কানেক্টেড: {account_display}",
                    "telegram_partial_session": "",
                    "telegram_otp_code": "",
                    "telegram_2fa_password": "",
                },
            )
            log(f"✅ Permanent STRING_SESSION generated & saved to Supabase for {account_display}!")
        except Exception as exc:
            err_msg = f"❌ ওটিপি ভেরিফাই ব্যর্থ: {exc}"
            log(err_msg)
            supabase.update_agent_config(
                device_hash,
                {"telegram_auth_state": "ERROR", "telegram_auth_message": err_msg},
            )
        finally:
            await client.disconnect()


async def ensure_telegram_destination(
    client: TelegramClient,
    task: Dict[str, Any],
    supabase: SupabaseBridge,
) -> Tuple[Any, str]:
    """
    Ensures the target Telegram Channel or Supergroup exists.
    If `target_chat_id` is empty or `create_new_channel` is True without an existing chat_id,
    creates a brand new Channel/Supergroup on the user's personal Telegram account.
    """
    existing_chat_id = str(task.get("target_chat_id") or "").strip()
    existing_invite = str(task.get("telegram_invite_link") or "").strip()
    channel_title = (
        str(task.get("target_channel_title") or "").strip()
        or str(task.get("program_title") or "").strip()
        or "MaxBird Course Archive"
    )
    subject_title = str(task.get("subject_title") or "").strip()
    if subject_title and subject_title not in channel_title:
        channel_title = f"{channel_title} — {subject_title}"

    if existing_chat_id:
        try:
            entity_key: Any = int(existing_chat_id) if existing_chat_id.lstrip("-").isdigit() else existing_chat_id
            entity = await client.get_entity(entity_key)
            return entity, existing_invite
        except Exception as exc:
            log(f"Existing chat_id {existing_chat_id} lookup note: {exc}. Creating new channel...")

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

    # Send & pin welcome index header message
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

    api_id_raw = (
        str(task.get("telegram_api_id") or "")
        or str(agent_cfg.get("telegram_api_id") or "")
        or os.environ.get("TELEGRAM_API_ID", "")
        or DEFAULT_TELEGRAM_API_ID
    ).strip()
    api_hash = (
        str(task.get("telegram_api_hash") or "")
        or str(agent_cfg.get("telegram_api_hash") or "")
        or os.environ.get("TELEGRAM_API_HASH", "")
        or DEFAULT_TELEGRAM_API_HASH
    ).strip()
    session_str = (
        str(task.get("telegram_session_string") or "")
        or str(agent_cfg.get("telegram_session_string") or "")
        or os.environ.get("TELEGRAM_SESSION_STRING", "")
    ).strip()
    shikho_token = (
        str(task.get("shikho_access_token") or "")
        or str(agent_cfg.get("shikho_access_token") or "")
        or os.environ.get("SHIKHO_ACCESS_TOKEN", "")
    ).strip()

    if not api_id_raw.isdigit() or not api_hash or not session_str:
        err = "টেলিগ্রাম SESSION_STRING এখনো তৈরি করা হয়নি। অনুগ্রহ করে অ্যাপের '🔌 একাউন্টস' ট্যাব থেকে আপনার ফোন নাম্বার ও ওটিপি দিয়ে অটোমেটিক STRING_SESSION তৈরি করে নিন।"
        log(f"❌ {err}")
        supabase.update_task(task_id, {"status": "FAILED", "status_message": err})
        return

    api_id = int(api_id_raw)
    supabase.update_task(
        task_id,
        {
            "status": "RUNNING",
            "github_run_id": os.environ.get("GITHUB_RUN_ID", ""),
            "status_message": "টেলিগ্রাম অ্যাকাউন্ট ও কোর্সের সার্ভারের সাথে সংযোগ স্থাপন করা হচ্ছে...",
        },
    )

    async with TelegramClient(StringSession(session_str), api_id, api_hash) as tg_client:
        me = await tg_client.get_me()
        log(f"🤖 Connected to Personal Telegram Account: {getattr(me, 'first_name', 'User')} (@{getattr(me, 'username', '')})")

        target_entity, invite_link = await ensure_telegram_destination(tg_client, task, supabase)
        chat_id_str = str(task.get("target_chat_id") or "")

        if task_type == "CREATE_CHANNEL_ONLY":
            done_msg = f"টেলিগ্রাম চ্যানেল সফলভাবে তৈরি হয়েছে! লিংক: {invite_link or 'প্রাইভেট চ্যানেল'}"
            supabase.update_task(
                task_id,
                {
                    "status": "COMPLETED",
                    "completed_items": 1,
                    "total_items": 1,
                    "status_message": done_msg,
                },
            )
            supabase.notify_task_completion("✅ টেলিগ্রাম চ্যানেল প্রস্তুত!", done_msg, invite_link)
            return

        # Check if explicit lessons list was provided in extra_payload_json
        extra = task.get("extra_payload_json") or {}
        if isinstance(extra, str):
            try:
                extra = json.loads(extra)
            except Exception:
                extra = {}

        bundled_lessons = extra.get("lessons") if isinstance(extra, dict) else None
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
        supabase.update_task(
            task_id,
            {
                "total_items": total_lessons,
                "status_message": f"মোট {total_lessons}টি লেকচার পাওয়া গেছে। ক্রমানুসারে ডাউনলোড ও আপলোড শুরু হচ্ছে...",
            },
        )

        completed_count = int(task.get("completed_items") or 0)

        for idx, lesson in enumerate(lessons, start=1):
            # Check runtime budget for 24/7 self-chaining
            if time.time() - START_TIME >= MAX_RUNTIME_SECONDS:
                log("⏱️ Approaching GitHub Actions 6-hour limit. Saving state and triggering continuation run...")
                supabase.update_task(
                    task_id,
                    {
                        "status": "RESUMING",
                        "completed_items": completed_count,
                        "status_message": f"৬ ঘণ্টার সেশন রিস্টার্ট হচ্ছে ({completed_count}/{total_lessons} সম্পন্ন)...",
                    },
                )
                trigger_self_continuation(task_id, payload_b64, agent_cfg)
                return

            lesson_id = str(lesson.get("id") or f"lesson_{idx}")
            lesson_title = str(lesson.get("title") or f"লেকচার {idx}").strip()
            subj_name = str(lesson.get("subject_name") or task.get("subject_title") or "বিষয়").strip()
            chap_name = str(lesson.get("chapter_name") or task.get("chapter_title") or "অধ্যায়").strip()
            teacher_obj = lesson.get("teacher") or {}
            teacher_name = (
                teacher_obj.get("name") if isinstance(teacher_obj, dict) else str(lesson.get("teacher_name") or "")
            ) or "শিক্ষক"

            video_url, slides = shikho_client.resolve_stream_and_slide(lesson)

            caption_base = (
                f"🎬 **লেকচার #{idx}: {lesson_title}**\n"
                f"📘 বিষয়: {subj_name}\n"
                f"📂 অধ্যায়: {chap_name}\n"
                f"👨‍🏫 শিক্ষক: {teacher_name}\n"
                f"📚 কোর্স: {task.get('program_title') or ''}"
            ).strip()

            # 1. Upload Video if enabled & not already uploaded
            if include_videos and video_url:
                if not supabase.is_item_already_uploaded(program_id, lesson_id, "VIDEO", chat_id_str):
                    supabase.update_task(
                        task_id,
                        {
                            "current_item_title": f"[{idx}/{total_lessons}] ভিডিও: {lesson_title}",
                            "status_message": f"ডাউনলোড ও টেলিগ্রামে আপলোড হচ্ছে ({idx}/{total_lessons}): {lesson_title}",
                        },
                    )
                    video_file = WORK_DIR / f"{sanitize_filename(lesson_title)}_{idx}.mp4"
                    try:
                        if download_video_file(video_url, video_file, video_quality):
                            sent_vid = await send_with_flood_protection(
                                lambda: tg_client.send_file(
                                    target_entity,
                                    file=str(video_file),
                                    caption=caption_base,
                                    supports_streaming=True,
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
                                chat_id=chat_id_str,
                                message_id=getattr(sent_vid, "id", 0),
                            )
                            log(f"✅ Uploaded video [{idx}/{total_lessons}]: {lesson_title}")
                            await asyncio.sleep(12)
                    except Exception as exc:
                        log(f"⚠️ Video upload warning for {lesson_title}: {exc}")
                    finally:
                        if video_file.exists():
                            video_file.unlink()

            # 2. Upload Lecture Slides (PDFs) if enabled & not already uploaded
            if include_slides and slides:
                for s_idx, slide in enumerate(slides, start=1):
                    slide_url = slide.get("url") or ""
                    slide_title = slide.get("title") or f"{lesson_title} - স্লাইড {s_idx}"
                    slide_key = f"{lesson_id}_slide_{s_idx}"
                    if not slide_url or supabase.is_item_already_uploaded(
                        program_id, slide_key, "SLIDE", chat_id_str
                    ):
                        continue

                    pdf_file = WORK_DIR / f"{sanitize_filename(slide_title)}_{idx}_{s_idx}.pdf"
                    try:
                        if download_pdf_file(slide_url, pdf_file):
                            slide_caption = (
                                f"📄 **লেকচার স্লাইড: {slide_title}**\n"
                                f"🎬 লেকচার #{idx}: {lesson_title}\n"
                                f"📘 {subj_name} • {chap_name}"
                            )
                            sent_doc = await send_with_flood_protection(
                                lambda: tg_client.send_file(
                                    target_entity,
                                    file=str(pdf_file),
                                    caption=slide_caption,
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
                                chat_id=chat_id_str,
                                message_id=getattr(sent_doc, "id", 0),
                            )
                            log(f"📄 Uploaded slide [{idx}/{total_lessons}]: {slide_title}")
                            await asyncio.sleep(6)
                    except Exception as exc:
                        log(f"⚠️ Slide upload warning for {slide_title}: {exc}")
                    finally:
                        if pdf_file.exists():
                            pdf_file.unlink()

            completed_count = idx
            supabase.update_task(
                task_id,
                {
                    "completed_items": completed_count,
                    "total_items": total_lessons,
                    "status_message": f"{completed_count}/{total_lessons} টি লেকচার প্রসেস সম্পন্ন হয়েছে।",
                },
            )

        final_msg = (
            f"✅ টাস্ক সম্পন্ন! মোট {total_lessons}টি লেকচার ও স্লাইড টেলিগ্রামে আপলোড করা হয়েছে।"
            + (f" চ্যানেল লিংক: {invite_link}" if invite_link else "")
        )
        supabase.update_task(
            task_id,
            {
                "status": "COMPLETED",
                "completed_items": total_lessons,
                "total_items": total_lessons,
                "current_item_title": "সম্পন্ন ✅",
                "status_message": final_msg,
            },
        )
        supabase.notify_task_completion(
            title=f"✅ AI এজেন্ট টাস্ক সম্পন্ন: {task.get('title') or 'কোর্স আপলোড'}",
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
    agent_cfg = supabase.get_agent_config(device_hash=device_hash)

    # Merge any direct credentials from payload
    if payload.get("agent_config") and isinstance(payload["agent_config"], dict):
        merged = dict(agent_cfg)
        for k, v in payload["agent_config"].items():
            if v:
                merged[k] = v
        agent_cfg = merged

    # 0. Check if this run is an interactive in-app Telegram OTP / StringSession generator request
    auth_action = str(payload.get("auth_action") or "").strip().upper()
    if auth_action in ("TELEGRAM_SEND_CODE", "TELEGRAM_SIGN_IN"):
        auth_payload = payload.get("auth_payload") if isinstance(payload.get("auth_payload"), dict) else {}
        await handle_telegram_auth_flow(
            auth_action=auth_action,
            auth_payload=auth_payload,
            agent_cfg=agent_cfg,
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
