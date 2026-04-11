from pydantic import BaseModel, Field, model_validator
from typing import Optional
import uuid

class HeartRateCreate(BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    device_timestamp: int = Field(..., gt=0)
    bpm: int = Field(..., ge=30, le=250)
    rri: Optional[int] = Field(None, ge=200, le=2000)
    source: str
    sync_id: Optional[uuid.UUID] = None
    firmware_version: Optional[str] = Field(None, max_length=32)
    hardware_version: Optional[str] = Field(None, max_length=32)

class SpO2Create(BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    device_timestamp: int = Field(..., gt=0)
    percent: int = Field(..., ge=70, le=100)
    reading_type: str = Field(..., pattern="^(manual|hourly|interval)$")
    interval_minutes: Optional[int] = Field(None, ge=1, le=60)
    day_index: Optional[int] = Field(None, ge=0, le=7)
    source: str
    sync_id: Optional[uuid.UUID] = None
    firmware_version: Optional[str] = Field(None, max_length=32)
    hardware_version: Optional[str] = Field(None, max_length=32)

class BloodPressureCreate(BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    device_timestamp: int = Field(..., gt=0)
    systolic: int = Field(..., ge=60, le=250)
    diastolic: int = Field(..., ge=40, le=150)
    heart_rate_used: Optional[int] = Field(None, ge=30, le=250)
    measurement_type: str
    source: str
    sync_id: Optional[uuid.UUID] = None
    firmware_version: Optional[str] = Field(None, max_length=32)
    hardware_version: Optional[str] = Field(None, max_length=32)

    @model_validator(mode='after')
    def check_diastolic_less_than_systolic(self):
        if self.diastolic >= self.systolic:
            raise ValueError('diastolic must be less than systolic')
        return self

class TemperatureCreate(BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    device_timestamp: int = Field(..., gt=0)
    celsius_primary: float = Field(..., ge=30.0, le=45.0)
    celsius_side1: Optional[float] = Field(None, ge=30.0, le=45.0)
    celsius_side2: Optional[float] = Field(None, ge=30.0, le=45.0)
    measurement_mode: str
    time_span_minutes: Optional[int] = Field(None, ge=1, le=120)
    source: str
    sync_id: Optional[uuid.UUID] = None
    firmware_version: Optional[str] = Field(None, max_length=32)
    hardware_version: Optional[str] = Field(None, max_length=32)

class StressCreate(BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    device_timestamp: int = Field(..., gt=0)
    stress_level: int = Field(..., ge=0, le=100)
    source: str
    sync_id: Optional[uuid.UUID] = None
    firmware_version: Optional[str] = Field(None, max_length=32)
    hardware_version: Optional[str] = Field(None, max_length=32)

class HRVCreate(BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    device_timestamp: int = Field(..., gt=0)
    hrv_ms: int = Field(..., gt=0)
    source: str
    sync_id: Optional[uuid.UUID] = None
    firmware_version: Optional[str] = Field(None, max_length=32)
    hardware_version: Optional[str] = Field(None, max_length=32)

class ActivityCreate(BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    device_timestamp: int = Field(..., gt=0)
    steps: int = Field(..., ge=0)
    calories: float = Field(..., ge=0.0)
    source: str
    sync_id: Optional[uuid.UUID] = None
    firmware_version: Optional[str] = Field(None, max_length=32)
    hardware_version: Optional[str] = Field(None, max_length=32)

class SleepCreate(BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    device_timestamp: int = Field(..., gt=0)
    sleep_duration_minutes: int = Field(..., ge=0)
    sleep_quality_score: Optional[int] = Field(None, ge=0, le=100)
    source: str
    sync_id: Optional[uuid.UUID] = None
    firmware_version: Optional[str] = Field(None, max_length=32)
    hardware_version: Optional[str] = Field(None, max_length=32)