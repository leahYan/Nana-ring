"""
Pydantic request schemas — what the Android app sends in POST /data/batch.

Rules enforced here:
  - id, user_id, server_timestamp are NEVER in request bodies (server-generated).
  - All value ranges match docs/ring_data_dictionary.csv exactly.
  - device_timestamp must be > 0 and not more than 60 s in the future
    (lenient on the past to allow WorkManager retries with old data).
  - ENUM fields validated with regex patterns.
  - Cross-field validators (e.g. diastolic < systolic) applied via model_validator.
"""

import time
import uuid
from typing import Optional

from pydantic import BaseModel, Field, model_validator


# ---------------------------------------------------------------------------
# Shared base
# ---------------------------------------------------------------------------

class _MetricBase(BaseModel):
    """Fields that every Android-sent record must include."""

    device_timestamp: int = Field(..., gt=0,
        description="Unix ms from the device clock. Must not be > 60 s in the future.")
    sync_id: Optional[uuid.UUID] = Field(None,
        description="Client idempotency key. Same sync_id + user_id → server deduplicates.")
    firmware_version: Optional[str] = Field(None, max_length=32)
    hardware_version: Optional[str] = Field(None, max_length=32)

    @model_validator(mode="after")
    def _clock_skew(self):
        now_ms = int(time.time() * 1000)
        if self.device_timestamp > now_ms + 60_000:
            raise ValueError(
                "device_timestamp is more than 60 s in the future — "
                "check device clock"
            )
        return self


# ---------------------------------------------------------------------------
# HeartRate
# ---------------------------------------------------------------------------

class HeartRateIn(_MetricBase):
    bpm: int = Field(..., ge=30, le=250, description="BPM, 30–250")
    rri: Optional[int] = Field(None, ge=200, le=2000, description="R-to-R interval ms, manual only")
    source: str = Field(..., pattern=r"^(manual|continuous)$")


# ---------------------------------------------------------------------------
# SpO2
# ---------------------------------------------------------------------------

class SpO2In(_MetricBase):
    percent: int = Field(..., ge=70, le=100, description="SpO2 %, 70–100")
    reading_type: str = Field(..., pattern=r"^(manual|hourly|interval)$")
    interval_minutes: Optional[int] = Field(None, ge=1, le=60)
    day_index: Optional[int] = Field(None, ge=0, le=7)


# ---------------------------------------------------------------------------
# BloodPressure
# ---------------------------------------------------------------------------

class BloodPressureIn(_MetricBase):
    systolic: int = Field(..., ge=60, le=250, description="mmHg")
    diastolic: int = Field(..., ge=40, le=150, description="mmHg")
    heart_rate_used: Optional[int] = Field(None, ge=30, le=250,
        description="HR value fed into SDK BP formula")
    measurement_type: str = Field(..., pattern=r"^(manual_ring|auto_hourly)$")

    @model_validator(mode="after")
    def _diastolic_less_than_systolic(self):
        if self.diastolic >= self.systolic:
            raise ValueError("diastolic must be strictly less than systolic")
        return self


# ---------------------------------------------------------------------------
# Temperature
# ---------------------------------------------------------------------------

class TemperatureIn(_MetricBase):
    celsius_primary: float = Field(..., ge=30.0, le=45.0)
    celsius_side1: Optional[float] = Field(None, ge=30.0, le=45.0)
    celsius_side2: Optional[float] = Field(None, ge=30.0, le=45.0)
    measurement_mode: str = Field(
        ..., pattern=r"^(manual_once|auto_series|three_point|interval|one_click)$"
    )
    time_span_minutes: Optional[int] = Field(None, ge=1, le=120,
        description="Interval between readings for auto_series mode")
    day_index: Optional[int] = Field(None, ge=0, le=7)


# ---------------------------------------------------------------------------
# HRV
# ---------------------------------------------------------------------------

class HRVIn(_MetricBase):
    ms: int = Field(..., ge=1, le=300, description="RMSSD in ms")
    interval_minutes: Optional[int] = Field(None, ge=1,
        description="Measurement interval for synced history, typically 30")
    day_offset: Optional[int] = Field(None, ge=0, le=6)
    source: str = Field(..., pattern=r"^(manual|continuous)$")


# ---------------------------------------------------------------------------
# Stress
# ---------------------------------------------------------------------------

class StressIn(_MetricBase):
    level: int = Field(..., ge=0, le=100, description="Stress index 0–100")
    interval_minutes: Optional[int] = Field(None, ge=1,
        description="Measurement interval for synced history, typically 30")
    day_offset: Optional[int] = Field(None, ge=0, le=6)
    source: str = Field(..., pattern=r"^(manual|continuous)$")


# ---------------------------------------------------------------------------
# Activity  (daily summary)
# ---------------------------------------------------------------------------

