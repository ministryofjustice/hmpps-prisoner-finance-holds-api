package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services.helpers

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services.DomainEventsSubscriber

class TestLogAppender : AppenderBase<ILoggingEvent>() {
  val events = mutableListOf<ILoggingEvent>()

  override fun append(event: ILoggingEvent) {
    events.add(event)
  }
}

fun mockLogger(): TestLogAppender {
  val logger = LoggerFactory.getLogger(
    DomainEventsSubscriber::class.java,
  ) as Logger
  val testLogAppender = TestLogAppender().apply {
    context = logger.loggerContext
    start()
  }
  logger.addAppender(testLogAppender)
  return testLogAppender
}
