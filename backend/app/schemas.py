from datetime import date
from decimal import Decimal
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field

Money = Annotated[
    int,
    Field(strict=True, ge=0, le=10**12, description="Integer paisa: Rs 100 = 10000"),
]
PositiveMoney = Annotated[int, Field(strict=True, gt=0, le=10**12)]
Quantity = Annotated[Decimal, Field(ge=0, le=10**6, decimal_places=3)]
Count = Annotated[int, Field(strict=True, ge=0, le=10**8)]
Shares = Annotated[int, Field(strict=True, gt=0, le=10**6)]
Name = Annotated[str, Field(min_length=1, max_length=120, pattern=r"\S")]
Note = Annotated[str, Field(max_length=1000)]
Phone = Annotated[str, Field(pattern=r"^\+[1-9]\d{7,14}$")]
Password = Annotated[str, Field(max_length=128)]


class Input(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Register(Input):
    phone: Phone
    name: Name
    password: Password


class Login(Input):
    phone: Phone
    password: str = Field(max_length=128)


class Profile(Input):
    name: Name
    language: Literal["en", "ur"] = "en"


class ChangePassword(Input):
    old_password: str = Field(max_length=128)
    new_password: Password


class BusinessCreate(Input):
    name: Name
    type: Literal["CHICKEN", "LPG", "BROILER"]
    total_shares: Shares
    share_price: PositiveMoney


class SupplierCreate(Input):
    business_id: str
    name: Name
    phone: Phone | None = None


class DailyInput(Input):
    business_id: str
    date: date
    kind: Literal["PURCHASE", "SALE", "BYPRODUCT", "EXPENSE"]
    quantity: Quantity = Decimal(0)
    amount: PositiveMoney
    channel: Literal["RETAIL", "COMMERCIAL"] | None = None
    note: Note = ""
    supplier_id: str | None = None


class CloseDay(Input):
    day_id: str


class BatchCreate(Input):
    business_id: str
    name: Name
    chicks: Shares
    total_shares: Shares
    share_price: PositiveMoney
    initial_cost: Money


class BatchUpdate(Input):
    date: date
    feed_kg: Quantity
    deaths: Count = 0
    expense: Money = 0
    note: Note = ""


class Harvest(Input):
    yield_kg: Quantity
    price_per_kg: Money
    additional_expense: Money = 0


class Buy(Input):
    business_id: str
    batch_id: str | None = None
    shares: Shares


class Deposit(Input):
    amount: PositiveMoney


class Withdraw(Input):
    amount: PositiveMoney
    provider: Literal["RAAST", "NAYAPAY", "UBL"]
    destination: str = Field(min_length=5, max_length=120)


class ResolveWithdrawal(Input):
    status: Literal["PAID", "FAILED"]
