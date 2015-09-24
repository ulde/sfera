package cc.sferalabs.sfera.events;

import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.SubscriberExceptionHandler;

import cc.sferalabs.sfera.core.services.TasksManager;

/**
 * 
 *
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public abstract class Bus {

	private static final SubscriberExceptionHandler SUBSCRIBER_EXCEPTION_HANDLER = new EventsSubscriberExceptionHandler();
	private static final EventBus EVENT_BUS = new AsyncEventBus(TasksManager.getTasksExecutorService(),
			SUBSCRIBER_EXCEPTION_HANDLER);
	private static final Map<String, Event> EVENTS_MAP = new HashMap<String, Event>();

	private static final Logger logger = LoggerFactory.getLogger(Bus.class);

	/**
	 * 
	 * @param listener
	 */
	public static void register(EventListener listener) {
		EVENT_BUS.register(listener);
	}

	/**
	 * 
	 * @param listener
	 */
	public static void unregister(EventListener listener) {
		EVENT_BUS.unregister(listener);
	}

	/**
	 * 
	 * @param event
	 */
	public static void post(Event event) {
		EVENTS_MAP.put(event.getId(), event);
		EVENT_BUS.post(event);
		logger.info("Event: {} = {}", event.getId(), event.getValue());
	}

	/**
	 * 
	 * @param event
	 */
	public static void postIfChanged(Event event) {
		Object currVal = getValueOf(event.getId());
		Object newVal = event.getValue();

		if (currVal == null) {
			if (newVal != null) {
				post(event);
			}
		} else if (!currVal.equals(newVal)) {
			post(event);
		}
	}

	/**
	 * 
	 * @param id
	 * @return
	 */
	public static Object getValueOf(String id) {
		Event ev = EVENTS_MAP.get(id);
		if (ev == null) {
			return null;
		}
		return ev.getValue();
	}

	/**
	 * 
	 * @return
	 */
	public static Map<String, Event> getCurrentState() {
		return new HashMap<>(EVENTS_MAP);
	}
}
