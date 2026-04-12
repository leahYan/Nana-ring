"""
SQLAlchemy ORM models.

Column names, types, and nullability are derived exclusively from
docs/ring_data_dictionary.csv (strict isomorphism requirement).

Two mixins exist:
  - CoreMixin       : user_id, device_timestamp, server_timestamp, sync_id
  - TimestampMixin  : CoreMixin + firmware_version + hardware_version

ActivityDetail uses CoreMixin only (firmware/hardware absent from data dict).
SleepStageDetail is a child table – no device/server timestamps at all.
"""

import uuid

from sqlalchemy import (
    BigInteger,
    ForeignKey,
    Integer,
    Numeric,
    SmallInteger,
    String,
    UniqueConstraint,
    Uuid,
    text,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


# ---------------------------------------------------------------------------
# Shared mixins
# ---------------------------------------------------------------------------

class CoreMixin:
    """Minimum columns shared by every metric record."""

    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), nullable=False, index=True
    )
    device_timestamp: Mapped[int] = mapped_column(BigInteger, nullable=False)
    server_timestamp: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        server_default=text("(EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT"),
    )
    sync_id: Mapped[uuid.UUID | None] = mapped_column(Uuid(as_uuid=True), nullable=True)


class TimestampMixin(CoreMixin):
    """CoreMixin + firmware/hardware version fields."""

    firmware_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    hardware_version: Mapped[str | None] = mapped_column(String(32), nullable=True)


# ---------------------------------------------------------------------------
# ring_heart_rate
# ---------------------------------------------------------------------------

