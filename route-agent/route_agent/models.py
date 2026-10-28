from datetime import date as Date
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, field_validator

ShortText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=160)]
PoiId = Annotated[str, StringConstraints(pattern=r"^[A-Za-z0-9_-]{1,80}$")]
Mode = Literal["walking", "transit", "driving"]


class Model(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True, allow_inf_nan=False, str_strip_whitespace=True)


class Constraints(Model):
    city: str = Field(default="", max_length=80)
    date: Date | None = None
    days: int = Field(default=1, ge=1, le=7)
    daily_minutes: int = Field(default=480, ge=30, le=960, alias="dailyMinutes")
    start_time: str = Field(default="09:00", pattern=r"^(?:[01]\d|2[0-3]):[0-5]\d$", alias="startTime")
    finish_by_time: str | None = Field(default=None, pattern=r"^(?:[01]\d|2[0-3]):[0-5]\d$", alias="finishByTime")
    budget: float | None = Field(default=None, ge=0, le=1000000)
    max_walking_meters: int | None = Field(default=None, ge=0, le=100000, alias="maxWalkingMeters")
    modes: list[Mode] = Field(default_factory=lambda: ["walking", "transit"], min_length=1, max_length=3)
    must_visit: list[ShortText] = Field(default_factory=list, max_length=20, alias="mustVisit")
    avoid: list[ShortText] = Field(default_factory=list, max_length=20)
    interests: list[ShortText] = Field(default_factory=list, max_length=12)
    group: str = Field(default="", max_length=300)
    accessibility: str = Field(default="", max_length=300)
    pace: Literal["relaxed", "balanced", "intensive"] = "balanced"
    return_to_start: bool = Field(default=False, alias="returnToStart")


class Turn(Model):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=8000)


class CurrentStop(Model):
    # Android RouteNode has additional descriptive fields; they are never trusted
    # as map evidence. The POI ID is re-resolved by the server before planning.
    model_config = ConfigDict(extra="ignore", populate_by_name=True)
    poi_id: PoiId = Field(alias="poiId")
    name: str = Field(default="", max_length=160)
    recommended_duration: int = Field(default=60, ge=0, le=960, alias="recommendedDuration")
    notes: str | None = Field(default=None, max_length=2000)


class Origin(Model):
    longitude: float = Field(ge=73, le=136)
    latitude: float = Field(ge=3, le=54)


class PlanRequest(Model):
    request_id: str = Field(pattern=r"^[A-Za-z0-9_-]{1,100}$", alias="requestId")
    memory_id: str = Field(pattern=r"^[A-Za-z0-9_-]{1,160}$", alias="memoryId")
    account_id: int = Field(gt=0, alias="accountId")
    message: str = Field(min_length=1, max_length=4000)
    history: list[Turn] = Field(default_factory=list, max_length=24)
    current_route: list[CurrentStop] = Field(default_factory=list, max_length=20, alias="currentRoute")
    constraints: Constraints = Field(default_factory=Constraints)
    origin: Origin | None = None

    @field_validator("current_route")
    @classmethod
    def unique_stops(cls, value):
        if len({stop.poi_id for stop in value}) != len(value):
            raise ValueError("currentRoute contains duplicate POI IDs")
        return value


class SearchArgs(Model):
    keyword: ShortText
    city: ShortText
    types: str = Field(default="", max_length=80)
    limit: int = Field(default=8, ge=1, le=15)


class DetailArgs(Model):
    poi_id: PoiId = Field(alias="poiId")


class NearbyArgs(Model):
    poi_id: PoiId = Field(alias="poiId")
    keyword: ShortText
    radius: int = Field(default=1500, ge=100, le=5000)


class WeatherArgs(Model):
    adcode: str = Field(pattern=r"^\d{6}$")


class LegArgs(Model):
    origin: PoiId
    destination: PoiId
    mode: Mode


class OptimizeArgs(Model):
    poi_ids: list[PoiId] = Field(min_length=2, max_length=20, alias="poiIds")
    fixed_start: bool = Field(default=True, alias="fixedStart")
    fixed_end: bool = Field(default=False, alias="fixedEnd")


class Visit(Model):
    poi_id: PoiId = Field(alias="poiId")
    minutes: int = Field(ge=10, le=480)
    learning_goal: str = Field(default="", max_length=400, alias="learningGoal")
    # Explicitly an estimate, never a verified ticket price.
    estimated_cost: float | None = Field(default=None, ge=0, le=100000, alias="estimatedCost")


class Day(Model):
    visits: list[Visit] = Field(min_length=1, max_length=20)
    modes: list[Mode] = Field(default_factory=list, max_length=20)
    break_minutes: int = Field(default=45, ge=0, le=240, alias="breakMinutes")
    start_poi_id: PoiId | None = Field(default=None, alias="startPoiId")
    end_poi_id: PoiId | None = Field(default=None, alias="endPoiId")


class ScheduleArgs(Model):
    days: list[Day] = Field(min_length=1, max_length=7)


class FinishArgs(Model):
    summary: str = Field(min_length=1, max_length=2500)
    assumptions: list[ShortText] = Field(default_factory=list, max_length=12)


class AskArgs(Model):
    questions: list[ShortText] = Field(min_length=1, max_length=3)
    reason: str = Field(default="", max_length=500)
