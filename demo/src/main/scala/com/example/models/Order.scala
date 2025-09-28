package com.example.models

import compat.schema.core._
import java.time.LocalDateTime
import java.util.UUID

@CompatCheck
case class Order(
  id: UUID,
  userId: UUID,
  items: List[OrderItem],
  totalAmount: BigDecimal,
  status: OrderStatus,
  createdAt: LocalDateTime,
  deliveredAt: Option[LocalDateTime] = None
)

object Order:
  given compatOrder: CompatSchema[Order] = AutoDerivation.derived

@CompatCheck
case class OrderItem(
  productId: UUID,
  productName: String,
  quantity: Int,
  unitPrice: BigDecimal,
  totalPrice: BigDecimal
)

object OrderItem:
  given compatOrderItem: CompatSchema[OrderItem] = AutoDerivation.derived

@CompatCheck
enum OrderStatus:
  case Pending, Confirmed, Shipped, Delivered, Cancelled

object OrderStatus:
  given compatOrderStatus: CompatSchema[OrderStatus] = AutoDerivation.derived

@CompatCheck
case class PaymentInfo(
  method: PaymentMethod,
  transactionId: Option[String] = None,
  processedAt: Option[LocalDateTime] = None
)

object PaymentInfo:
  given compatPaymentInfo: CompatSchema[PaymentInfo] = AutoDerivation.derived

@CompatCheck
enum PaymentMethod:
  case CreditCard, DebitCard, PayPal, BankTransfer, Cash

object PaymentMethod:
  given compatPaymentMethod: CompatSchema[PaymentMethod] = AutoDerivation.derived