class HeartRate(Base, TimestampMixin):
    __tablename__ = "ring_heart_rate"
    __table_args__ = (
        UniqueConstraint("sync_id", "user_id", name="uq_heart_rate_sync"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    bpm: Mapped[int] = mapped_column(SmallInteger, nullable=False)   # INT16, 30–250
    rri: Mapped[int | None] = mapped_column(Integer, nullable=True)  # INT32, 200–2000 ms
    source: Mapped[str] = mapped_column(String(32), nullable=False)  # manual | continuous


# ---------------------------------------------------------------------------
# ring_spo2
# ---------------------------------------------------------------------------

class SpO2(Base, TimestampMixin):
    __tablename__ = "ring_spo2"
    __table_args__ = (
        UniqueConstraint("sync_id", "user_id", name="uq_spo2_sync"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    percent: Mapped[int] = mapped_column(SmallInteger, nullable=False)      # INT8, 70–100
    reading_type: Mapped[str] = mapped_column(String(32), nullable=False)   # manual | hourly | interval
    interval_minutes: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)  # INT16, 1–60
    day_index: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)         # INT8, 0–7


# ---------------------------------------------------------------------------
# ring_blood_pressure
# ---------------------------------------------------------------------------

class BloodPressure(Base, TimestampMixin):
    __tablename__ = "ring_blood_pressure"
    __table_args__ = (
        UniqueConstraint("sync_id", "user_id", name="uq_bp_sync"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    systolic: Mapped[int] = mapped_column(SmallInteger, nullable=False)       # INT16, 60–250 mmHg
    diastolic: Mapped[int] = mapped_column(SmallInteger, nullable=False)      # INT16, 40–150 mmHg
    heart_rate_used: Mapped[int | None] = mapped_column(Integer, nullable=True)  # INT32, 30–250
    measurement_type: Mapped[str] = mapped_column(String(32), nullable=False) # manual_ring | auto_hourly


# ---------------------------------------------------------------------------
# ring_temperature
# ---------------------------------------------------------------------------

class Temperature(Base, TimestampMixin):
    __tablename__ = "ring_temperature"
    __table_args__ = (
        UniqueConstraint("sync_id", "user_id", name="uq_temperature_sync"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    celsius_primary: Mapped[float] = mapped_column(Numeric(5, 2), nullable=False)        # 30.0–45.0
    celsius_side1: Mapped[float | None] = mapped_column(Numeric(5, 2), nullable=True)
    celsius_side2: Mapped[float | None] = mapped_column(Numeric(5, 2), nullable=True)
    measurement_mode: Mapped[str] = mapped_column(String(32), nullable=False)
    # manual_once | auto_series | three_point | interval | one_click
    time_span_minutes: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)   # INT16, 1–120
    day_index: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)           # INT8, 0–7


# ---------------------------------------------------------------------------
# ring_hrv
# ---------------------------------------------------------------------------

class HRV(Base, TimestampMixin):
    __tablename__ = "ring_hrv"
    __table_args__ = (
        UniqueConstraint("sync_id", "user_id", name="uq_hrv_sync"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    ms: Mapped[int] = mapped_column(SmallInteger, nullable=False)            # INT16, RMSSD 1–300 ms
    interval_minutes: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)   # typically 30
    day_offset: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)         # INT8, 0–6
    source: Mapped[str] = mapped_column(String(32), nullable=False)          # manual | continuous


# ---------------------------------------------------------------------------
# ring_stress
# ---------------------------------------------------------------------------

class Stress(Base, TimestampMixin):
    __tablename__ = "ring_stress"
    __table_args__ = (
        UniqueConstraint("sync_id", "user_id", name="uq_stress_sync"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    level: Mapped[int] = mapped_column(SmallInteger, nullable=False)         # INT8, 0–100
    interval_minutes: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)
    day_offset: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)         # INT8, 0–6
    source: Mapped[str] = mapped_column(String(32), nullable=False)          # manual | continuous


# ---------------------------------------------------------------------------
# ring_activity  (one summary row per calendar day)
# ---------------------------------------------------------------------------

class Activity(Base, TimestampMixin):
    __tablename__ = "ring_activity"
    __table_args__ = (
        UniqueConstraint("sync_id", "user_id", name="uq_activity_sync"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    steps: Mapped[int] = mapped_column(Integer, nullable=False)                           # INT32, 0–100000
    running_steps: Mapped[int | None] = mapped_column(Integer, nullable=True)            # INT32
    distance_meters: Mapped[int] = mapped_column(Integer, nullable=False)                # INT32, 0–200000
    calories_kcal: Mapped[int] = mapped_column(Integer, nullable=False)                  # INT32, SDK cal / 1000
    active_duration_seconds: Mapped[int] = mapped_column(Integer, nullable=False)        # INT32, 0–86400
    sleep_duration_seconds: Mapped[int | None] = mapped_column(Integer, nullable=True)  # INT32, rough figure
    days_ago: Mapped[int] = mapped_column(SmallInteger, nullable=False)                  # INT8, 0–7


# ---------------------------------------------------------------------------
# ring_activity_detail  (one row per 15-minute window)
# ---------------------------------------------------------------------------

class ActivityDetail(Base, CoreMixin):
    """
    Uses CoreMixin only — firmware/hardware are not defined in the data dict
    for this child table (captured on the parent ring_activity row instead).
    """
    __tablename__ = "ring_activity_detail"
    __table_args__ = (
        UniqueConstraint("sync_id", "user_id", name="uq_activity_detail_sync"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    time_index: Mapped[int] = mapped_column(SmallInteger, nullable=False)      # INT8, 0–95
    walk_steps: Mapped[int] = mapped_column(Integer, nullable=False)           # INT32, 0–5000
    run_steps: Mapped[int | None] = mapped_column(Integer, nullable=True)      # INT32
    distance_meters: Mapped[int] = mapped_column(Integer, nullable=False)      # INT32
    calories_small_cal: Mapped[int] = mapped_column(SmallInteger, nullable=False)  # INT16, raw SDK cal
    day_offset: Mapped[int] = mapped_column(SmallInteger, nullable=False)      # INT8, 0–7


# ---------------------------------------------------------------------------
# ring_sleep  (one row per sleep session)
# ---------------------------------------------------------------------------

class Sleep(Base, TimestampMixin):
    __tablename__ = "ring_sleep"
    __table_args__ = (
        UniqueConstraint("sync_id", "user_id", name="uq_sleep_sync"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    start_timestamp: Mapped[int] = mapped_column(BigInteger, nullable=False)             # SleepNewProtoResp.st * 1000
    end_timestamp: Mapped[int] = mapped_column(BigInteger, nullable=False)               # SleepNewProtoResp.et * 1000
    deep_minutes: Mapped[int] = mapped_column(SmallInteger, nullable=False)              # INT16, stage_type=3
    light_minutes: Mapped[int] = mapped_column(SmallInteger, nullable=False)             # INT16, stage_type=2
    rem_minutes: Mapped[int] = mapped_column(SmallInteger, nullable=False)               # INT16, stage_type=4
    awake_minutes: Mapped[int] = mapped_column(SmallInteger, nullable=False)             # INT16, stage_type=5
    not_worn_minutes: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)    # INT16, stage_type=1
    total_minutes: Mapped[int] = mapped_column(SmallInteger, nullable=False)             # server-derived sum
    waking_count: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)        # SleepDisplay only
    lunch_start_timestamp: Mapped[int | None] = mapped_column(BigInteger, nullable=True) # nap start
    lunch_end_timestamp: Mapped[int | None] = mapped_column(BigInteger, nullable=True)   # nap end
    day_offset: Mapped[int] = mapped_column(SmallInteger, nullable=False)                # INT8, 0–7


# ---------------------------------------------------------------------------
# ring_sleep_stage_detail  (child of ring_sleep; one row per stage segment)
# ---------------------------------------------------------------------------

class SleepStageDetail(Base):
    """
    Child table — no device/server timestamps (inherited from parent sleep row).
    user_id is denormalised here for query efficiency per the data dict.
    """
    __tablename__ = "ring_sleep_stage_detail"

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    sleep_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("ring_sleep.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), nullable=False, index=True
    )
    stage_index: Mapped[int] = mapped_column(SmallInteger, nullable=False)    # INT8, 0-based position
    stage_type: Mapped[int] = mapped_column(SmallInteger, nullable=False)     # INT8, 0–5
    stage_label: Mapped[str] = mapped_column(String(16), nullable=False)      # server-derived
    duration_minutes: Mapped[int] = mapped_column(SmallInteger, nullable=False)  # INT16, 1–600
    sync_id: Mapped[uuid.UUID | None] = mapped_column(Uuid(as_uuid=True), nullable=True)
