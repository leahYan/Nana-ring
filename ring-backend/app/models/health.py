from sqlalchemy import Column, Integer, String, BigInteger, DECIMAL, TIMESTAMP, func, UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column
import uuid

class Base(DeclarativeBase):
    pass

class TimestampMixin:
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    device_timestamp: Mapped[int] = mapped_column(BigInteger, nullable=False)
    server_timestamp: Mapped[int] = mapped_column(BigInteger, nullable=False, server_default=func.extract('epoch', func.now()) * 1000)
    source: Mapped[str] = mapped_column(String, nullable=False)
    sync_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=True)
    firmware_version: Mapped[str] = mapped_column(String(32), nullable=True)
    hardware_version: Mapped[str] = mapped_column(String(32), nullable=True)

class HeartRate(Base, TimestampMixin):
    __tablename__ = 'ring_heart_rate'

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    bpm: Mapped[int] = mapped_column(Integer, nullable=False)
    rri: Mapped[int] = mapped_column(Integer, nullable=True)

class SpO2(Base, TimestampMixin):
    __tablename__ = 'ring_spo2'

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    percent: Mapped[int] = mapped_column(Integer, nullable=False)
    reading_type: Mapped[str] = mapped_column(String, nullable=False)
    interval_minutes: Mapped[int] = mapped_column(Integer, nullable=True)
    day_index: Mapped[int] = mapped_column(Integer, nullable=True)

class BloodPressure(Base, TimestampMixin):
    __tablename__ = 'ring_blood_pressure'

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    systolic: Mapped[int] = mapped_column(Integer, nullable=False)
    diastolic: Mapped[int] = mapped_column(Integer, nullable=False)
    heart_rate_used: Mapped[int] = mapped_column(Integer, nullable=True)
    measurement_type: Mapped[str] = mapped_column(String, nullable=False)

class Temperature(Base, TimestampMixin):
    __tablename__ = 'ring_temperature'

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    celsius_primary: Mapped[float] = mapped_column(DECIMAL(5, 2), nullable=False)
    celsius_side1: Mapped[float] = mapped_column(DECIMAL(5, 2), nullable=True)
    celsius_side2: Mapped[float] = mapped_column(DECIMAL(5, 2), nullable=True)
    measurement_mode: Mapped[str] = mapped_column(String, nullable=False)
    time_span_minutes: Mapped[int] = mapped_column(Integer, nullable=True)

class Stress(Base, TimestampMixin):
    __tablename__ = 'ring_stress'

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    stress_level: Mapped[int] = mapped_column(Integer, nullable=False)  # Assuming 0-100 scale

class HRV(Base, TimestampMixin):
    __tablename__ = 'ring_hrv'

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    hrv_ms: Mapped[int] = mapped_column(Integer, nullable=False)  # Heart Rate Variability in ms

class Activity(Base, TimestampMixin):
    __tablename__ = 'ring_activity'

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    steps: Mapped[int] = mapped_column(BigInteger, nullable=False)
    calories: Mapped[float] = mapped_column(DECIMAL(8, 2), nullable=False)

class Sleep(Base, TimestampMixin):
    __tablename__ = 'ring_sleep'

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    sleep_duration_minutes: Mapped[int] = mapped_column(Integer, nullable=False)
    sleep_quality_score: Mapped[int] = mapped_column(Integer, nullable=True)  # 0-100