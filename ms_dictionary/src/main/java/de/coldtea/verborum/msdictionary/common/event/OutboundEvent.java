package de.coldtea.verborum.msdictionary.common.event;

/**
 * A message a service wants on the exchange, raised as a Spring application event so the actual send
 * happens **after** the surrounding transaction commits (rule 1 in docs/agent/rabbitmq.md).
 * <p>
 * Internal to this service — it never crosses the wire. Only {@code payload} does.
 */
public record OutboundEvent(String routingKey, Object payload) {
}
