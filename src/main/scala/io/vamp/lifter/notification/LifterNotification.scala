package io.vamp.lifter.notification

import io.vamp.common.notification.Notification

object PersistenceInitializationSuccess extends Notification

case class PersistenceInitializationFailure(message: String) extends Notification
