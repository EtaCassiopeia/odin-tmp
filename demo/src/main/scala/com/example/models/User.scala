package com.example.models

import compat.schema.core._
import java.time.LocalDateTime
import java.util.UUID

@CompatCheck
@CompatMode("full")
case class User(
  id: UUID,
  name: String,
  email: String,
  age: Option[Int] = None,
  status: UserStatus = UserStatus.Active,
  createdAt: LocalDateTime,
  profile: Option[UserProfile] = None
)

object User:
  given compatUser: CompatSchema[User] = AutoDerivation.derived

@CompatCheck  
case class UserProfile(
  firstName: String,
  lastName: String,
  bio: Option[String] = None,
  website: Option[String] = None
)

object UserProfile:
  given compatUserProfile: CompatSchema[UserProfile] = AutoDerivation.derived

@CompatCheck
enum UserStatus:
  case Active, Inactive, Suspended, Pending

object UserStatus:
  given compatUserStatus: CompatSchema[UserStatus] = AutoDerivation.derived