class ActivityIn(_MetricBase):
    steps: int = Field(..., ge=0, le=100_000)
    running_steps: Optional[int] = Field(None, ge=0)
    distance_meters: int = Field(..., ge=0, le=200_000)
    calories_kcal: int = Field(..., ge=0, le=10_000,
        description="Android must divide SDK's small-cal value by 1000 before sending")
    active_duration_seconds: int = Field(..., ge=0, le=86_400)
    sleep_duration_seconds: Optional[int] = Field(None, ge=0, le=86_400)
    days_ago: int = Field(..., ge=0, le=7)

    @model_validator(mode="after")
    def _running_le_total(self):
        if self.running_steps is not None and self.running_steps > self.steps:
            raise ValueError("running_steps must be <= steps")
        return self


# ---------------------------------------------------------------------------
# ActivityDetail  (one row per 15-minute window)
# ---------------------------------------------------------------------------

class ActivityDetailIn(BaseModel):
    """
    CoreMixin equivalent — no firmware/hardware per data dict.
    No _MetricBase inheritance because there is no firmware/hardware in this table.
    """

    device_timestamp: int = Field(..., gt=0)
    sync_id: Optional[uuid.UUID] = None
    time_index: int = Field(..., ge=0, le=95, description="15-min slot 0–95")
    walk_steps: int = Field(..., ge=0, le=5_000)
    run_steps: Optional[int] = Field(None, ge=0, le=5_000)
    distance_meters: int = Field(..., ge=0, le=10_000)
    calories_small_cal: int = Field(..., ge=0, le=1_000,
        description="Raw SDK small calories (cal), NOT kcal")
    day_offset: int = Field(..., ge=0, le=7)

    @model_validator(mode="after")
    def _clock_skew(self):
        now_ms = int(time.time() * 1000)
        if self.device_timestamp > now_ms + 60_000:
            raise ValueError("device_timestamp is more than 60 s in the future")
        return self


# ---------------------------------------------------------------------------
# Sleep stage detail  (embedded inside SleepIn)
# ---------------------------------------------------------------------------

class SleepStageIn(BaseModel):
    stage_index: int = Field(..., ge=0, le=500)
    stage_type: int = Field(..., ge=0, le=5,
        description="0=not sleeping, 1=removed, 2=light, 3=deep, 4=REM, 5=awake")
    duration_minutes: int = Field(..., ge=1, le=600)
    sync_id: Optional[uuid.UUID] = None


# ---------------------------------------------------------------------------
# Sleep  (session summary + embedded stage detail)
# ---------------------------------------------------------------------------

class SleepIn(_MetricBase):
    start_timestamp: int = Field(..., gt=0, description="SleepNewProtoResp.st * 1000")
    end_timestamp: int = Field(..., gt=0, description="SleepNewProtoResp.et * 1000")
    deep_minutes: int = Field(..., ge=0, le=600)
    light_minutes: int = Field(..., ge=0, le=600)
    rem_minutes: int = Field(..., ge=0, le=300)
    awake_minutes: int = Field(..., ge=0, le=300)
    not_worn_minutes: Optional[int] = Field(None, ge=0, le=600)
    waking_count: Optional[int] = Field(None, ge=0, le=50)
    lunch_start_timestamp: Optional[int] = Field(None, gt=0)
    lunch_end_timestamp: Optional[int] = Field(None, gt=0)
    day_offset: int = Field(..., ge=0, le=7)
    stages: list[SleepStageIn] = Field(default_factory=list,
        description="Embedded stage detail — maps to ring_sleep_stage_detail")

    @model_validator(mode="after")
    def _sleep_times_valid(self):
        if self.end_timestamp <= self.start_timestamp:
            raise ValueError("end_timestamp must be after start_timestamp")
        duration_ms = self.end_timestamp - self.start_timestamp
        if duration_ms > 86_400_000:
            raise ValueError("Sleep session exceeds 24 hours")
        if self.lunch_start_timestamp and self.lunch_end_timestamp:
            if self.lunch_end_timestamp <= self.lunch_start_timestamp:
                raise ValueError("lunch_end_timestamp must be after lunch_start_timestamp")
        return self


# ---------------------------------------------------------------------------
# Batch request / response
# ---------------------------------------------------------------------------

class BatchUploadRequest(BaseModel):
    heart_rate: list[HeartRateIn] = Field(default_factory=list)
    spo2: list[SpO2In] = Field(default_factory=list)
    blood_pressure: list[BloodPressureIn] = Field(default_factory=list)
    temperature: list[TemperatureIn] = Field(default_factory=list)
    hrv: list[HRVIn] = Field(default_factory=list)
    stress: list[StressIn] = Field(default_factory=list)
    activity: list[ActivityIn] = Field(default_factory=list)
    activity_detail: list[ActivityDetailIn] = Field(default_factory=list)
    sleep: list[SleepIn] = Field(default_factory=list)


class BatchUploadResponse(BaseModel):
    received: int
    inserted: int
    skipped: int
    errors: list[str] = Field(default_factory=list)
