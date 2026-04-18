from pydantic import BaseModel, EmailStr
from typing import List, Optional

class UserResponse(BaseModel):
    id: str
    email: EmailStr
    role: Optional[str] = None
    fullName: Optional[str] = None
    bio: Optional[str] = None
    gender: Optional[str] = None
    dob: Optional[str] = None

class GenericResponse(BaseModel):
    status: str
    message: Optional[str] = None
